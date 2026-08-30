package com.strings.app.domain.filter

import com.strings.app.domain.model.ConditionField
import com.strings.app.domain.model.ConditionGroup
import com.strings.app.domain.model.ConditionLeaf
import com.strings.app.domain.model.ConditionNode
import com.strings.app.domain.model.ConditionOperator
import com.strings.app.domain.model.LogicGroup
import com.strings.app.domain.model.Message
import com.strings.app.domain.sms.SenderPrincipal

data class SuggestedFilter(
    val name: String,
    val root: ConditionGroup
)

/**
 * Builds a draft filter from a set of messages the user selected. The body
 * conditions are common phrases (real substrings shared by every selected
 * message), extracted greedily longest-first so that variable parts (OTPs,
 * amounts, dates) naturally become phrase boundaries. Phrases that keep the
 * same relative order across all selected messages are chained into a single
 * `MATCHES_REGEX` (order-enforced, with wildcard gaps for the variable parts);
 * any phrase whose order is not consistent becomes a separate AND `CONTAINS`.
 */
class FilterSuggester {
    private data class Phrase(val text: String, val start: Int)

    fun suggest(messages: List<Message>): SuggestedFilter? {
        if (messages.isEmpty()) return null
        val senderCondition: ConditionNode? = buildSenderCondition(messages)
        val phrases: List<Phrase> = buildCommonPhrases(messages).sortedBy { it.start }
        val phraseLeaves: List<ConditionLeaf> = buildBodyLeaves(phrases.map { it.text }, messages)
        val children: List<ConditionNode> = listOfNotNull(senderCondition) + phraseLeaves
        if (children.isEmpty()) return null
        return SuggestedFilter(
            name = buildName(senderCondition, phrases.map { it.text }),
            root = ConditionGroup(logic = LogicGroup.AND, children = children)
        )
    }

    /**
     * Phrases are visited in their reference (textual) order. A phrase joins the
     * ordered chain only if the whole chain-so-far + this phrase still matches
     * every selected message in that order; otherwise it becomes a standalone
     * `CONTAINS`. A chain of 2+ phrases is emitted as one `MATCHES_REGEX`; a
     * lone chained phrase stays a plain `CONTAINS`.
     */
    private fun buildBodyLeaves(orderedPhrases: List<String>, messages: List<Message>): List<ConditionLeaf> {
        if (orderedPhrases.isEmpty()) return emptyList()
        val chained: MutableList<String> = mutableListOf()
        val separate: MutableList<String> = mutableListOf()
        for (phrase in orderedPhrases) {
            val candidate: List<String> = chained + phrase
            val keepsOrder: Boolean = chained.isEmpty() || allMatchChain(candidate, messages)
            if (keepsOrder) chained.add(phrase) else separate.add(phrase)
        }
        val leaves: MutableList<ConditionLeaf> = mutableListOf()
        when {
            chained.size >= 2 -> leaves.add(
                ConditionLeaf(ConditionField.BODY, ConditionOperator.MATCHES_REGEX, chainPattern(chained))
            )
            chained.size == 1 -> leaves.add(
                ConditionLeaf(ConditionField.BODY, ConditionOperator.CONTAINS, chained.first())
            )
        }
        separate.forEach { phrase ->
            leaves.add(ConditionLeaf(ConditionField.BODY, ConditionOperator.CONTAINS, phrase))
        }
        return leaves
    }

    private fun allMatchChain(phrases: List<String>, messages: List<Message>): Boolean {
        val regex = Regex(chainPattern(phrases), RegexOption.IGNORE_CASE)
        return messages.all { regex.containsMatchIn(it.body) }
    }

    private fun chainPattern(phrases: List<String>): String =
        phrases.joinToString(WILDCARD_GAP) { Regex.escape(it) }

    private fun buildSenderCondition(messages: List<Message>): ConditionNode? {
        val senders: List<String> = messages.map { it.sender.trim() }.filter { it.isNotEmpty() }
        if (senders.isEmpty()) return null
        val distinctSenders: Set<String> = senders.toSet()
        if (distinctSenders.size == 1) {
            return ConditionLeaf(ConditionField.SENDER, ConditionOperator.EQUALS, distinctSenders.first())
        }
        val principals: Set<String> = senders.map { principalOf(it) }.filter { it.isNotBlank() }.toSet()
        if (principals.isEmpty()) return null
        if (principals.size == 1) {
            return ConditionLeaf(ConditionField.SENDER, ConditionOperator.CONTAINS, principals.first())
        }
        val children: List<ConditionLeaf> = principals.sorted().map { p ->
            ConditionLeaf(ConditionField.SENDER, ConditionOperator.CONTAINS, p)
        }
        return ConditionGroup(logic = LogicGroup.OR, children = children)
    }

    private fun buildCommonPhrases(messages: List<Message>): List<Phrase> {
        val reference: Message = messages.minByOrNull { it.body.length } ?: return emptyList()
        val others: List<Message> = messages.filter { it !== reference }
        val tokens: List<IntRange> = TOKEN_REGEX.findAll(reference.body).map { it.range }.toList()
        if (tokens.isEmpty()) return emptyList()
        val consumed = BooleanArray(tokens.size)
        val phrases: MutableList<Phrase> = mutableListOf()
        while (phrases.size < MAX_PHRASES) {
            var bestPhrase: String? = null
            var bestStart = -1
            var bestEnd = -1
            var bestWordCount = 0
            var start = 0
            while (start < tokens.size) {
                if (consumed[start]) {
                    start++
                    continue
                }
                var end = start
                while (end < tokens.size && !consumed[end] && (end - start + 1) <= MAX_WORDS) {
                    val wordCount: Int = end - start + 1
                    val phrase: String = reference.body.substring(tokens[start].first, tokens[end].last + 1)
                    val isBetter: Boolean = wordCount > bestWordCount ||
                        (wordCount == bestWordCount && phrase.length > (bestPhrase?.length ?: 0))
                    if (isBetter && isMeaningful(phrase) && others.all { containsPhrase(it.body, phrase) }) {
                        bestPhrase = phrase
                        bestStart = start
                        bestEnd = end
                        bestWordCount = wordCount
                    }
                    end++
                }
                start++
            }
            if (bestPhrase == null) break
            phrases.add(Phrase(text = bestPhrase.trim(), start = tokens[bestStart].first))
            for (index in bestStart..bestEnd) {
                consumed[index] = true
            }
        }
        return phrases
    }

    private fun containsPhrase(haystack: String, phrase: String): Boolean {
        val pattern = "(?<![\\p{L}\\p{N}])" + Regex.escape(phrase) + "(?![\\p{L}\\p{N}])"
        return Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(haystack)
    }

    private fun isMeaningful(phrase: String): Boolean {
        return TOKEN_REGEX.findAll(phrase).map { it.value }.any { word -> isContentWord(word) }
    }

    private fun isContentWord(word: String): Boolean {
        return word.length >= MIN_WORD_LENGTH &&
            word.any { it.isLetter() } &&
            !word.all { it.isDigit() } &&
            word.lowercase() !in STOPWORDS
    }

    private fun buildName(senderCondition: ConditionNode?, phrases: List<String>): String {
        if (senderCondition != null) {
            val label: String = when (senderCondition) {
                is ConditionLeaf -> principalOf(senderCondition.value)
                is ConditionGroup -> senderCondition.children
                    .filterIsInstance<ConditionLeaf>()
                    .joinToString("/") { it.value }
            }
            if (label.isNotBlank()) return "$label messages"
        }
        val firstPhrase: String? = phrases.firstOrNull()
        if (firstPhrase != null) {
            val keyword: String? = TOKEN_REGEX.findAll(firstPhrase).map { it.value }.firstOrNull { isContentWord(it) }
            if (keyword != null) {
                return keyword.replaceFirstChar { it.uppercase() } + " messages"
            }
        }
        return "Suggested filter"
    }

    private fun principalOf(sender: String): String = SenderPrincipal.principal(sender)

    private companion object {
        val TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+")
        const val WILDCARD_GAP = "[\\s\\S]*?"
        const val MAX_PHRASES = 4
        const val MAX_WORDS = 8
        const val MIN_WORD_LENGTH = 3
        val STOPWORDS: Set<String> = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "to", "of", "for", "in", "on", "at", "by", "with", "and", "or",
            "your", "you", "our", "we", "has", "have", "had", "will", "this",
            "that", "it", "as", "from", "but", "not", "no", "if", "so", "up",
            "out", "get", "its", "their", "them", "http", "https", "www", "com"
        )
    }
}

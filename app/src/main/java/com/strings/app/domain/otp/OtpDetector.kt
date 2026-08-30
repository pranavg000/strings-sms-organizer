package com.strings.app.domain.otp

import kotlin.math.abs

/**
 * Pure, testable OTP extractor. Returns the most likely one-time code in a message
 * body, or null when the message is not an OTP. A code is only returned when an OTP
 * keyword is present and the candidate is not part of a currency amount.
 */
class OtpDetector {
    fun detect(body: String): String? {
        if (body.isBlank()) return null
        val keywordPositions: List<Int> = KEYWORD_REGEX.findAll(body).map { it.range.first }.toList()
        if (keywordPositions.isEmpty()) return null
        val candidates: List<MatchResult> = CANDIDATE_REGEX.findAll(body).toList()
        var best: MatchResult? = null
        var bestDistance: Int = Int.MAX_VALUE
        for (candidate in candidates) {
            if (isAmountContext(body, candidate)) continue
            if (isPhoneNumberContext(body, candidate)) continue
            val position: Int = candidate.range.first
            val distance: Int = keywordPositions.minOf { abs(it - position) }
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        return best?.value
    }

    private fun isPhoneNumberContext(body: String, match: MatchResult): Boolean {
        var left: Int = match.range.first
        while (left - 1 >= 0 && isPhoneChar(body[left - 1])) left--
        var right: Int = match.range.last
        while (right + 1 < body.length && isPhoneChar(body[right + 1])) right++
        val digitsInSpan: Int = (left..right).count { body[it].isDigit() }
        return digitsInSpan >= PHONE_MIN_DIGITS
    }

    private fun isPhoneChar(c: Char): Boolean =
        c.isDigit() || c == ' ' || c == '-' || c == '(' || c == ')' || c == '+'

    private fun isAmountContext(body: String, match: MatchResult): Boolean {
        val after: Char? = body.getOrNull(match.range.last + 1)
        val afterNext: Char? = body.getOrNull(match.range.last + 2)
        if ((after == '.' || after == ',') && afterNext != null && afterNext.isDigit()) return true
        val before: Char? = body.getOrNull(match.range.first - 1)
        if (before == '.' || before == ',') return true
        val windowStart: Int = (match.range.first - CURRENCY_WINDOW).coerceAtLeast(0)
        val prefix: String = body.substring(windowStart, match.range.first).lowercase()
        return CURRENCY_KEYWORDS.any { prefix.contains(it) }
    }

    companion object {
        private const val CURRENCY_WINDOW: Int = 8
        private const val PHONE_MIN_DIGITS: Int = 10
        private val CANDIDATE_REGEX: Regex = Regex("\\b\\d{4,8}\\b")
        private val POSITIVE_KEYWORDS: List<String> = listOf(
            "otp", "one-time", "one time", "verification", "verify", "verification code",
            "security code", "passcode", "pin", "password", "authenticate", "auth code", "code"
        )
        // Keywords matched as whole words so substrings (e.g. "pin" in "shopping") never trigger detection.
        private val KEYWORD_REGEX: Regex = Regex(
            "(?<![\\p{L}\\p{N}])(" +
                POSITIVE_KEYWORDS.joinToString("|") { Regex.escape(it) } +
                ")(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )
        private val CURRENCY_KEYWORDS: List<String> = listOf(
            "rs", "inr", "₹", "amount", "bal", "balance", "debited", "credited"
        )
    }
}

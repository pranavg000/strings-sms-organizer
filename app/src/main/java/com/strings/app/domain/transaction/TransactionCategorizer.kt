package com.strings.app.domain.transaction

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.Tag
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.repository.TransactionRepository

/**
 * Single source of truth for turning a stored [Message] into a transaction. Used by both the
 * live ingest pipeline (`SyncSmsUseCase`) and the batch re-categorization tool, so they always
 * behave identically. It is idempotent: it clears any existing transaction for the message
 * first, so re-running after a parser or account change replaces the old result (and removes
 * it entirely when the message no longer parses as a transaction).
 *
 * When the parser reports a transactional message from a supported bank with no configured
 * account, a pending account suggestion is recorded so the user can add it from the Manage
 * accounts screen.
 *
 * A message the user marked as "not a transaction" ([Message.isTransactionExcluded]) is never
 * parsed: its transaction is cleared and nothing is recreated, so batch re-runs respect the
 * override. [clearTransaction] is the inverse of a match -- it drops the transaction and the
 * Finance tags this class attached.
 */
class TransactionCategorizer(
    private val transactionParser: TransactionParser,
    private val transactionRepository: TransactionRepository,
    private val messageRepository: MessageRepository,
    private val tagRepository: TagRepository
) {
    suspend fun categorize(message: Message): ParsedTransaction? {
        val accounts: List<Account> = transactionRepository.getAllAccountsOnce()
        return categorize(message, accounts)
    }

    /** Bulk-friendly overload: callers iterating many messages fetch accounts once. */
    suspend fun categorize(message: Message, accounts: List<Account>): ParsedTransaction? {
        transactionRepository.deleteTransactionsForMessage(message.id)
        if (message.isTransactionExcluded) return null
        return when (val outcome: ParseOutcome = transactionParser.parse(message.body, message.sender, accounts)) {
            is ParseOutcome.NoMatch -> null
            is ParseOutcome.UnconfiguredAccount -> {
                transactionRepository.recordAccountSuggestion(outcome.bankCode, outcome.accountTail)
                null
            }
            is ParseOutcome.Match -> persistTransaction(message, outcome.transaction)
        }
    }

    private suspend fun persistTransaction(message: Message, parsed: ParsedTransaction): ParsedTransaction? {
        val accountId: Long = parsed.account.id
        if (parsed.transactionTime != null) {
            val duplicate: Boolean = transactionRepository.isDuplicate(
                accountId, parsed.amount, parsed.transactionTime
            )
            if (duplicate) return null
        }
        transactionRepository.insertTransaction(
            Transaction(
                messageId = message.id,
                accountId = accountId,
                amount = parsed.amount,
                type = parsed.type,
                balanceAfter = parsed.balanceAfter,
                merchant = parsed.merchant,
                transactionTime = parsed.transactionTime,
                timestamp = message.timestamp,
                rawMatch = parsed.rawMatch
            )
        )
        val financeTagId: Long = ensureTag(FINANCE_TAG_NAME, parentTagId = null)
        val sourceTagId: Long = ensureTag(parsed.account.bankName, parentTagId = financeTagId)
        messageRepository.addTagToMessage(message.id, financeTagId)
        messageRepository.addTagToMessage(message.id, sourceTagId)
        return parsed
    }

    /**
     * Removes the message's transaction and every Finance-family tag (the Finance tag and its
     * bank children) from the message. Sentinel rows are left alone, as in [categorize].
     */
    suspend fun clearTransaction(messageId: Long) {
        transactionRepository.deleteTransactionsForMessage(messageId)
        val financeTag: Tag = tagRepository.getTagByName(FINANCE_TAG_NAME) ?: return
        val financeFamilyIds: Set<Long> = (tagRepository.getDescendantTagIds(financeTag.id) + financeTag.id).toSet()
        val assignedTagIds: List<Long> = messageRepository.getTagIdsForMessage(messageId)
        for (tagId: Long in assignedTagIds) {
            if (tagId in financeFamilyIds) messageRepository.removeTagFromMessage(messageId, tagId)
        }
    }

    private suspend fun ensureTag(name: String, parentTagId: Long?): Long {
        val existing: Tag? = tagRepository.getTagByName(name)
        if (existing != null) return existing.id
        return tagRepository.insertTag(
            Tag(name = name, color = "", parentTagId = parentTagId, isSystemTag = true)
        )
    }

    companion object {
        const val FINANCE_TAG_NAME = "Finance"
    }
}

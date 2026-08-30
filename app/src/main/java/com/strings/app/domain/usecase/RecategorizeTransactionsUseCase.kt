package com.strings.app.domain.usecase

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.transaction.TransactionCategorizer

data class RecategorizeResult(
    val scanned: Int,
    val categorized: Int
)

/**
 * Re-runs categorization over every message since [sinceMillis]. Safe to run repeatedly --
 * the categorizer is idempotent, so a re-run after a parser or account-configuration change
 * updates each message's transaction in place (and clears it when the message no longer
 * parses). Runs automatically after account changes and after a backup import restores
 * account configs; also exposed in Settings as a manual tool.
 */
class RecategorizeTransactionsUseCase(
    private val messageRepository: MessageRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionCategorizer: TransactionCategorizer
) {
    suspend fun execute(sinceMillis: Long): RecategorizeResult {
        val accounts: List<Account> = transactionRepository.getAllAccountsOnce()
        val messages: List<Message> = messageRepository.getMessagesSince(sinceMillis)
        var categorized = 0
        for (message in messages) {
            if (message.isOtp) continue
            if (transactionCategorizer.categorize(message, accounts) != null) categorized++
        }
        return RecategorizeResult(scanned = messages.size, categorized = categorized)
    }
}

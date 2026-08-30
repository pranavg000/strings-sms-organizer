package com.strings.app.domain.usecase

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.transaction.CategorizationReport
import com.strings.app.domain.transaction.CategorizedMessageDto
import com.strings.app.domain.transaction.ParsedTransaction
import com.strings.app.domain.transaction.TransactionParser
import kotlinx.serialization.json.Json

/**
 * Dry run of the parser: scans messages since [sinceMillis], runs [TransactionParser] on each
 * (against the user's configured accounts), and returns a pretty-printed JSON report of
 * everything that WOULD be categorized (with the extracted fields). It writes nothing to the
 * database, so it is safe to inspect parser output before any transactions are created.
 */
class ExportCategorizationUseCase(
    private val messageRepository: MessageRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionParser: TransactionParser
) {
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun execute(sinceMillis: Long, windowDays: Int): String {
        val accounts: List<Account> = transactionRepository.getAllAccountsOnce()
        val messages: List<Message> = messageRepository.getMessagesSince(sinceMillis)
        val categorized: List<CategorizedMessageDto> = messages.mapNotNull { message ->
            val parsed: ParsedTransaction = transactionParser
                .parseTransaction(message.body, message.sender, accounts)
                ?: return@mapNotNull null
            CategorizedMessageDto(
                messageId = message.id,
                sender = message.sender,
                timestamp = message.timestamp,
                body = message.body,
                amount = parsed.amount,
                type = parsed.type.name,
                sourceName = parsed.account.bankName,
                displayName = parsed.account.displayName,
                accountType = parsed.account.accountType.name,
                accountTail = parsed.account.accountTail,
                balanceAfter = parsed.balanceAfter,
                merchant = parsed.merchant,
                transactionTime = parsed.transactionTime,
                rawMatch = parsed.rawMatch
            )
        }
        val report = CategorizationReport(
            generatedAt = System.currentTimeMillis(),
            windowDays = windowDays,
            scanned = messages.size,
            categorized = categorized.size,
            messages = categorized
        )
        return json.encodeToString(CategorizationReport.serializer(), report)
    }
}

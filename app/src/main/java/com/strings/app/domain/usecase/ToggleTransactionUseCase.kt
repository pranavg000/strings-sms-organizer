package com.strings.app.domain.usecase

import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.transaction.ParsedTransaction
import com.strings.app.domain.transaction.TransactionCategorizer

enum class IncludeTransactionResult {
    /** The parser recognized the message and a transaction now exists for it. */
    CATEGORIZED,
    /** Detection ran again but found no transaction; the message is left as-is. */
    NOT_RECOGNIZED
}

/**
 * User-driven override of transaction detection for one message.
 *
 * [exclude] marks the message as "not a transaction": the flag is persisted so batch
 * re-categorization skips it, and the existing transaction plus the Finance tags the
 * categorizer added are removed. [include] clears the flag and re-runs the normal detection
 * pipeline -- nothing bespoke -- reporting whether it produced a transaction so the UI can
 * tell the user when it didn't.
 */
class ToggleTransactionUseCase(
    private val messageRepository: MessageRepository,
    private val transactionCategorizer: TransactionCategorizer
) {
    suspend fun exclude(messageId: Long) {
        messageRepository.setTransactionExcluded(messageId, true)
        transactionCategorizer.clearTransaction(messageId)
    }

    suspend fun include(messageId: Long): IncludeTransactionResult {
        messageRepository.setTransactionExcluded(messageId, false)
        val message: Message = messageRepository.getMessageById(messageId)
            ?: return IncludeTransactionResult.NOT_RECOGNIZED
        val parsed: ParsedTransaction? = transactionCategorizer.categorize(message)
        return if (parsed != null) IncludeTransactionResult.CATEGORIZED else IncludeTransactionResult.NOT_RECOGNIZED
    }
}

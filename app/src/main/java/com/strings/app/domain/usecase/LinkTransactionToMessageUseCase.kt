package com.strings.app.domain.usecase

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionOrigin
import com.strings.app.domain.model.TransactionType
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.transaction.TransactionCategorizer

enum class LinkTransactionResult {
    LINKED,
    TRANSACTION_NOT_FOUND,
    MESSAGE_NOT_FOUND,
    /** Parser output is owned by the message it was parsed from and can't be re-pointed. */
    NOT_LINKABLE
}

/**
 * Turns a sentinel ("unaccounted amount") into a real transaction by attaching it to a message
 * the user picked, and re-points an already linked transaction on relink. No validation is done
 * against the message text: the amount, type, and account stay exactly as the balance check
 * recorded them; only the message, the timestamp (the message's), and the origin change.
 *
 * A linked row behaves like a parsed one for display and navigation -- the message gets the
 * Finance > bank tags, and tapping the ledger row opens the message -- but it is never deleted
 * by re-categorization, which only owns [TransactionOrigin.PARSED] rows. When a message loses
 * its last transaction (relink away or [remove]) its Finance tags are stripped again.
 */
class LinkTransactionToMessageUseCase(
    private val transactionRepository: TransactionRepository,
    private val messageRepository: MessageRepository,
    private val transactionCategorizer: TransactionCategorizer
) {
    suspend fun link(transactionId: Long, messageId: Long): LinkTransactionResult {
        val transaction: Transaction = transactionRepository.getTransactionById(transactionId)
            ?: return LinkTransactionResult.TRANSACTION_NOT_FOUND
        if (transaction.origin == TransactionOrigin.PARSED) return LinkTransactionResult.NOT_LINKABLE
        val message: Message = messageRepository.getMessageById(messageId)
            ?: return LinkTransactionResult.MESSAGE_NOT_FOUND
        val previousMessageId: Long? = if (transaction.isLinked) transaction.messageId else null
        transactionRepository.updateTransaction(
            transaction.copy(
                messageId = message.id,
                timestamp = message.timestamp,
                origin = TransactionOrigin.LINKED
            )
        )
        tagMessage(message.id, transaction.accountId)
        if (previousMessageId != null && previousMessageId != message.id) {
            releaseMessage(previousMessageId)
        }
        return LinkTransactionResult.LINKED
    }

    /**
     * Creates a linked transaction from scratch (used when a backup restores one). Returns the
     * new id, or null when the message or account doesn't exist on this device.
     */
    suspend fun create(
        messageId: Long,
        accountId: Long,
        amount: Double,
        type: TransactionType,
        balanceAfter: Double?,
        rawMatch: String
    ): Long? {
        val message: Message = messageRepository.getMessageById(messageId) ?: return null
        val account: Account = transactionRepository.getAccountById(accountId) ?: return null
        val id: Long = transactionRepository.insertTransaction(
            Transaction(
                messageId = message.id,
                accountId = account.id,
                amount = amount,
                type = type,
                balanceAfter = balanceAfter,
                merchant = null,
                transactionTime = null,
                timestamp = message.timestamp,
                rawMatch = rawMatch,
                origin = TransactionOrigin.LINKED
            )
        )
        transactionCategorizer.attachFinanceTags(message.id, account.bankName)
        return id
    }

    /** Permanently deletes a linked transaction; parsed rows and sentinels are left untouched. */
    suspend fun remove(transactionId: Long) {
        val transaction: Transaction = transactionRepository.getTransactionById(transactionId) ?: return
        if (!transaction.isLinked) return
        transactionRepository.deleteTransactionById(transactionId)
        releaseMessage(transaction.messageId)
    }

    private suspend fun tagMessage(messageId: Long, accountId: Long) {
        val account: Account = transactionRepository.getAccountById(accountId) ?: return
        transactionCategorizer.attachFinanceTags(messageId, account.bankName)
    }

    private suspend fun releaseMessage(messageId: Long) {
        if (transactionRepository.getTransactionsForMessage(messageId).isEmpty()) {
            transactionCategorizer.detachFinanceTags(messageId)
        }
    }
}

package com.strings.app.domain.model

data class Transaction(
    val id: Long = 0L,
    val messageId: Long,
    val accountId: Long,
    val amount: Double,
    val type: TransactionType,
    val balanceAfter: Double? = null,
    val merchant: String? = null,
    val transactionTime: String? = null,
    val timestamp: Long,
    val rawMatch: String,
    val origin: TransactionOrigin = TransactionOrigin.PARSED
) {
    val isSentinel: Boolean
        get() = origin == TransactionOrigin.SENTINEL
    val isLinked: Boolean
        get() = origin == TransactionOrigin.LINKED
}

enum class TransactionType {
    CREDIT, DEBIT
}

/**
 * How a transaction row came to exist. The origin decides which pipeline owns the row:
 * only [PARSED] rows are deleted and recreated when a message is re-categorized, so
 * [SENTINEL] and [LINKED] rows survive parser and account changes.
 */
enum class TransactionOrigin {
    /** Produced by the bank parser from the message's text. */
    PARSED,
    /** Placeholder for an unaccounted amount found by the balance check; shares the anchor's messageId. */
    SENTINEL,
    /** A sentinel the user attached to a message by hand; behaves like a real transaction for that message. */
    LINKED
}

/**
 * One ledger row: the transaction plus the user's description of its message (the
 * description lives on the message, so a sentinel only gains one once it is linked).
 */
data class LedgerEntry(
    val transaction: Transaction,
    val messageDescription: String? = null
)

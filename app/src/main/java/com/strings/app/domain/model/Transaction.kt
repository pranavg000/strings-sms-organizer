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
    val isSentinel: Boolean = false
)

enum class TransactionType {
    CREDIT, DEBIT
}

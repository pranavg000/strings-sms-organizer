package com.strings.app.domain.transaction

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.TransactionType

/**
 * The outcome of parsing a single SMS body into a financial transaction. It carries the
 * matched user-configured [account] directly (parsers only ever match against configured
 * accounts), so the categorizer can persist against the account row without any
 * name-based resolution.
 */
data class ParsedTransaction(
    val account: Account,
    val amount: Double,
    val type: TransactionType,
    val balanceAfter: Double? = null,
    val merchant: String? = null,
    val transactionTime: String? = null,
    val rawMatch: String
)

package com.strings.app.domain.model

/**
 * A detected account the user has not configured yet: a transactional SMS arrived from a
 * supported bank ([bankCode] maps into the BankCatalog) with tail digits that match no
 * configured account. Surfaced on the Manage accounts screen with Add/Dismiss actions.
 * [accountTail] is empty for tail-less wallets (Swiggy/Zomato/Amazon Pay).
 */
data class AccountSuggestion(
    val id: Long,
    val bankCode: String,
    val accountTail: String
)

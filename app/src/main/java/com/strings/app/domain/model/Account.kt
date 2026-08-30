package com.strings.app.domain.model

/**
 * A user-configured tracked account. [bankName] is the user's short name for the account,
 * used as the finance tag name and the card title. [bankCode] links the account to a
 * [com.strings.app.domain.transaction.BankCatalog] entry (empty for legacy rows created
 * before accounts became user-configured -- those need setup before they match again).
 * [colorIndex] maps into the shared AppPalette (-1 = unassigned, falls back to a hash).
 * [parentAccountId] links add-on/supplementary cards to their primary account so balances
 * and stats aggregate across the family. [isEnabled] gates parsing (disabled wallets stop
 * producing transactions).
 */
data class Account(
    val id: Long = 0L,
    val bankName: String,
    val accountTail: String,
    val accountType: AccountType,
    val displayName: String,
    val bankCode: String = "",
    val colorIndex: Int = -1,
    val parentAccountId: Long? = null,
    val isEnabled: Boolean = true
)

enum class AccountType {
    SAVINGS, CREDIT_CARD, WALLET
}

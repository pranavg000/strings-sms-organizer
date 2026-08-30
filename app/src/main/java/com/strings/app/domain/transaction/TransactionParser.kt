package com.strings.app.domain.transaction

import com.strings.app.domain.model.Account
import com.strings.app.domain.sms.SenderPrincipal

/**
 * The result of running the parser over one SMS.
 * [Match] carries a transaction against a configured account.
 * [UnconfiguredAccount] means the message is transactional and from a supported bank, but
 * no configured account matched -- the categorizer records it as an account suggestion.
 * [NoMatch] means the message is not a transaction (or not from a supported bank).
 */
sealed interface ParseOutcome {
    data class Match(val transaction: ParsedTransaction) : ParseOutcome
    data class UnconfiguredAccount(val bankCode: String, val accountTail: String) : ParseOutcome
    data object NoMatch : ParseOutcome
}

/**
 * Parses an SMS against the user's configured accounts. The sender principal routes the
 * message to a single [BankParser] via [BankCatalog]; the parser only sees the enabled
 * accounts belonging to its bank, so all account identity is data, never code.
 *
 * [isNonTransactional] runs before any parser to reject messages that look like transactions
 * (they contain amounts and bank keywords) but are actually bill reminders, statements,
 * declined alerts, EMI confirmations, or beneficiary acknowledgements.
 */
class TransactionParser(
    parsers: List<BankParser>
) {
    private val parsersByCode: Map<BankCode, BankParser> = parsers.associateBy { it.bankCode }

    fun parse(body: String, sender: String, accounts: List<Account>): ParseOutcome {
        if (isNonTransactional(body)) return ParseOutcome.NoMatch
        val principal: String = SenderPrincipal.principal(sender)
        val bank: CatalogBank = BankCatalog.byPrincipal(principal) ?: return ParseOutcome.NoMatch
        val parser: BankParser = parsersByCode[bank.code] ?: return ParseOutcome.NoMatch
        val bankAccounts: List<Account> = accounts.filter {
            it.isEnabled && it.bankCode == bank.code.name
        }
        val match: ParsedTransaction? = parser.match(body, bankAccounts)
        if (match != null) return ParseOutcome.Match(match)
        return suggestionFor(bank, body, accounts)
    }

    /** Convenience for callers that only care about a successful match. */
    fun parseTransaction(body: String, sender: String, accounts: List<Account>): ParsedTransaction? {
        return (parse(body, sender, accounts) as? ParseOutcome.Match)?.transaction
    }

    private fun suggestionFor(bank: CatalogBank, body: String, accounts: List<Account>): ParseOutcome {
        if (BankParsing.extractAmount(body) == null) return ParseOutcome.NoMatch
        if (!BankParsing.looksTransactional(body)) return ParseOutcome.NoMatch
        if (!bank.requiresTail) {
            val hasMarker: Boolean = bank.walletMarkers.any { body.contains(it, ignoreCase = true) }
            if (!hasMarker) return ParseOutcome.NoMatch
            val known: Boolean = accounts.any { it.bankCode == bank.code.name }
            return if (known) ParseOutcome.NoMatch else ParseOutcome.UnconfiguredAccount(bank.code.name, "")
        }
        val tail: String = BankParsing.extractAccountTail(body) ?: return ParseOutcome.NoMatch
        val known: Boolean = accounts.any { it.bankCode == bank.code.name && it.accountTail == tail }
        return if (known) ParseOutcome.NoMatch else ParseOutcome.UnconfiguredAccount(bank.code.name, tail)
    }

    private companion object {
        fun isNonTransactional(body: String): Boolean {
            val lower: String = body.lowercase()
            if (lower.contains("amount due")) return true
            if (lower.contains("total due")) return true
            if (lower.contains("is due for payment") || lower.contains("is due on")) return true
            if (lower.contains("statement is sent to")) return true
            if (lower.contains("bill alert") || lower.contains("pay now!")) return true
            if (lower.contains("reminder!")) return true
            if (lower.contains("bill dated") && lower.contains("due")) return true
            if (lower.contains("will be auto-debited on")) return true
            if (lower.contains("declined")) return true
            if (lower.contains("converted into emi")) return true
            if (lower.contains("has been credited to the beneficiary account")) return true
            return false
        }
    }
}

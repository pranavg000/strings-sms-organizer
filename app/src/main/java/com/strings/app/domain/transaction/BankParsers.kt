package com.strings.app.domain.transaction

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.model.TransactionType

/**
 * A per-bank detection strategy. [match] receives ONLY the user's enabled accounts for
 * this bank (pre-filtered by [TransactionParser]) and returns a [ParsedTransaction] when
 * the message belongs to one of them, or null to pass through. Detection patterns are
 * public per-bank knowledge; account identity (tails, names) comes from the accounts.
 */
interface BankParser {
    val bankCode: BankCode
    fun match(body: String, accounts: List<Account>): ParsedTransaction?
}

/** The full production parser chain, one entry per catalog bank. */
fun defaultBankParsers(): List<BankParser> = listOf(
    AxisParser(),
    IciciParser(),
    HdfcParser(),
    IdfcParser(),
    BoiParser(),
    EpfoParser(),
    PluxeeParser(),
    SwiggyParser(),
    ZomatoParser(),
    AmazonPayParser()
)

/**
 * Shared extraction helpers and regexes used by every [BankParser]. Pure functions over
 * the SMS body -- no account identity in here.
 */
internal object BankParsing {
    val AMOUNT_REGEX: Regex =
        Regex("(?i)(?<![a-z])(?:rs|inr|₹)\\.?\\s*([\\d,]+(?:\\.\\d+)?)")
    val BALANCE_REGEX: Regex =
        Regex("(?i)(?:updated balance|available balance|avl bal|avb bal|wallet balance)[:\\s]*(?:is\\s+)?(?:rs|inr|₹)\\.?\\s*([\\d,]+(?:\\.\\d+)?)")
    val AVL_BAL_REGEX: Regex =
        Regex("(?i)Avl\\s*Bal\\s*(?:Rs)?\\s*([\\d,]+(?:\\.\\d+)?)")
    val ACCOUNT_TAIL_REGEX: Regex =
        Regex("(?i)(?:a/c|acct|account|card|x{2,})[\\s:.*xX]*?(\\d{3,6})")
    val REVERSAL_REGEX: Regex =
        Regex("\\b(?:revers(?:ed|al)|refund(?:ed)?)\\b", RegexOption.IGNORE_CASE)
    val STANDALONE_DEBIT: Regex =
        Regex("\\bdebit\\b", RegexOption.IGNORE_CASE)
    val STANDALONE_CREDIT: Regex =
        Regex("\\bcredit\\b(?!\\s*card)", RegexOption.IGNORE_CASE)
    val SPEND_REGEX: Regex =
        Regex("\\bspent\\b", RegexOption.IGNORE_CASE)
    val HDFC_TIME_REGEX: Regex =
        Regex("""\d{4}-\d{2}-\d{2}:(\d{2}):(\d{2}):\d{2}""")
    val AXIS_TIME_REGEX: Regex =
        Regex("""\d{2}-\d{2}-\d{2}\s+(\d{2}):(\d{2}):\d{2}""")
    val AMPM_TIME_REGEX: Regex =
        Regex("(?i)\\bat\\s+(\\d{1,2}):(\\d{2})\\s*(AM|PM)\\b")

    /**
     * Refunds and reversals are money coming back, so they are always CREDIT.
     * Checked BEFORE any spend/default-DEBIT branch because reversal texts often
     * quote the original transaction's wording ("spent", merchant name, etc.).
     */
    fun isReversal(body: String): Boolean = REVERSAL_REGEX.containsMatchIn(body)

    /**
     * Determines debit vs. credit from keyword presence. "debited"/"credited" are checked
     * first (Indian bank pattern: "[account] debited ... [recipient] credited" -- both
     * keywords appear but the tracked-account action is debit). Then standalone "Debit"/"Credit"
     * as whole words (Axis uses "Debit INR ..." / "Credit INR ..."). The credit fallback
     * excludes "Credit Card" via negative lookahead to avoid false positives.
     * Reversal/refund wording counts as CREDIT (checked after the debit keywords so the
     * dual-keyword UPI rule keeps priority for genuine debits).
     */
    fun classifyDirection(body: String): TransactionType? = when {
        body.contains("debited", ignoreCase = true) -> TransactionType.DEBIT
        STANDALONE_DEBIT.containsMatchIn(body) -> TransactionType.DEBIT
        isReversal(body) -> TransactionType.CREDIT
        body.contains("credited", ignoreCase = true) -> TransactionType.CREDIT
        STANDALONE_CREDIT.containsMatchIn(body) -> TransactionType.CREDIT
        else -> null
    }

    /**
     * True when the body carries transaction-like wording (an explicit direction keyword
     * or a card spend). Used to gate account suggestions so promos never suggest accounts.
     */
    fun looksTransactional(body: String): Boolean =
        classifyDirection(body) != null || SPEND_REGEX.containsMatchIn(body)

    fun extractAmount(body: String): Double? {
        val match: MatchResult = AMOUNT_REGEX.find(body) ?: return null
        return match.groupValues[1].replace(",", "").toDoubleOrNull()
    }

    fun extractRawAmount(body: String): String? {
        return AMOUNT_REGEX.find(body)?.value?.trim()
    }

    fun extractBalance(body: String): Double? {
        val match: MatchResult = BALANCE_REGEX.find(body) ?: return null
        return match.groupValues[1].replace(",", "").toDoubleOrNull()
    }

    fun extractAvlBal(body: String): Double? {
        val match: MatchResult = AVL_BAL_REGEX.find(body) ?: return null
        return match.groupValues[1].replace(",", "").toDoubleOrNull()
    }

    fun extractAccountTail(body: String): String? {
        val match: MatchResult = ACCOUNT_TAIL_REGEX.find(body) ?: return null
        return match.groupValues[1].takeIf { it.isNotBlank() }
    }

    fun extractTime(body: String): String? {
        val hdfc: MatchResult? = HDFC_TIME_REGEX.find(body)
        if (hdfc != null) return "${hdfc.groupValues[1]}:${hdfc.groupValues[2]}"
        val axis: MatchResult? = AXIS_TIME_REGEX.find(body)
        if (axis != null) return "${axis.groupValues[1]}:${axis.groupValues[2]}"
        val ampm: MatchResult? = AMPM_TIME_REGEX.find(body)
        if (ampm != null) {
            val hourRaw: Int = ampm.groupValues[1].toInt()
            val isPm: Boolean = ampm.groupValues[3].equals("PM", ignoreCase = true)
            val hour: Int = when {
                isPm && hourRaw != 12 -> hourRaw + 12
                !isPm && hourRaw == 12 -> 0
                else -> hourRaw
            }
            return "%02d:%s".format(hour, ampm.groupValues[2])
        }
        return null
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Axis Bank
// ──────────────────────────────────────────────────────────────────────────

class AxisParser : BankParser {
    override val bankCode: BankCode = BankCode.AXIS

    override fun match(body: String, accounts: List<Account>): ParsedTransaction? {
        for (account in accounts) {
            if (account.accountTail.isBlank() || !body.contains(account.accountTail)) continue
            val parsed: ParsedTransaction? = when (account.accountType) {
                AccountType.SAVINGS -> matchSavings(body, account)
                AccountType.CREDIT_CARD -> matchCreditCard(body, account)
                AccountType.WALLET -> null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun matchSavings(body: String, account: Account): ParsedTransaction? {
        val amount: Double = BankParsing.extractAmount(body) ?: return null
        val type: TransactionType = BankParsing.classifyDirection(body) ?: return null
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            transactionTime = BankParsing.extractTime(body),
            rawMatch = BankParsing.extractRawAmount(body) ?: body
        )
    }

    private fun matchCreditCard(body: String, account: Account): ParsedTransaction? {
        val amount: Double = BankParsing.extractAmount(body) ?: return null
        val type: TransactionType = when {
            BankParsing.isReversal(body) -> TransactionType.CREDIT
            AXIS_CC_SPEND.containsMatchIn(body) -> TransactionType.DEBIT
            body.contains("received", ignoreCase = true) ||
                body.contains("credited", ignoreCase = true) ||
                body.contains("cashback", ignoreCase = true) -> TransactionType.CREDIT
            else -> TransactionType.DEBIT
        }
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            transactionTime = BankParsing.extractTime(body),
            rawMatch = BankParsing.extractRawAmount(body) ?: body
        )
    }

    private companion object {
        val AXIS_CC_SPEND: Regex = Regex("spent\\s+INR", RegexOption.IGNORE_CASE)
    }
}

// ──────────────────────────────────────────────────────────────────────────
// ICICI Bank
// ──────────────────────────────────────────────────────────────────────────

class IciciParser : BankParser {
    override val bankCode: BankCode = BankCode.ICICI

    override fun match(body: String, accounts: List<Account>): ParsedTransaction? {
        return matchCreditCard(body, accounts) ?: matchSavings(body, accounts)
    }

    private fun matchCreditCard(body: String, accounts: List<Account>): ParsedTransaction? {
        val cards: List<Account> = accounts.filter {
            it.accountType == AccountType.CREDIT_CARD && it.accountTail.isNotBlank()
        }
        val direct: Account? = cards.firstOrNull { body.contains("XX${it.accountTail}") }
        if (direct != null) return buildCreditCardTransaction(body, direct)
        if (body.contains("Credit Card Account XX", ignoreCase = true)) {
            val tailMatch: MatchResult = ICICI_CC_ACCOUNT_TAIL.find(body) ?: return null
            val tail: String = tailMatch.groupValues[1]
            val fallback: Account = cards.firstOrNull { it.accountTail == tail } ?: return null
            return buildCreditCardTransaction(body, fallback)
        }
        return null
    }

    private fun buildCreditCardTransaction(body: String, account: Account): ParsedTransaction? {
        val amount: Double = BankParsing.extractAmount(body) ?: return null
        val type: TransactionType = when {
            BankParsing.isReversal(body) -> TransactionType.CREDIT
            body.contains("spent", ignoreCase = true) -> TransactionType.DEBIT
            body.contains("payment", ignoreCase = true) &&
                body.contains("received", ignoreCase = true) -> TransactionType.CREDIT
            body.contains("thank you for your payment", ignoreCase = true) -> TransactionType.CREDIT
            else -> TransactionType.DEBIT
        }
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            rawMatch = BankParsing.extractRawAmount(body) ?: body
        )
    }

    private fun matchSavings(body: String, accounts: List<Account>): ParsedTransaction? {
        for (account in accounts) {
            if (account.accountType != AccountType.SAVINGS || account.accountTail.isBlank()) continue
            if (!body.contains(account.accountTail)) continue
            if (!savingsPattern(account.accountTail).containsMatchIn(body)) continue
            val amount: Double = BankParsing.extractAmount(body) ?: continue
            val type: TransactionType = BankParsing.classifyDirection(body) ?: continue
            return ParsedTransaction(
                account = account,
                amount = amount,
                type = type,
                balanceAfter = BankParsing.extractBalance(body),
                rawMatch = BankParsing.extractRawAmount(body) ?: body
            )
        }
        return null
    }

    /**
     * ICICI savings alerts reference the account as "Acct XX<tail>" / "Account XX<tail>",
     * or occasionally with an extra leading digit ("XX4<tail>" when the bank exposes one
     * more digit of the account number).
     */
    private fun savingsPattern(tail: String): Regex {
        val escaped: String = Regex.escape(tail)
        return Regex("(?i)(?:Acct?|Account)\\s*XX\\d*$escaped|XX\\d{1,2}$escaped")
    }

    private companion object {
        val ICICI_CC_ACCOUNT_TAIL: Regex = Regex("(?i)Credit Card Account XX(\\d{4})")
    }
}

// ──────────────────────────────────────────────────────────────────────────
// HDFC Bank
// ──────────────────────────────────────────────────────────────────────────

class HdfcParser : BankParser {
    override val bankCode: BankCode = BankCode.HDFC

    override fun match(body: String, accounts: List<Account>): ParsedTransaction? {
        // HDFC forwards some co-branded KOTAK notices from its own header; skip those.
        if (body.contains("KOTAK", ignoreCase = true)) return null
        for (account in accounts) {
            if (account.accountTail.isBlank() || !body.contains(account.accountTail)) continue
            val parsed: ParsedTransaction? = when (account.accountType) {
                AccountType.CREDIT_CARD -> matchCreditCard(body, account)
                AccountType.SAVINGS -> matchSavings(body, account)
                AccountType.WALLET -> null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun matchCreditCard(body: String, account: Account): ParsedTransaction? {
        val amount: Double = BankParsing.extractAmount(body) ?: return null
        val type: TransactionType = when {
            BankParsing.isReversal(body) -> TransactionType.CREDIT
            HDFC_SPEND.containsMatchIn(body) -> TransactionType.DEBIT
            body.contains("PAYMENT", ignoreCase = true) &&
                body.contains("RECEIVED", ignoreCase = true) -> TransactionType.CREDIT
            body.contains("credited to your card", ignoreCase = true) -> TransactionType.CREDIT
            else -> TransactionType.DEBIT
        }
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            transactionTime = BankParsing.extractTime(body),
            rawMatch = BankParsing.extractRawAmount(body) ?: body
        )
    }

    private fun matchSavings(body: String, account: Account): ParsedTransaction? {
        val amount: Double = BankParsing.extractAmount(body) ?: return null
        val type: TransactionType = BankParsing.classifyDirection(body) ?: return null
        val balance: Double? = BankParsing.extractAvlBal(body) ?: BankParsing.extractBalance(body)
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            balanceAfter = balance,
            transactionTime = BankParsing.extractTime(body),
            rawMatch = BankParsing.extractRawAmount(body) ?: body
        )
    }

    private companion object {
        val HDFC_SPEND: Regex = Regex("spent\\s+Rs", RegexOption.IGNORE_CASE)
    }
}

// ──────────────────────────────────────────────────────────────────────────
// IDFC First Bank
// ──────────────────────────────────────────────────────────────────────────

class IdfcParser : BankParser {
    override val bankCode: BankCode = BankCode.IDFC

    override fun match(body: String, accounts: List<Account>): ParsedTransaction? {
        for (account in accounts) {
            if (account.accountType != AccountType.CREDIT_CARD || account.accountTail.isBlank()) continue
            if (!body.contains(account.accountTail)) continue
            val amount: Double = BankParsing.extractAmount(body) ?: continue
            val type: TransactionType = when {
                BankParsing.isReversal(body) -> TransactionType.CREDIT
                body.contains("spent", ignoreCase = true) -> TransactionType.DEBIT
                body.contains("payment", ignoreCase = true) &&
                    body.contains("received", ignoreCase = true) -> TransactionType.CREDIT
                body.contains("credited", ignoreCase = true) -> TransactionType.CREDIT
                else -> TransactionType.DEBIT
            }
            return ParsedTransaction(
                account = account,
                amount = amount,
                type = type,
                transactionTime = BankParsing.extractTime(body),
                rawMatch = BankParsing.extractRawAmount(body) ?: body
            )
        }
        return null
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Bank of India
// ──────────────────────────────────────────────────────────────────────────

class BoiParser : BankParser {
    override val bankCode: BankCode = BankCode.BOI

    override fun match(body: String, accounts: List<Account>): ParsedTransaction? {
        val savings: List<Account> = accounts.filter {
            it.accountType == AccountType.SAVINGS && it.accountTail.isNotBlank()
        }
        for (account in savings) {
            if (!body.contains(account.accountTail)) continue
            val parsed: ParsedTransaction? = matchWithTail(body, account)
            if (parsed != null) return parsed
        }
        val single: Account = savings.singleOrNull() ?: return null
        return matchUpiWithoutTail(body, single)
    }

    private fun matchWithTail(body: String, account: Account): ParsedTransaction? {
        val amount: Double = BankParsing.extractAmount(body) ?: return null
        val type: TransactionType = BankParsing.classifyDirection(body) ?: return null
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            balanceAfter = BankParsing.extractAvlBal(body),
            rawMatch = BankParsing.extractRawAmount(body) ?: body
        )
    }

    /**
     * BOI UPI mandate debits omit both the account tail and the currency prefix:
     * "BOI UPI - Your account has been debited towards <merchant> ... for 149.00 on <date>".
     * When exactly one BOI account is configured, tail-less account-level alerts map to it.
     * The bare amount regex requires decimals ("for 149.00") so date fragments never match.
     */
    private fun matchUpiWithoutTail(body: String, account: Account): ParsedTransaction? {
        if (!body.contains("account", ignoreCase = true)) return null
        val type: TransactionType = BankParsing.classifyDirection(body) ?: return null
        val match: MatchResult = BankParsing.AMOUNT_REGEX.find(body)
            ?: BOI_BARE_AMOUNT_REGEX.find(body)
            ?: return null
        val amount: Double = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            balanceAfter = BankParsing.extractAvlBal(body),
            rawMatch = match.value.trim()
        )
    }

    private companion object {
        val BOI_BARE_AMOUNT_REGEX: Regex = Regex("(?i)\\bfor\\s+([\\d,]+\\.\\d{1,2})\\b")
    }
}

// ──────────────────────────────────────────────────────────────────────────
// EPFO
// ──────────────────────────────────────────────────────────────────────────

class EpfoParser : BankParser {
    override val bankCode: BankCode = BankCode.EPFO

    override fun match(body: String, accounts: List<Account>): ParsedTransaction? {
        if (!body.contains("Contribution", ignoreCase = true)) return null
        for (account in accounts) {
            if (account.accountTail.isBlank() || !body.contains(account.accountTail)) continue
            val contributionMatch: MatchResult = EPFO_CONTRIBUTION.find(body) ?: continue
            val amount: Double = contributionMatch.groupValues[1].replace(",", "").toDoubleOrNull()
                ?: continue
            val balanceMatch: MatchResult? = EPFO_BALANCE.find(body)
            val balance: Double? = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            return ParsedTransaction(
                account = account,
                amount = amount,
                type = TransactionType.CREDIT,
                balanceAfter = balance,
                rawMatch = contributionMatch.value
            )
        }
        return null
    }

    private companion object {
        val EPFO_CONTRIBUTION: Regex = Regex("(?i)Contribution of Rs\\.?\\s*([\\d,]+(?:\\.\\d+)?)")
        val EPFO_BALANCE: Regex = Regex("(?i)passbook balance[\\s\\S]*?Rs\\.?\\s*([\\d,]+(?:\\.\\d+)?)")
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Pluxee Meal Card
// ──────────────────────────────────────────────────────────────────────────

class PluxeeParser : BankParser {
    override val bankCode: BankCode = BankCode.PLUXEE

    /**
     * Pluxee spend alerts carry the card tail ("card no.xx1234"), but Meal Wallet credit
     * alerts do not -- those are accepted via the "Meal Wallet" marker instead (only when a
     * single Pluxee card is configured). Without a tail the direction must come from an
     * explicit keyword (no DEBIT default) so promo texts from the same sender never become
     * phantom transactions.
     */
    override fun match(body: String, accounts: List<Account>): ParsedTransaction? {
        if (!body.contains("Pluxee", ignoreCase = true)) return null
        val wallets: List<Account> = accounts.filter { it.accountType == AccountType.WALLET }
        val withTail: Account? = wallets.firstOrNull {
            it.accountTail.isNotBlank() && body.contains(it.accountTail)
        }
        val hasCardTail: Boolean = withTail != null
        val account: Account = withTail
            ?: wallets.singleOrNull().takeIf { body.contains("Meal Wallet", ignoreCase = true) }
            ?: return null
        val amount: Double = BankParsing.extractAmount(body) ?: return null
        val type: TransactionType = when {
            BankParsing.isReversal(body) -> TransactionType.CREDIT
            body.contains("spent", ignoreCase = true) -> TransactionType.DEBIT
            body.contains("credited", ignoreCase = true) ||
                body.contains("loaded", ignoreCase = true) ||
                body.contains("added", ignoreCase = true) -> TransactionType.CREDIT
            hasCardTail -> TransactionType.DEBIT
            else -> return null
        }
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            balanceAfter = BankParsing.extractBalance(body),
            rawMatch = BankParsing.extractRawAmount(body) ?: body
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Brand-matched wallets (no tail digits)
// ──────────────────────────────────────────────────────────────────────────

class SwiggyParser : BankParser {
    override val bankCode: BankCode = BankCode.SWIGGY

    override fun match(body: String, accounts: List<Account>): ParsedTransaction? {
        val account: Account = accounts.firstOrNull() ?: return null
        if (!body.contains("Swiggy Money", ignoreCase = true)) return null
        val amount: Double = BankParsing.extractAmount(body) ?: return null
        val type: TransactionType = when {
            BankParsing.isReversal(body) -> TransactionType.CREDIT
            body.contains("debited", ignoreCase = true) -> TransactionType.DEBIT
            body.contains("credited", ignoreCase = true) ||
                body.contains("added", ignoreCase = true) -> TransactionType.CREDIT
            else -> TransactionType.DEBIT
        }
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            balanceAfter = BankParsing.extractBalance(body),
            rawMatch = BankParsing.extractRawAmount(body) ?: body
        )
    }
}

class ZomatoParser : BankParser {
    override val bankCode: BankCode = BankCode.ZOMATO

    override fun match(body: String, accounts: List<Account>): ParsedTransaction? {
        val account: Account = accounts.firstOrNull() ?: return null
        if (!body.contains("Zomato Money", ignoreCase = true)) return null
        val amount: Double = BankParsing.extractAmount(body) ?: return null
        val type: TransactionType =
            if (BankParsing.isReversal(body)) TransactionType.CREDIT else TransactionType.DEBIT
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            balanceAfter = BankParsing.extractBalance(body),
            rawMatch = BankParsing.extractRawAmount(body) ?: body
        )
    }
}

class AmazonPayParser : BankParser {
    override val bankCode: BankCode = BankCode.AMAZON_PAY

    override fun match(body: String, accounts: List<Account>): ParsedTransaction? {
        val account: Account = accounts.firstOrNull() ?: return null
        if (!body.contains("Apay balance", ignoreCase = true) &&
            !body.contains("Amazon Pay", ignoreCase = true)
        ) return null
        val amount: Double = BankParsing.extractAmount(body) ?: return null
        val type: TransactionType = when {
            BankParsing.isReversal(body) -> TransactionType.CREDIT
            body.contains("debited", ignoreCase = true) ||
                body.contains("Payment of", ignoreCase = true) -> TransactionType.DEBIT
            body.contains("credited", ignoreCase = true) ||
                body.contains("added", ignoreCase = true) -> TransactionType.CREDIT
            else -> TransactionType.DEBIT
        }
        return ParsedTransaction(
            account = account,
            amount = amount,
            type = type,
            balanceAfter = BankParsing.extractBalance(body),
            rawMatch = BankParsing.extractRawAmount(body) ?: body
        )
    }
}

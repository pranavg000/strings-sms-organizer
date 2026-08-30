package com.strings.app.domain.transaction

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Fixtures follow the real SMS formats of each bank but use synthetic account tails,
 * names, references, and amounts.
 */
class TransactionParserTest {
    private lateinit var parser: TransactionParser
    private lateinit var accounts: List<Account>

    @Before
    fun setUp() {
        parser = TransactionParser(defaultBankParsers())
        accounts = listOf(
            account(1L, BankCode.HDFC, "HDFC Savings", AccountType.SAVINGS, "2210"),
            account(2L, BankCode.HDFC, "HDFC Card", AccountType.CREDIT_CARD, "8802"),
            account(3L, BankCode.ICICI, "ICICI Savings", AccountType.SAVINGS, "421"),
            account(4L, BankCode.BOI, "BOI Savings", AccountType.SAVINGS, "5566"),
            account(5L, BankCode.PLUXEE, "Pluxee Meal Card", AccountType.WALLET, "7788"),
            account(6L, BankCode.ZOMATO, "Zomato Money", AccountType.WALLET, ""),
            account(7L, BankCode.IDFC, "IDFC Card", AccountType.CREDIT_CARD, "3344")
        )
    }

    private fun account(
        id: Long,
        bankCode: BankCode,
        name: String,
        type: AccountType,
        tail: String
    ): Account = Account(
        id = id,
        bankName = name,
        accountTail = tail,
        accountType = type,
        displayName = name,
        bankCode = bankCode.name
    )

    private fun parse(body: String, sender: String): ParsedTransaction? =
        parser.parseTransaction(body, sender, accounts)

    @Test
    fun parsesZomatoWalletPayment() {
        val body =
            "Payment of Rs. 540.48 from Zomato Money Balance is successful. " +
                "Updated balance: Rs. 114.95. Contact Zomatomoneysupport@zomato.com for queries. -ZOMATO"
        val result: ParsedTransaction? = parse(body, "VM-ZOMATO")
        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result!!.type)
        assertEquals(540.48, result.amount, 0.001)
        assertEquals(114.95, result.balanceAfter!!, 0.001)
        assertEquals("Zomato Money", result.account.bankName)
        assertEquals(AccountType.WALLET, result.account.accountType)
    }

    @Test
    fun parsesBankDebitWithBalanceAndTail() {
        val body = "Rs.1,200.00 debited from a/c XX2210 on 10-06-26. Avl Bal Rs.45,000.00. -HDFC Bank"
        val result: ParsedTransaction? = parse(body, "VM-HDFCBK")
        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result!!.type)
        assertEquals(1200.00, result.amount, 0.001)
        assertEquals(45000.00, result.balanceAfter!!, 0.001)
        assertEquals("2210", result.account.accountTail)
        assertEquals(AccountType.SAVINGS, result.account.accountType)
    }

    @Test
    fun unsupportedBankReturnsNoMatch() {
        val body = "Your a/c XX9876 is credited with Rs.5,000 on 10Jun. Avl Bal Rs.30,000. -SBI"
        assertNull(parse(body, "VK-SBIINB"))
    }

    @Test
    fun ignoresWordBoundaryFalsePositive() {
        assertNull(parse("Rs.500 prepaid recharge successful", "VM-AIRTEL"))
    }

    @Test
    fun ignoresBalanceOnlyAlert() {
        assertNull(parse("Avl Bal Rs.45,000 in a/c XX1234 as on 10-06-26.", "VM-HDFCBK"))
    }

    @Test
    fun returnsNullWhenNoAmount() {
        assertNull(parse("Your account statement is ready to view.", "VM-HDFCBK"))
    }

    // ── Dual-keyword "debited...credited" classification ─────────────────────

    @Test
    fun boiUpiDebitNotMisclassifiedAsCredit() {
        val body = "Rs.750.00 debited A/cXX5566 and credited to PRIYA KUMAR " +
            "via UPI Ref No 124716591067 on 14Jun26. Call 18001031906, if not done by you. -BOI"
        val result: ParsedTransaction? = parse(body, "BP-BOIIND-S")
        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result!!.type)
        assertEquals(750.0, result.amount, 0.001)
        assertEquals("BOI Savings", result.account.bankName)
    }

    @Test
    fun boiCreditStillClassifiedCorrectly() {
        val body = "BOI -  Rs.650.00 Credited to your Ac XX5566 on 06-04-26 " +
            "by UPI ref No.609616036016.Avl Bal 7495.87"
        val result: ParsedTransaction? = parse(body, "JM-BOIIND-S")
        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result!!.type)
        assertEquals(650.0, result.amount, 0.001)
        assertEquals(7495.87, result.balanceAfter!!, 0.001)
    }

    @Test
    fun iciciSavingsUpiDebitNotMisclassifiedAsCredit() {
        val body = "ICICI Bank Acct XX421 debited for Rs 20000.00 on 26-May-26; " +
            "ACME BROKING credited. UPI:651243505574. Call 18002662 for dispute. " +
            "SMS BLOCK 421 to 9215676766."
        val result: ParsedTransaction? = parse(body, "AD-ICICIT-S")
        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result!!.type)
        assertEquals(20000.0, result.amount, 0.001)
        assertEquals("ICICI Savings", result.account.bankName)
    }

    @Test
    fun iciciSavingsImpsDebitNotMisclassifiedAsCredit() {
        val body = "ICICI Bank Acct XX421 debited with Rs 32,000.00 on 03-May-26 " +
            "& Acct XX880 credited.IMPS:612316795283. Call 18002662 for dispute " +
            "or SMS BLOCK 421 to 9215676766"
        val result: ParsedTransaction? = parse(body, "AX-ICICIT-S")
        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result!!.type)
        assertEquals(32000.0, result.amount, 0.001)
    }

    @Test
    fun iciciSavingsCreditStillClassifiedCorrectly() {
        val body = "ICICI Bank Account XX421 credited:Rs. 66,298.76 on 18-Mar-26. " +
            "Info CMS* CMS5593729500*EXAMPLE CO. Available Balance is Rs. 1,32,640.21."
        val result: ParsedTransaction? = parse(body, "AD-ICICIT-S")
        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result!!.type)
        assertEquals(66298.76, result.amount, 0.001)
    }

    @Test
    fun boiUpiMandateDebitWithoutTailOrCurrencyPrefix() {
        val body = "BOI UPI - Your account has been debited towards UBER INDIA SYSTEMS " +
            "PRIVATE LIMITED for 149.00 on 26/08/2026 (UPI Ref no 103913935789)."
        val result: ParsedTransaction? = parse(body, "JM-BOIIND-S")
        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result!!.type)
        assertEquals(149.0, result.amount, 0.001)
        assertEquals("BOI Savings", result.account.bankName)
    }

    @Test
    fun boiMandatePausedNoticeIsNotATransaction() {
        val body = "BOI UPI - The Mandate is set for 26-Aug-26 with Rs. 149.00 towards " +
            "UBER INDIA SYSTEMS PRIVATE LIMITED for the UPI Mandate is paused  from " +
            "26082026 to 26072031 (UPI Ref- {128511239054). Kindly undo it to continue " +
            "pay for the UPI Mandate"
        assertNull(parse(body, "JM-BOIIND-S"))
    }

    // ── Pluxee meal card ──────────────────────────────────────────────────────

    @Test
    fun pluxeeSpendParsesAsDebitWithBalance() {
        val body = "Rs. 2.00 spent from Pluxee  Meal wallet, card no.xx7788 on 31-07-2026 13:52:5 " +
            "at SWIGGY . Avl bal Rs.8798.00. Not you call 18002106919"
        val result: ParsedTransaction? = parse(body, "XX-Puxee-X")
        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result!!.type)
        assertEquals(2.0, result.amount, 0.001)
        assertEquals(8798.0, result.balanceAfter!!, 0.001)
        assertEquals("Pluxee Meal Card", result.account.bankName)
        assertEquals(AccountType.WALLET, result.account.accountType)
        assertEquals("7788", result.account.accountTail)
    }

    @Test
    fun pluxeeReversalParsesAsCredit() {
        val body = "Your Pluxee Card xx7788 has been credited with INR 2.00 on Fri Jul 31 2026 " +
            "13:57:01as a reversal against a previous transaction on Jul 31,2026 13:52:05."
        val result: ParsedTransaction? = parse(body, "XX-Puxee-X")
        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result!!.type)
        assertEquals(2.0, result.amount, 0.001)
        assertEquals("Pluxee Meal Card", result.account.bankName)
    }

    @Test
    fun pluxeeMealWalletCreditWithoutCardTail() {
        val body = "Your Pluxee Card has been successfully credited with Rs.8800 towards  " +
            "Meal Wallet on Thu Aug 27 2026 20:20:32. Your current Meal Wallet balance is Rs.10683.95."
        val result: ParsedTransaction? = parse(body, "XX-Puxee-X")
        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result!!.type)
        assertEquals(8800.0, result.amount, 0.001)
        assertEquals(10683.95, result.balanceAfter!!, 0.001)
        assertEquals("Pluxee Meal Card", result.account.bankName)
        assertEquals("7788", result.account.accountTail)
    }

    // ── IDFC First credit card ────────────────────────────────────────────────

    @Test
    fun idfcCcSpendParsesAsDebitWithTime() {
        val body = "Transaction Successful! INR 2.00 spent on your IDFC FIRST Bank Credit Card " +
            "ending XX3344 at NETFLIX on 29 AUG 2026 at 11:50 AM Avbl Limit: INR 499998 " +
            "If not done by you, call 180010888 for dispute or to block your card SMS CCBLOCK 3344 to 5676732"
        val result: ParsedTransaction? = parse(body, "AD-IDFCFB-S")
        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result!!.type)
        assertEquals(2.0, result.amount, 0.001)
        assertEquals("IDFC Card", result.account.bankName)
        assertEquals(AccountType.CREDIT_CARD, result.account.accountType)
        assertEquals("3344", result.account.accountTail)
        assertEquals("11:50", result.transactionTime)
    }

    // ── Refunds / reversals are credits, not spends ───────────────────────────

    @Test
    fun hdfcCcRefundParsesAsCredit() {
        val body = "Alert! Rs. 2 refunded by Airport Lounge         Bangalore     IND on " +
            "29/JUL/2026 & adjusted against HDFC Bank Credit Card 8802 View updated balance " +
            "here: https://1.hdfc.bank.in/HDFCBK/s/PWkLwZjk"
        val result: ParsedTransaction? = parse(body, "VM-HDFCBK")
        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result!!.type)
        assertEquals(2.0, result.amount, 0.001)
        assertEquals("HDFC Card", result.account.bankName)
    }

    @Test
    fun hdfcCcReversalParsesAsCredit() {
        val body = "Transaction Reversed!On HDFC Bank CREDIT Card xx8802 Amt: Rs.2 " +
            "By UBERINDI3806727 On 2026-07-26:11:32:07"
        val result: ParsedTransaction? = parse(body, "VM-HDFCBK")
        assertNotNull(result)
        assertEquals(TransactionType.CREDIT, result!!.type)
        assertEquals(2.0, result.amount, 0.001)
        assertEquals("11:32", result.transactionTime)
    }

    @Test
    fun hdfcCcGenuineSpendStillDebit() {
        val body = "Spent Rs.636.94 On HDFC Bank Card 8802 At SWIGGY LIMITED On 2026-07-20:19:04:31"
        val result: ParsedTransaction? = parse(body, "VM-HDFCBK")
        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result!!.type)
        assertEquals(636.94, result.amount, 0.001)
    }

    @Test
    fun iciciSavingsDebitOnlyKeyword() {
        val body = "ICICI Bank Acc XX421 debited Rs. 1,000.00 on 01-Apr-26 " +
            "InfoBIL*NEFT*IN12.Avl Bal Rs. 1,60,513.03.To dispute call 18002662 " +
            "or SMS BLOCK 421 to 9215676766"
        val result: ParsedTransaction? = parse(body, "VK-ICICIT-S")
        assertNotNull(result)
        assertEquals(TransactionType.DEBIT, result!!.type)
        assertEquals(1000.0, result.amount, 0.001)
        assertEquals(160513.03, result.balanceAfter!!, 0.001)
    }

    // ── Unconfigured accounts become suggestions, not transactions ────────────

    @Test
    fun unconfiguredTailProducesAccountSuggestion() {
        val body = "Rs.1,200.00 debited from a/c XX9999 on 10-06-26. Avl Bal Rs.45,000.00. -HDFC Bank"
        val outcome: ParseOutcome = parser.parse(body, "VM-HDFCBK", accounts)
        assertEquals(
            ParseOutcome.UnconfiguredAccount(bankCode = BankCode.HDFC.name, accountTail = "9999"),
            outcome
        )
    }

    @Test
    fun unconfiguredWalletProducesSuggestionWithEmptyTail() {
        val withoutWallets: List<Account> = accounts.filter { it.bankCode != BankCode.ZOMATO.name }
        val body =
            "Rs. 200.00 credited to your Zomato Money Balance. " +
                "Updated balance: Rs. 314.95. -ZOMATO"
        val outcome: ParseOutcome = parser.parse(body, "VM-ZOMATO", withoutWallets)
        assertEquals(
            ParseOutcome.UnconfiguredAccount(bankCode = BankCode.ZOMATO.name, accountTail = ""),
            outcome
        )
    }

    @Test
    fun disabledAccountDoesNotMatch() {
        val disabled: List<Account> = accounts.map {
            if (it.bankCode == BankCode.HDFC.name) it.copy(isEnabled = false) else it
        }
        val body = "Rs.1,200.00 debited from a/c XX2210 on 10-06-26. Avl Bal Rs.45,000.00. -HDFC Bank"
        assertNull(parser.parseTransaction(body, "VM-HDFCBK", disabled))
    }
}

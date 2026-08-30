package com.strings.app.domain.transaction

import com.strings.app.domain.model.AccountType

/**
 * Identifies a supported institution. The catalog entry for each code carries the public
 * metadata (sender principals, supported account types); the user's own accounts (last
 * digits, names, colors) live in the database and are configured from the Manage
 * accounts screen.
 */
enum class BankCode {
    AXIS, ICICI, HDFC, IDFC, BOI, EPFO, PLUXEE, SWIGGY, ZOMATO, AMAZON_PAY
}

/**
 * Public, shareable description of a supported institution.
 *
 * [principals] are the DLT sender principals (see [com.strings.app.domain.sms.SenderPrincipal])
 * that route an SMS to this bank's parser.
 * [requiresTail] is true when accounts are identified by the last digits of the account/card
 * number in the SMS body; false for brand-matched wallets that need only an on/off toggle.
 * [walletMarkers] are body substrings that identify genuine wallet activity for tail-less
 * wallets (used to suggest enabling the wallet, never to match promo texts).
 */
data class CatalogBank(
    val code: BankCode,
    val displayName: String,
    val principals: Set<String>,
    val supportedTypes: Set<AccountType>,
    val requiresTail: Boolean,
    val defaultAccountName: String? = null,
    val walletMarkers: List<String> = emptyList()
)

/**
 * Single public source of truth for every institution the parsers understand. Contains no
 * personal data -- only bank identities and SMS routing metadata.
 */
object BankCatalog {
    val banks: List<CatalogBank> = listOf(
        CatalogBank(
            code = BankCode.AXIS,
            displayName = "Axis Bank",
            principals = setOf("AXISBK"),
            supportedTypes = setOf(AccountType.SAVINGS, AccountType.CREDIT_CARD),
            requiresTail = true
        ),
        CatalogBank(
            code = BankCode.ICICI,
            displayName = "ICICI Bank",
            principals = setOf("ICICIT"),
            supportedTypes = setOf(AccountType.SAVINGS, AccountType.CREDIT_CARD),
            requiresTail = true
        ),
        CatalogBank(
            code = BankCode.HDFC,
            displayName = "HDFC Bank",
            principals = setOf("HDFCBK"),
            supportedTypes = setOf(AccountType.SAVINGS, AccountType.CREDIT_CARD),
            requiresTail = true
        ),
        CatalogBank(
            code = BankCode.IDFC,
            displayName = "IDFC First Bank",
            principals = setOf("IDFCFB"),
            supportedTypes = setOf(AccountType.CREDIT_CARD),
            requiresTail = true
        ),
        CatalogBank(
            code = BankCode.BOI,
            displayName = "Bank of India",
            principals = setOf("BOIIND"),
            supportedTypes = setOf(AccountType.SAVINGS),
            requiresTail = true
        ),
        CatalogBank(
            code = BankCode.EPFO,
            displayName = "EPFO",
            principals = setOf("EPFOHO"),
            supportedTypes = setOf(AccountType.SAVINGS),
            requiresTail = true
        ),
        CatalogBank(
            code = BankCode.PLUXEE,
            // Observed header is XX-Puxee-X; also accept the full brand spelling.
            displayName = "Pluxee Meal Card",
            principals = setOf("PUXEE", "PLUXEE"),
            supportedTypes = setOf(AccountType.WALLET),
            requiresTail = true
        ),
        CatalogBank(
            code = BankCode.SWIGGY,
            displayName = "Swiggy Money",
            principals = setOf("SWIGGY"),
            supportedTypes = setOf(AccountType.WALLET),
            requiresTail = false,
            defaultAccountName = "Swiggy Money",
            walletMarkers = listOf("Swiggy Money")
        ),
        CatalogBank(
            code = BankCode.ZOMATO,
            displayName = "Zomato Money",
            principals = setOf("ZOMATO"),
            supportedTypes = setOf(AccountType.WALLET),
            requiresTail = false,
            defaultAccountName = "Zomato Money",
            walletMarkers = listOf("Zomato Money")
        ),
        CatalogBank(
            code = BankCode.AMAZON_PAY,
            displayName = "Amazon Pay",
            principals = setOf("QCAMZN"),
            supportedTypes = setOf(AccountType.WALLET),
            requiresTail = false,
            defaultAccountName = "Amazon Pay",
            walletMarkers = listOf("Apay balance", "Amazon Pay")
        )
    )

    /** Banks whose accounts are added through the form (identified by tail digits). */
    val formBanks: List<CatalogBank> = banks.filter { it.requiresTail }

    /** Tail-less wallets that are enabled with a simple toggle. */
    val toggleWallets: List<CatalogBank> = banks.filter { !it.requiresTail }

    private val principalIndex: Map<String, CatalogBank> = buildMap {
        for (bank in banks) {
            for (principal in bank.principals) put(principal, bank)
        }
    }

    private val codeIndex: Map<String, CatalogBank> = banks.associateBy { it.code.name }

    fun byPrincipal(principal: String): CatalogBank? = principalIndex[principal]

    fun byCode(code: String): CatalogBank? = codeIndex[code]
}

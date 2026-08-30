package com.strings.app.domain.usecase

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.transaction.BankCatalog
import com.strings.app.domain.transaction.CatalogBank

/**
 * Planned repair for one wallet account row created before accounts became user-configured.
 * [adopted] is the legacy row updated with its catalog bank code; [duplicateAccountId] is a
 * toggle-created row for the same wallet that must be deleted (its recent transactions are
 * rebuilt against the adopted row by the follow-up recategorization).
 */
data class WalletAdoption(
    val adopted: Account,
    val duplicateAccountId: Long?
)

/**
 * Self-healing startup pass for wallet accounts migrated from before accounts were
 * user-configured. Legacy rows carry an empty [Account.bankCode], which the Manage accounts
 * screen shows as "needs setup" -- but tail-less wallets are enabled with a toggle, not the
 * bank/tail form, so a legacy wallet row can never be completed by hand (the bank picker
 * only lists tail-based banks). This pass adopts such rows into their catalog wallet by
 * name, deletes any duplicate the toggle has already created, and rebuilds recent
 * transaction history. Idempotent: once no legacy wallet rows remain it does nothing.
 */
class AdoptLegacyWalletAccountsUseCase(
    private val transactionRepository: TransactionRepository,
    private val recategorizeTransactionsUseCase: RecategorizeTransactionsUseCase
) {
    suspend fun execute() {
        val accounts: List<Account> = transactionRepository.getAllAccountsOnce()
        val adoptions: List<WalletAdoption> = plan(accounts)
        if (adoptions.isEmpty()) return
        for (adoption in adoptions) {
            if (adoption.duplicateAccountId != null) {
                transactionRepository.deleteAccount(adoption.duplicateAccountId)
            }
            transactionRepository.updateAccount(adoption.adopted)
        }
        recategorizeTransactionsUseCase.execute(System.currentTimeMillis() - RECATEGORIZE_WINDOW_MS)
    }

    companion object {
        private const val RECATEGORIZE_WINDOW_MS: Long = 365L * 24 * 60 * 60 * 1000

        /**
         * Pure planning step: maps each legacy (empty bankCode) row whose name matches a
         * toggle wallet to a [WalletAdoption]. The legacy row is kept (it may hold history
         * older than the recategorization window) and the toggle-created duplicate, when
         * present, donates its enabled state and color before being deleted.
         */
        fun plan(accounts: List<Account>): List<WalletAdoption> {
            return accounts
                .filter { it.bankCode.isEmpty() }
                .mapNotNull { legacy ->
                    val wallet: CatalogBank = matchWallet(legacy) ?: return@mapNotNull null
                    val duplicate: Account? = accounts.firstOrNull {
                        it.id != legacy.id && it.bankCode == wallet.code.name
                    }
                    WalletAdoption(
                        adopted = legacy.copy(
                            bankCode = wallet.code.name,
                            accountType = AccountType.WALLET,
                            isEnabled = duplicate?.isEnabled ?: legacy.isEnabled,
                            colorIndex = if (legacy.colorIndex >= 0) {
                                legacy.colorIndex
                            } else {
                                duplicate?.colorIndex ?: legacy.colorIndex
                            }
                        ),
                        duplicateAccountId = duplicate?.id
                    )
                }
        }

        private fun matchWallet(account: Account): CatalogBank? {
            return BankCatalog.toggleWallets.firstOrNull { wallet ->
                val names: List<String> = listOfNotNull(wallet.defaultAccountName, wallet.displayName)
                names.any { name ->
                    name.equals(account.displayName.trim(), ignoreCase = true) ||
                        name.equals(account.bankName.trim(), ignoreCase = true)
                }
            }
        }
    }
}

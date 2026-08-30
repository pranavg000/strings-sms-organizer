package com.strings.app.domain.usecase

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionType
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.transaction.AccountFamilies
import com.strings.app.domain.transaction.BalanceDiscrepancy
import com.strings.app.domain.transaction.BalanceReconciler
import kotlin.math.abs

/**
 * Runs the balance tally check for a transaction that just gained a known balance, merging all
 * family accounts into one ledger (matching how `computeEstimatedBalance` aggregates balances).
 * Shared by the SMS ingest path (balance parsed from the message) and the manual "Set balance"
 * path, so the discrepancy rules stay identical.
 *
 * A detected gap is also persisted as a "sentinel" transaction: a real ledger row (flagged
 * `isSentinel`) for the unaccounted amount, timestamped just before the anchor so it sits inside
 * the reconciled window without shifting the estimated balance (which starts at the anchor's
 * reported balance). It stays visible on the dashboard until the user dismisses it. The sentinel
 * shares the anchor's messageId, has no balanceAfter (it can never become an anchor itself), and
 * is replaced -- not stacked -- when the same anchor is re-checked after a balance edit.
 */
class CheckBalanceDiscrepancyUseCase(
    private val transactionRepository: TransactionRepository,
    private val balanceReconciler: BalanceReconciler
) {
    suspend fun forTransaction(transactionId: Long): BalanceDiscrepancy? {
        val transaction: Transaction = transactionRepository.getTransactionById(transactionId) ?: return null
        if (transaction.isSentinel) return null
        if (transaction.balanceAfter == null) return null
        transactionRepository.deleteSentinelsForMessage(transaction.messageId)
        val account: Account = transactionRepository.getAccountById(transaction.accountId) ?: return null
        val allAccounts: List<Account> = transactionRepository.getAllAccountsOnce()
        val familyAccountIds: List<Long> = AccountFamilies.familyAccountIds(account, allAccounts)
        val familyTransactions: List<Transaction> =
            transactionRepository.getTransactionsByAccountsOnce(familyAccountIds)
        val discrepancy: BalanceDiscrepancy? = balanceReconciler.reconcile(familyTransactions, transactionId)
        if (discrepancy != null) {
            recordSentinel(transaction, discrepancy)
        }
        return discrepancy
    }

    private suspend fun recordSentinel(anchor: Transaction, discrepancy: BalanceDiscrepancy) {
        transactionRepository.insertTransaction(
            Transaction(
                messageId = anchor.messageId,
                accountId = anchor.accountId,
                amount = abs(discrepancy.difference),
                type = if (discrepancy.difference < 0) TransactionType.DEBIT else TransactionType.CREDIT,
                balanceAfter = null,
                merchant = null,
                transactionTime = null,
                timestamp = anchor.timestamp - 1,
                rawMatch = "Balance check: expected ${discrepancy.expectedBalance}, " +
                    "reported ${discrepancy.reportedBalance}",
                isSentinel = true
            )
        )
    }

    suspend fun forMessage(messageId: Long): BalanceDiscrepancy? {
        val transaction: Transaction = transactionRepository.getTransactionForMessage(messageId) ?: return null
        if (transaction.balanceAfter == null) return null
        return forTransaction(transaction.id)
    }
}

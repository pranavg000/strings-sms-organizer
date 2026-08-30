package com.strings.app.domain.transaction

import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionType
import kotlin.math.abs

data class BalanceDiscrepancy(
    val accountId: Long,
    val expectedBalance: Double,
    val reportedBalance: Double,
    val difference: Double
)

/**
 * Checks whether a newly known balance (an "anchor": a transaction whose balanceAfter was just
 * populated from an SMS or entered manually) tallies with the previous known balance plus the
 * signed amounts of every transaction between the two anchors (inclusive of the new anchor's own
 * amount, since balanceAfter is the balance AFTER that transaction).
 *
 * Returns null when there is nothing to compare against (no earlier anchor) or when the gap is
 * within [TOLERANCE] -- so a discrepancy is only ever reported at the moment a new known balance
 * arrives, never retroactively.
 */
class BalanceReconciler {
    fun reconcile(transactions: List<Transaction>, anchorTransactionId: Long): BalanceDiscrepancy? {
        val sorted: List<Transaction> = transactions.sortedBy { it.timestamp }
        val anchorIndex: Int = sorted.indexOfFirst { it.id == anchorTransactionId }
        if (anchorIndex == -1) return null
        val anchor: Transaction = sorted[anchorIndex]
        val reported: Double = anchor.balanceAfter ?: return null
        val previousAnchorIndex: Int = sorted.subList(0, anchorIndex).indexOfLast { it.balanceAfter != null }
        if (previousAnchorIndex == -1) return null
        var expected: Double = sorted[previousAnchorIndex].balanceAfter!!
        for (i in (previousAnchorIndex + 1)..anchorIndex) {
            val transaction: Transaction = sorted[i]
            expected += when (transaction.type) {
                TransactionType.CREDIT -> transaction.amount
                TransactionType.DEBIT -> -transaction.amount
            }
        }
        val difference: Double = reported - expected
        if (abs(difference) < TOLERANCE) return null
        return BalanceDiscrepancy(
            accountId = anchor.accountId,
            expectedBalance = expected,
            reportedBalance = reported,
            difference = difference
        )
    }

    companion object {
        const val TOLERANCE: Double = 10.0
    }
}

package com.strings.app.domain.transaction

import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BalanceReconcilerTest {
    private lateinit var reconciler: BalanceReconciler

    @Before
    fun setUp() {
        reconciler = BalanceReconciler()
    }

    private fun transaction(
        id: Long,
        amount: Double,
        type: TransactionType,
        timestamp: Long,
        balanceAfter: Double? = null,
        accountId: Long = 1L
    ): Transaction = Transaction(
        id = id,
        messageId = id,
        accountId = accountId,
        amount = amount,
        type = type,
        balanceAfter = balanceAfter,
        timestamp = timestamp,
        rawMatch = "raw"
    )

    @Test
    fun `no discrepancy when balances tally exactly`() {
        val transactions: List<Transaction> = listOf(
            transaction(id = 1, amount = 500.0, type = TransactionType.CREDIT, timestamp = 1_000, balanceAfter = 10_000.0),
            transaction(id = 2, amount = 200.0, type = TransactionType.DEBIT, timestamp = 2_000),
            transaction(id = 3, amount = 300.0, type = TransactionType.DEBIT, timestamp = 3_000, balanceAfter = 9_500.0)
        )
        assertNull(reconciler.reconcile(transactions, anchorTransactionId = 3))
    }

    @Test
    fun `no discrepancy when difference is within tolerance`() {
        val transactions: List<Transaction> = listOf(
            transaction(id = 1, amount = 500.0, type = TransactionType.CREDIT, timestamp = 1_000, balanceAfter = 10_000.0),
            transaction(id = 2, amount = 300.0, type = TransactionType.DEBIT, timestamp = 2_000, balanceAfter = 9_705.0)
        )
        assertNull(reconciler.reconcile(transactions, anchorTransactionId = 2))
    }

    @Test
    fun `discrepancy reported when tally is off beyond tolerance`() {
        val transactions: List<Transaction> = listOf(
            transaction(id = 1, amount = 500.0, type = TransactionType.CREDIT, timestamp = 1_000, balanceAfter = 10_000.0),
            transaction(id = 2, amount = 200.0, type = TransactionType.DEBIT, timestamp = 2_000),
            transaction(id = 3, amount = 300.0, type = TransactionType.DEBIT, timestamp = 3_000, balanceAfter = 9_000.0)
        )
        val discrepancy: BalanceDiscrepancy? = reconciler.reconcile(transactions, anchorTransactionId = 3)
        assertNotNull(discrepancy)
        assertEquals(9_500.0, discrepancy!!.expectedBalance, 0.001)
        assertEquals(9_000.0, discrepancy.reportedBalance, 0.001)
        assertEquals(-500.0, discrepancy.difference, 0.001)
        assertEquals(1L, discrepancy.accountId)
    }

    @Test
    fun `credits and debits between anchors are both applied`() {
        val transactions: List<Transaction> = listOf(
            transaction(id = 1, amount = 100.0, type = TransactionType.DEBIT, timestamp = 1_000, balanceAfter = 5_000.0),
            transaction(id = 2, amount = 2_000.0, type = TransactionType.CREDIT, timestamp = 2_000),
            transaction(id = 3, amount = 500.0, type = TransactionType.DEBIT, timestamp = 3_000),
            transaction(id = 4, amount = 1_000.0, type = TransactionType.CREDIT, timestamp = 4_000, balanceAfter = 7_500.0)
        )
        assertNull(reconciler.reconcile(transactions, anchorTransactionId = 4))
    }

    @Test
    fun `null when there is no earlier anchor`() {
        val transactions: List<Transaction> = listOf(
            transaction(id = 1, amount = 200.0, type = TransactionType.DEBIT, timestamp = 1_000),
            transaction(id = 2, amount = 300.0, type = TransactionType.DEBIT, timestamp = 2_000, balanceAfter = 9_500.0)
        )
        assertNull(reconciler.reconcile(transactions, anchorTransactionId = 2))
    }

    @Test
    fun `null when anchor transaction has no balance`() {
        val transactions: List<Transaction> = listOf(
            transaction(id = 1, amount = 500.0, type = TransactionType.CREDIT, timestamp = 1_000, balanceAfter = 10_000.0),
            transaction(id = 2, amount = 200.0, type = TransactionType.DEBIT, timestamp = 2_000)
        )
        assertNull(reconciler.reconcile(transactions, anchorTransactionId = 2))
    }

    @Test
    fun `null when anchor transaction is not in the list`() {
        val transactions: List<Transaction> = listOf(
            transaction(id = 1, amount = 500.0, type = TransactionType.CREDIT, timestamp = 1_000, balanceAfter = 10_000.0)
        )
        assertNull(reconciler.reconcile(transactions, anchorTransactionId = 99))
    }

    @Test
    fun `manual anchor in the middle ignores later transactions`() {
        val transactions: List<Transaction> = listOf(
            transaction(id = 1, amount = 500.0, type = TransactionType.CREDIT, timestamp = 1_000, balanceAfter = 10_000.0),
            transaction(id = 2, amount = 200.0, type = TransactionType.DEBIT, timestamp = 2_000, balanceAfter = 9_800.0),
            transaction(id = 3, amount = 5_000.0, type = TransactionType.DEBIT, timestamp = 3_000)
        )
        assertNull(reconciler.reconcile(transactions, anchorTransactionId = 2))
    }

    @Test
    fun `family transactions from multiple accounts are merged into one ledger`() {
        val transactions: List<Transaction> = listOf(
            transaction(id = 1, amount = 500.0, type = TransactionType.CREDIT, timestamp = 1_000, balanceAfter = 10_000.0, accountId = 1L),
            transaction(id = 2, amount = 1_000.0, type = TransactionType.DEBIT, timestamp = 2_000, accountId = 2L),
            transaction(id = 3, amount = 300.0, type = TransactionType.DEBIT, timestamp = 3_000, balanceAfter = 9_500.0, accountId = 1L)
        )
        val discrepancy: BalanceDiscrepancy? = reconciler.reconcile(transactions, anchorTransactionId = 3)
        assertNotNull(discrepancy)
        assertEquals(8_700.0, discrepancy!!.expectedBalance, 0.001)
        assertEquals(800.0, discrepancy.difference, 0.001)
    }
}

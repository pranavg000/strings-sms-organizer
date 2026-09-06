package com.strings.app.ui.finance

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import com.strings.app.domain.model.Transaction
import com.strings.app.ui.components.DateSeparator

/**
 * Shared ledger body for the Overview tab and the Account detail screen: one
 * [TransactionRow] per transaction, with a [DateSeparator] whenever the day
 * changes (same adjacent-item comparison the inbox list uses). Expects
 * [transactions] sorted by timestamp descending.
 */
fun LazyListScope.transactionItems(
    transactions: List<Transaction>,
    accountNameFor: (Transaction) -> String?,
    onClick: (Transaction) -> Unit,
    onSetBalance: (Transaction) -> Unit,
    onDismissSentinel: (Transaction) -> Unit,
    onExcludeTransaction: (Transaction) -> Unit
) {
    itemsIndexed(
        items = transactions,
        key = { _, transaction -> transaction.id }
    ) { index, transaction ->
        val previous: Transaction? = transactions.getOrNull(index - 1)
        val label: String = transactionDateSectionLabel(transaction.timestamp)
        if (previous == null || transactionDateSectionLabel(previous.timestamp) != label) {
            DateSeparator(label)
        }
        TransactionRow(
            transaction = transaction,
            accountName = accountNameFor(transaction),
            onClick = { onClick(transaction) },
            onSetBalance = { onSetBalance(transaction) },
            onDismissSentinel = { onDismissSentinel(transaction) },
            onExcludeTransaction = { onExcludeTransaction(transaction) }
        )
    }
}

package com.strings.app.ui.finance

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import com.strings.app.domain.model.LedgerEntry
import com.strings.app.domain.model.Transaction
import com.strings.app.ui.components.DateSeparator

/**
 * Shared ledger body for the Overview tab and the Account detail screen: one
 * [TransactionRow] per entry, with a [DateSeparator] whenever the day
 * changes (same adjacent-item comparison the inbox list uses). Expects
 * [entries] sorted by timestamp descending.
 */
fun LazyListScope.transactionItems(
    entries: List<LedgerEntry>,
    accountNameFor: (Transaction) -> String?,
    actions: LedgerActions
) {
    itemsIndexed(
        items = entries,
        key = { _, entry -> entry.transaction.id }
    ) { index, entry ->
        val previous: LedgerEntry? = entries.getOrNull(index - 1)
        val label: String = transactionDateSectionLabel(entry.transaction.timestamp)
        if (previous == null || transactionDateSectionLabel(previous.transaction.timestamp) != label) {
            DateSeparator(label)
        }
        TransactionRow(
            entry = entry,
            accountName = accountNameFor(entry.transaction),
            actions = actions
        )
    }
}

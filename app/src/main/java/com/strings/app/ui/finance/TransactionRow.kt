package com.strings.app.ui.finance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strings.app.domain.model.LedgerEntry
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionOrigin
import com.strings.app.domain.model.TransactionType
import com.strings.app.ui.help.HelpTexts
import com.strings.app.ui.theme.Spacing

/**
 * Everything a ledger row can do, grouped so the Overview tab and the Account detail screen
 * wire the same set. Which actions a row actually offers depends on its origin:
 * sentinel = link / dismiss; linked = relink / balance / description / remove;
 * parsed = balance / description / not-a-transaction.
 */
data class LedgerActions(
    val onClick: (Transaction) -> Unit,
    val onSetBalance: (Transaction) -> Unit,
    val onEditDescription: (LedgerEntry) -> Unit,
    val onLinkToMessage: (Transaction) -> Unit,
    val onDismissSentinel: (Transaction) -> Unit,
    val onRemoveLinked: (Transaction) -> Unit,
    val onExcludeTransaction: (Transaction) -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    entry: LedgerEntry,
    accountName: String?,
    actions: LedgerActions
) {
    val transaction: Transaction = entry.transaction
    var showMenu: Boolean by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { actions.onClick(transaction) },
                onLongClick = if (transaction.isSentinel) null else ({ actions.onSetBalance(transaction) }),
                onLongClickLabel = "Set balance"
            )
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (accountName != null) {
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            when (transaction.origin) {
                TransactionOrigin.SENTINEL -> OriginLabel(
                    icon = Icons.Outlined.Warning,
                    text = if (transaction.type == TransactionType.DEBIT) "Unaccounted spend" else "Unaccounted credit"
                )
                TransactionOrigin.LINKED -> OriginLabel(icon = Icons.Outlined.Link, text = "Linked manually")
                TransactionOrigin.PARSED -> Unit
            }
            val description: String? = entry.messageDescription
            if (description != null && !transaction.isSentinel) {
                Text(
                    text = descriptionPreview(description),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatTransactionClock(transaction.timestamp, transaction.transactionTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatSignedAmount(transaction.amount, transaction.type),
            style = MaterialTheme.typography.titleMedium,
            color = amountColor(transaction.type)
        )
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                val descriptionLabel: String =
                    if (entry.messageDescription == null) "Add description" else "Edit description"
                when (transaction.origin) {
                    TransactionOrigin.SENTINEL -> {
                        MenuItem("Link to message", onDismiss = { showMenu = false }) {
                            actions.onLinkToMessage(transaction)
                        }
                        MenuItem("Dismiss", onDismiss = { showMenu = false }) {
                            actions.onDismissSentinel(transaction)
                        }
                    }
                    TransactionOrigin.LINKED -> {
                        MenuItem("Relink to message", onDismiss = { showMenu = false }) {
                            actions.onLinkToMessage(transaction)
                        }
                        MenuItem("Set balance", onDismiss = { showMenu = false }) {
                            actions.onSetBalance(transaction)
                        }
                        MenuItem(descriptionLabel, onDismiss = { showMenu = false }) {
                            actions.onEditDescription(entry)
                        }
                        MenuItem("Remove transaction", onDismiss = { showMenu = false }) {
                            actions.onRemoveLinked(transaction)
                        }
                    }
                    TransactionOrigin.PARSED -> {
                        MenuItem("Set balance", onDismiss = { showMenu = false }) {
                            actions.onSetBalance(transaction)
                        }
                        MenuItem(descriptionLabel, onDismiss = { showMenu = false }) {
                            actions.onEditDescription(entry)
                        }
                        MenuItem("Not a transaction", onDismiss = { showMenu = false }) {
                            actions.onExcludeTransaction(transaction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OriginLabel(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MenuItem(label: String, onDismiss: () -> Unit, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            onDismiss()
            onClick()
        }
    )
}

/**
 * Confirmation for removing a sentinel ("unaccounted") transaction. Deleting it is permanent --
 * the discrepancy is only recomputed if the anchor's balance is checked again.
 */
@Composable
internal fun DismissSentinelDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dismiss unaccounted amount?") },
        text = { Text(HelpTexts.SENTINEL_DISMISS_BODY) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Dismiss") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Confirmation for deleting a transaction the user linked to a message by hand. */
@Composable
internal fun RemoveLinkedTransactionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove linked transaction?") },
        text = { Text(HelpTexts.LINKED_REMOVE_BODY) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

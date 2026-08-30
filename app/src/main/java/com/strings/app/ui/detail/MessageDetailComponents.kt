package com.strings.app.ui.detail

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.strings.app.R
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.Tag
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionType
import com.strings.app.domain.transaction.BankCode
import com.strings.app.ui.components.tagIconFor
import com.strings.app.ui.finance.amountColor
import com.strings.app.ui.finance.formatCurrency
import com.strings.app.ui.finance.formatSignedAmount
import com.strings.app.ui.finance.formatTransactionDate
import com.strings.app.ui.theme.Spacing

/**
 * A single extensible info field shown in the message details card. New field
 * kinds (amount, links, ...) only need a new builder entry and, if interactive,
 * a new [InfoFieldAction].
 */
data class MessageInfoField(
    val label: String,
    val value: String,
    val emphasized: Boolean = false,
    val action: InfoFieldAction? = null
)

enum class InfoFieldAction { COPY }

@Composable
fun MessageDetailsCard(
    fields: List<MessageInfoField>,
    modifier: Modifier = Modifier
) {
    if (fields.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            fields.forEach { field -> DetailRow(field) }
        }
    }
}

@Composable
private fun DetailRow(field: MessageInfoField) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = field.value,
                style = if (field.emphasized) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = MaterialTheme.colorScheme.onSurface
            )
            if (field.action == InfoFieldAction.COPY) {
                IconButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText(field.label, field.value))
                            )
                        }
                        Toast.makeText(context, "${field.label} copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy ${field.label}"
                    )
                }
            }
        }
    }
}

@Composable
fun OtpDetailCard(
    otpCode: String,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
        ) {
            Text(
                text = "One-Time Password (OTP)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = otpCode,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("OTP", otpCode))
                            )
                        }
                        Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy OTP",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageTagsSheet(
    allTags: List<Tag>,
    assignedTagIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = "Manage tags",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.md)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                allTags.forEach { tag ->
                    val selected: Boolean = tag.id in assignedTagIds
                    FilterChip(
                        selected = selected,
                        onClick = { onToggle(tag.id) },
                        label = { Text(tag.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = tagIconFor(tag.icon),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionDetailCard(
    transaction: Transaction,
    account: Account?,
    modifier: Modifier = Modifier
) {
    val iconRes: Int? = bankIconResFor(account?.bankCode ?: "")
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (account != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (iconRes != null) {
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = account.displayName,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
            }
            Text(
                text = formatSignedAmount(transaction.amount, transaction.type),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor(transaction.type)
            )
            Text(
                text = if (transaction.type == TransactionType.CREDIT) "Credit Amount" else "Debit Amount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(Spacing.md))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                transaction.balanceAfter?.let { balance ->
                    TransactionDetailRow(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        label = "Available Balance",
                        value = formatCurrency(balance)
                    )
                }
                if (account != null) {
                    TransactionDetailRow(
                        icon = Icons.Outlined.CreditCard,
                        label = "Account",
                        value = formatAccountDisplay(account)
                    )
                }
                transaction.transactionTime?.let { time ->
                    TransactionDetailRow(
                        icon = Icons.Outlined.CalendarToday,
                        label = "Value Date",
                        value = formatTransactionDate(transaction.timestamp, time)
                    )
                }
                transaction.merchant?.let { merchant ->
                    TransactionDetailRow(
                        icon = Icons.Outlined.Store,
                        label = "Merchant",
                        value = merchant
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatAccountDisplay(account: Account): String {
    return account.displayName
}

private val BANK_ICON_MAP: Map<String, Int> = mapOf(
    BankCode.BOI.name to R.drawable.ic_bank_boi,
    BankCode.ICICI.name to R.drawable.ic_bank_icici,
    BankCode.AXIS.name to R.drawable.ic_bank_axis,
    BankCode.HDFC.name to R.drawable.ic_bank_hdfc
)

private fun bankIconResFor(bankCode: String): Int? {
    return BANK_ICON_MAP[bankCode]
}

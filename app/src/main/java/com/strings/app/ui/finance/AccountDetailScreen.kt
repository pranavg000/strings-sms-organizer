package com.strings.app.ui.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionType
import com.strings.app.ui.common.accountSharedBounds
import com.strings.app.ui.help.HelpTexts
import com.strings.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToMessage: (Long) -> Unit,
    viewModel: AccountDetailViewModel = koinViewModel { parametersOf(accountId) }
) {
    val account: Account? by viewModel.account.collectAsState()
    val currentMonth: YearMonth by viewModel.currentMonth.collectAsState()
    val estimatedBalance: Double? by viewModel.estimatedBalance.collectAsState()
    val summary: MonthSummary by viewModel.monthlySummary.collectAsState()
    val transactions: List<Transaction>? by viewModel.transactions.collectAsState()
    val isFamily: Boolean by viewModel.isFamily.collectAsState()
    val familyAccountsById: Map<Long, Account> by viewModel.familyAccountsById.collectAsState()
    var balanceEditTransaction: Transaction? by remember { mutableStateOf(null) }
    var sentinelToDismiss: Transaction? by remember { mutableStateOf(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.balanceDiscrepancies.collect { discrepancy ->
            snackbarHostState.showSnackbar(formatDiscrepancyMessage(discrepancy))
        }
    }

    Scaffold(
        modifier = Modifier.accountSharedBounds(accountId),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(account?.displayName ?: "Account") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item(key = "balance_card") {
                AccountBalanceCard(
                    account = account,
                    estimatedBalance = estimatedBalance,
                    summary = summary
                )
            }
            item(key = "month_nav") {
                MonthNavigationRow(
                    month = currentMonth,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    canGoNext = currentMonth < YearMonth.now()
                )
            }
            when {
                transactions == null -> item(key = "loading") {
                    FinancePlaceholder(text = "Loading…")
                }
                transactions.orEmpty().isEmpty() -> item(key = "empty") {
                    FinancePlaceholder(text = "No transactions this month")
                }
                else -> items(transactions.orEmpty(), key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        accountName = if (isFamily) familyAccountsById[transaction.accountId]?.displayName else null,
                        onClick = {
                            if (transaction.isSentinel) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(HelpTexts.SENTINEL_INFO)
                                }
                            } else {
                                onNavigateToMessage(transaction.messageId)
                            }
                        },
                        onSetBalance = { balanceEditTransaction = transaction },
                        onDismissSentinel = { sentinelToDismiss = transaction }
                    )
                }
            }
        }
    }
    balanceEditTransaction?.let { txn ->
        SetBalanceDialog(
            currentBalance = txn.balanceAfter,
            onDismiss = { balanceEditTransaction = null },
            onConfirm = { newBalance ->
                viewModel.setTransactionBalance(txn.id, newBalance)
                balanceEditTransaction = null
            }
        )
    }
    sentinelToDismiss?.let { txn ->
        DismissSentinelDialog(
            onConfirm = {
                viewModel.dismissSentinel(txn.id)
                sentinelToDismiss = null
            },
            onDismiss = { sentinelToDismiss = null }
        )
    }
}

@Composable
private fun AccountBalanceCard(
    account: Account?,
    estimatedBalance: Double?,
    summary: MonthSummary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            if (account != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = formatAccountType(account.accountType),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (account.accountTail.isNotEmpty()) {
                        Text(
                            text = "\u2022\u2022${account.accountTail}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (estimatedBalance != null) {
                Text(
                    text = "Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
                Text(
                    text = formatCurrency(estimatedBalance),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatSignedAmount(summary.totalCredits, TransactionType.CREDIT),
                        style = MaterialTheme.typography.bodyMedium,
                        color = creditColor()
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Expense",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatSignedAmount(summary.totalDebits, TransactionType.DEBIT),
                        style = MaterialTheme.typography.bodyMedium,
                        color = debitColor()
                    )
                }
            }
        }
    }
}

private fun formatAccountType(type: AccountType): String {
    return when (type) {
        AccountType.SAVINGS -> "Savings"
        AccountType.CREDIT_CARD -> "Credit Card"
        AccountType.WALLET -> "Wallet"
    }
}

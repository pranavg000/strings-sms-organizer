package com.strings.app.ui.finance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionType
import com.strings.app.ui.common.accountSharedBounds
import com.strings.app.ui.help.HelpTexts
import com.strings.app.ui.theme.CardColors
import com.strings.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.YearMonth
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMessage: (Long) -> Unit,
    onNavigateToAccountDetail: (Long) -> Unit,
    onNavigateToManageAccounts: () -> Unit,
    viewModel: FinanceDashboardViewModel = koinViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.balanceDiscrepancies.collect { discrepancy ->
            snackbarHostState.showSnackbar(formatDiscrepancyMessage(discrepancy))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Finance") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToManageAccounts) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Manage accounts")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    icon = {
                        Icon(
                            if (pagerState.currentPage == 0) Icons.Filled.PieChart else Icons.Outlined.PieChart,
                            contentDescription = "Overview"
                        )
                    },
                    label = { Text("Overview") }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    icon = {
                        Icon(
                            if (pagerState.currentPage == 1) Icons.Filled.AccountBalance else Icons.Outlined.AccountBalance,
                            contentDescription = "Accounts"
                        )
                    },
                    label = { Text("Accounts") }
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            when (page) {
                0 -> OverviewTab(
                    viewModel = viewModel,
                    onNavigateToMessage = onNavigateToMessage,
                    onShowSentinelInfo = {
                        coroutineScope.launch { snackbarHostState.showSnackbar(HelpTexts.SENTINEL_INFO) }
                    }
                )
                1 -> AccountsTab(
                    viewModel = viewModel,
                    onNavigateToAccountDetail = onNavigateToAccountDetail,
                    onNavigateToManageAccounts = onNavigateToManageAccounts
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(
    viewModel: FinanceDashboardViewModel,
    onNavigateToMessage: (Long) -> Unit,
    onShowSentinelInfo: () -> Unit
) {
    val currentMonth: YearMonth by viewModel.currentMonth.collectAsState()
    val totalBalance: Double? by viewModel.totalBalance.collectAsState()
    val summary: MonthSummary by viewModel.summary.collectAsState()
    val transactions: List<Transaction>? by viewModel.overviewTransactions.collectAsState()
    val accountsById: Map<Long, Account> by viewModel.accountsById.collectAsState()
    var balanceEditTransaction: Transaction? by remember { mutableStateOf(null) }
    var sentinelToDismiss: Transaction? by remember { mutableStateOf(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item(key = "balance_card") {
            TotalBalanceCard(totalBalance = totalBalance, summary = summary)
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
                    accountName = accountsById[transaction.accountId]?.displayName,
                    onClick = {
                        if (transaction.isSentinel) {
                            onShowSentinelInfo()
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
private fun AccountsTab(
    viewModel: FinanceDashboardViewModel,
    onNavigateToAccountDetail: (Long) -> Unit,
    onNavigateToManageAccounts: () -> Unit
) {
    val currentMonth: YearMonth by viewModel.currentMonth.collectAsState()
    val accountBalances: List<AccountBalance>? by viewModel.accountBalances.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item(key = "month_nav") {
            MonthNavigationRow(
                month = currentMonth,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                canGoNext = currentMonth < YearMonth.now()
            )
        }
        when {
            accountBalances == null -> item(key = "loading") {
                FinancePlaceholder(text = "Loading…")
            }
            accountBalances.orEmpty().isEmpty() -> item(key = "empty") {
                FinancePlaceholder(text = "No accounts yet.\nAdd your accounts to start tracking transactions.")
            }
            else -> items(accountBalances.orEmpty(), key = { it.account.id }) { accountBalance ->
                AccountCard(
                    accountBalance = accountBalance,
                    onClick = { onNavigateToAccountDetail(accountBalance.account.id) }
                )
            }
        }
        item(key = "manage_accounts") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = onNavigateToManageAccounts) {
                    Text("Manage accounts")
                }
            }
        }
    }
}

@Composable
internal fun FinancePlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TotalBalanceCard(totalBalance: Double?, summary: MonthSummary) {
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
            if (totalBalance != null) {
                Text(
                    text = "Total Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatCurrency(totalBalance),
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
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatSignedAmount(summary.totalCredits, TransactionType.CREDIT),
                        style = MaterialTheme.typography.titleMedium,
                        color = creditColor()
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Expense",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatSignedAmount(summary.totalDebits, TransactionType.DEBIT),
                        style = MaterialTheme.typography.titleMedium,
                        color = debitColor()
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    accountBalance: AccountBalance,
    onClick: () -> Unit
) {
    val colors: CardColors = rememberAccountCardColors(accountBalance.account)
    val parentDisplayName: String? = accountBalance.parentDisplayName

    Card(
        modifier = Modifier
            .accountSharedBounds(accountBalance.account.id)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colors.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
        ) {
            Text(
                text = accountBalance.account.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = colors.accent
            )
            Text(
                text = accountBalance.account.accountType.name.replace('_', ' ').lowercase()
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent.copy(alpha = 0.7f)
            )
            if (parentDisplayName != null) {
                Text(
                    text = "Linked with $parentDisplayName",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accent.copy(alpha = 0.6f)
                )
            }
            if (accountBalance.estimatedBalance != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = formatCurrency(accountBalance.estimatedBalance),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.accent
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = "This month",
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatSignedAmount(accountBalance.monthlyCredits, TransactionType.CREDIT),
                        style = MaterialTheme.typography.bodyMedium,
                        color = creditColor()
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Expense",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatSignedAmount(accountBalance.monthlyDebits, TransactionType.DEBIT),
                        style = MaterialTheme.typography.bodyMedium,
                        color = debitColor()
                    )
                }
            }
        }
    }
}

@Composable
fun MonthNavigationRow(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = formatMonth(month),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    transaction: Transaction,
    accountName: String? = null,
    onClick: () -> Unit,
    onSetBalance: () -> Unit,
    onDismissSentinel: () -> Unit = {}
) {
    var showMenu: Boolean by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (transaction.isSentinel) null else onSetBalance,
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
            if (transaction.isSentinel) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (transaction.type == TransactionType.DEBIT) {
                            "Unaccounted spend"
                        } else {
                            "Unaccounted credit"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Text(
                text = formatTransactionDate(transaction.timestamp, transaction.transactionTime),
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
                if (transaction.isSentinel) {
                    DropdownMenuItem(
                        text = { Text("Dismiss") },
                        onClick = {
                            showMenu = false
                            onDismissSentinel()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Set balance") },
                        onClick = {
                            showMenu = false
                            onSetBalance()
                        }
                    )
                }
            }
        }
    }
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



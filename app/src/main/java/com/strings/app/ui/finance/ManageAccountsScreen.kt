package com.strings.app.ui.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountSuggestion
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.transaction.BankCatalog
import com.strings.app.domain.transaction.CatalogBank
import com.strings.app.ui.components.InfoTooltipIcon
import com.strings.app.ui.help.HelpTexts
import com.strings.app.ui.theme.CardColors
import com.strings.app.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToCreate: (String, String) -> Unit,
    viewModel: ManageAccountsViewModel = koinViewModel()
) {
    val accounts: List<Account> by viewModel.accounts.collectAsStateWithLifecycle()
    val suggestions: List<AccountSuggestion> by viewModel.suggestions.collectAsStateWithLifecycle()
    val formAccounts: List<Account> = accounts.filter { account ->
        BankCatalog.byCode(account.bankCode)?.requiresTail != false
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Manage accounts") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    InfoTooltipIcon(
                        text = HelpTexts.ACCOUNTS_LIST,
                        title = "How accounts work"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToCreate("", "") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add account")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (suggestions.isNotEmpty()) {
                item(key = "suggestions_header") {
                    SectionHeader(text = "Detected accounts")
                }
                items(items = suggestions, key = { "suggestion_${it.id}" }) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        onAdd = {
                            val bank: CatalogBank? = BankCatalog.byCode(suggestion.bankCode)
                            if (bank != null && !bank.requiresTail) {
                                viewModel.setWalletEnabled(bank, true)
                            } else {
                                onNavigateToCreate(suggestion.bankCode, suggestion.accountTail)
                            }
                        },
                        onDismiss = { viewModel.dismissSuggestion(suggestion.id) }
                    )
                }
            }
            item(key = "accounts_header") {
                SectionHeader(text = "Your accounts")
            }
            if (formAccounts.isEmpty()) {
                item(key = "accounts_empty") {
                    Text(
                        text = "No accounts yet. Tap + to add one \u2014 transactions from its " +
                            "SMS alerts will start appearing on the Finance dashboard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.sm)
                    )
                }
            } else {
                items(items = formAccounts, key = { it.id }) { account ->
                    AccountRow(
                        account = account,
                        parentName = accounts.firstOrNull { it.id == account.parentAccountId }?.displayName,
                        onClick = { onNavigateToEdit(account.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
            item(key = "wallets_header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(text = "Wallets")
                    InfoTooltipIcon(text = HelpTexts.ACCOUNT_WALLETS)
                }
            }
            items(items = BankCatalog.toggleWallets, key = { "wallet_${it.code.name}" }) { wallet ->
                WalletRow(
                    wallet = wallet,
                    enabled = accounts.any { it.bankCode == wallet.code.name && it.isEnabled },
                    onToggle = { enabled -> viewModel.setWalletEnabled(wallet, enabled) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
    )
}

@Composable
private fun SuggestionCard(
    suggestion: AccountSuggestion,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val bankName: String = BankCatalog.byCode(suggestion.bankCode)?.displayName ?: suggestion.bankCode
    val title: String = if (suggestion.accountTail.isEmpty()) {
        "$bankName activity detected"
    } else {
        "New account \u2022\u2022${suggestion.accountTail} from $bankName"
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Found in a transactional SMS that matches no configured account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                TextButton(onClick = onAdd) { Text("Add") }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    parentName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors: CardColors = rememberAccountCardColors(account)
    val bank: CatalogBank? = BankCatalog.byCode(account.bankCode)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = colors.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.accent
                )
                val subtitle: String = buildString {
                    append(bank?.displayName ?: "Needs setup \u2014 tap to pick a bank")
                    if (account.accountTail.isNotEmpty()) {
                        append(" \u2022 \u2022\u2022")
                        append(account.accountTail)
                    }
                    if (!account.isEnabled) {
                        append(" \u2022 Paused")
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bank == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        colors.accent.copy(alpha = 0.7f)
                    }
                )
                if (parentName != null) {
                    Text(
                        text = "Linked with $parentName",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent.copy(alpha = 0.6f)
                    )
                }
            }
            Text(
                text = accountTypeLabel(account.accountType),
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun WalletRow(
    wallet: CatalogBank,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!enabled) }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = wallet.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                thumbContent = if (enabled) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}

internal fun accountTypeLabel(type: AccountType): String {
    return when (type) {
        AccountType.SAVINGS -> "Savings"
        AccountType.CREDIT_CARD -> "Credit card"
        AccountType.WALLET -> "Wallet"
    }
}

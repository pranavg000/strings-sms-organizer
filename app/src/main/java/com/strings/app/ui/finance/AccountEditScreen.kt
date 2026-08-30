package com.strings.app.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.transaction.BankCatalog
import com.strings.app.domain.transaction.CatalogBank
import com.strings.app.ui.components.InfoTooltipIcon
import com.strings.app.ui.help.HelpTexts
import com.strings.app.ui.theme.AppPalette
import com.strings.app.ui.theme.CardColors
import com.strings.app.ui.theme.Spacing
import com.strings.app.ui.theme.resolveCardColors
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    accountId: Long,
    prefillBankCode: String,
    prefillTail: String,
    onNavigateBack: () -> Unit,
    viewModel: ManageAccountsViewModel = koinViewModel()
) {
    LaunchedEffect(accountId) {
        viewModel.loadAccountForEdit(accountId, prefillBankCode, prefillTail)
    }
    val state: AccountEditState by viewModel.editState.collectAsStateWithLifecycle()
    val bank: CatalogBank? = BankCatalog.byCode(state.bankCode)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New account" else "Edit account") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.requestDeleteAccount() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete account")
                        }
                    }
                    IconButton(
                        onClick = { viewModel.saveAccount(onNavigateBack) },
                        enabled = !state.isSaving
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            BankSelector(
                selectedBankCode = state.bankCode,
                onSelect = { viewModel.updateBank(it) }
            )
            if (bank != null && bank.supportedTypes.size > 1) {
                AccountTypeSelector(
                    supportedTypes = bank.supportedTypes.toList(),
                    selected = state.accountType,
                    onSelect = { viewModel.updateAccountType(it) }
                )
            }
            if (bank == null || bank.requiresTail) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.accountTail,
                        onValueChange = { viewModel.updateAccountTail(it) },
                        label = { Text("Last digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    InfoTooltipIcon(text = HelpTexts.ACCOUNT_TAIL)
                }
            }
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Account name") },
                supportingText = { Text("Used as the card title and the finance tag") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (state.accountType == AccountType.CREDIT_CARD) {
                val parentOptions: List<Account> = viewModel.parentOptions()
                if (parentOptions.isNotEmpty() || state.parentAccountId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            ParentAccountSelector(
                                selectedParentId = state.parentAccountId,
                                options = parentOptions,
                                onSelect = { viewModel.updateParentAccount(it) }
                            )
                        }
                        InfoTooltipIcon(text = HelpTexts.ACCOUNT_PARENT)
                    }
                }
            }
            if (!state.isNew) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Track this account", style = MaterialTheme.typography.bodyLarge)
                        InfoTooltipIcon(text = HelpTexts.ACCOUNT_TRACKING)
                    }
                    Switch(
                        checked = state.isEnabled,
                        onCheckedChange = { viewModel.updateIsEnabled(it) },
                        thumbContent = if (state.isEnabled) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
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
            Text("Card color", style = MaterialTheme.typography.titleSmall)
            ColorSwatchRow(
                selectedIndex = state.colorIndex,
                onSelect = { viewModel.updateColorIndex(it) }
            )
            if (state.validationError != null) {
                Text(
                    text = state.validationError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "\"${state.name}\" and all its detected transactions will be deleted. " +
                        "Your messages are not affected. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteAccount(onNavigateBack) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankSelector(
    selectedBankCode: String,
    onSelect: (String) -> Unit
) {
    var expanded: Boolean by remember { mutableStateOf(false) }
    val selectedName: String = BankCatalog.byCode(selectedBankCode)?.displayName ?: "Select bank"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Bank") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BankCatalog.formBanks.forEach { bank ->
                val isSelected: Boolean = bank.code.name == selectedBankCode
                DropdownMenuItem(
                    text = { Text(bank.displayName) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(bank.code.name)
                        expanded = false
                    },
                    modifier = if (isSelected) {
                        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@Composable
private fun AccountTypeSelector(
    supportedTypes: List<AccountType>,
    selected: AccountType,
    onSelect: (AccountType) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        supportedTypes.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = supportedTypes.size)
            ) {
                Text(accountTypeLabel(type))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentAccountSelector(
    selectedParentId: Long?,
    options: List<Account>,
    onSelect: (Long?) -> Unit
) {
    var expanded: Boolean by remember { mutableStateOf(false) }
    val selectedName: String = options.firstOrNull { it.id == selectedParentId }?.displayName ?: "None"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Linked primary card") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val entries: List<Pair<Long?, String>> =
                listOf<Pair<Long?, String>>(null to "None") + options.map { it.id to it.displayName }
            entries.forEach { (id, name) ->
                val isSelected: Boolean = id == selectedParentId
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                    modifier = if (isSelected) {
                        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AppPalette.indices.forEach { index ->
            val colors: CardColors = resolveCardColors(index)
            val isSelected: Boolean = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.container, CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

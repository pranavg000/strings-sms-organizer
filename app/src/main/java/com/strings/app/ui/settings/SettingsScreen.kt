package com.strings.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strings.app.data.prefs.ThemeMode
import com.strings.app.ui.backup.BackupViewModel
import com.strings.app.ui.theme.Spacing
import com.strings.app.util.AppLockController
import com.strings.app.util.BiometricAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val THEME_OPTIONS: List<Pair<ThemeMode, String>> = listOf(
    ThemeMode.SYSTEM to "System",
    ThemeMode.LIGHT to "Light",
    ThemeMode.DARK to "Dark"
)

private const val RELEASES_URL: String = "https://github.com/pranavg000/strings-sms-organizer/releases/latest"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
    backupViewModel: BackupViewModel = koinViewModel()
) {
    val themeMode: ThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appLockEnabled: Boolean by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val settingsBusy: Boolean by viewModel.isBusy.collectAsStateWithLifecycle()
    val backupBusy: Boolean by backupViewModel.isBusy.collectAsStateWithLifecycle()
    val isBusy: Boolean = settingsBusy || backupBusy
    val context = LocalContext.current
    val appLockController: AppLockController = koinInject()
    val canUseAppLock: Boolean = remember { BiometricAuth.isAvailable(context) }
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showClearFinanceConfirm: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        merge(viewModel.messages, backupViewModel.messages).collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    // The data operations live in the ViewModels (viewModelScope + NonCancellable) so
    // leaving this screen mid-import can't abort them; the screen only owns the SAF
    // streams. Pickers are flagged as trusted so the app lock doesn't relock on return.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupViewModel.export { jsonString ->
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(jsonString.toByteArray())
            } ?: throw IllegalStateException("Could not open the selected file.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupViewModel.import {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().decodeToString()
            } ?: throw IllegalStateException("Could not read the selected file.")
        }
    }
    val categorizationExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val message: String = try {
                val jsonString: String = viewModel.exportCategorizationJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(jsonString.toByteArray())
                    } ?: throw IllegalStateException("Could not open the selected file.")
                }
                "Categorization exported"
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    val categorizationExportLastYearLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val message: String = try {
                val jsonString: String = viewModel.exportCategorizationLastYearJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(jsonString.toByteArray())
                    } ?: throw IllegalStateException("Could not open the selected file.")
                }
                "Categorization exported"
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            SettingsSectionLabel(text = "Appearance")
            SettingsCard {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleMedium
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm)
                ) {
                    THEME_OPTIONS.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = THEME_OPTIONS.size
                            )
                        ) {
                            Text(label)
                        }
                    }
                }
            }
            SettingsSectionLabel(text = "Security")
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App lock",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (canUseAppLock) {
                                "Require fingerprint or device lock to open Strings. " +
                                    "Also blocks screenshots and hides the app preview in Recents."
                            } else {
                                "Set up a screen lock on this device to use App lock"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = { wantEnabled ->
                            if (wantEnabled) {
                                viewModel.setAppLockEnabled(true)
                            } else {
                                BiometricAuth.authenticate(
                                    context = context,
                                    title = "Confirm to turn off App lock"
                                ) {
                                    viewModel.setAppLockEnabled(false)
                                }
                            }
                        },
                        enabled = canUseAppLock,
                        thumbContent = if (appLockEnabled) {
                            {
                                Icon(
                                    Icons.Default.Check,
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
            SettingsSectionLabel(text = "Data")
            SettingsActionCard(
                title = "Export data",
                supportingText = "Save tags, filters, tabs, message states, and balances as a JSON backup",
                icon = Icons.Default.FileUpload,
                onClick = {
                    appLockController.expectTrustedActivityResult()
                    exportLauncher.launch("strings-backup.json")
                },
                enabled = !isBusy
            )
            SettingsActionCard(
                title = "Import data",
                supportingText = "Restore everything from a Strings backup file",
                icon = Icons.Default.FileDownload,
                onClick = {
                    appLockController.expectTrustedActivityResult()
                    importLauncher.launch(arrayOf("application/json"))
                },
                enabled = !isBusy
            )
            SettingsActionCard(
                title = "Clear finance data",
                supportingText = "Delete all transactions, accounts, and Finance tags. This can't be undone.",
                icon = Icons.Default.CleaningServices,
                onClick = { showClearFinanceConfirm = true },
                isDestructive = true,
                enabled = !isBusy
            )
            var advancedExpanded: Boolean by remember { mutableStateOf(false) }
            SettingsSectionLabel(text = "Advanced")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Developer tools",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { advancedExpanded = !advancedExpanded }) {
                        Icon(
                            imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (advancedExpanded) "Collapse" else "Expand"
                        )
                    }
                }
            }
            AnimatedVisibility(visible = advancedExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SettingsActionCard(
                        title = "Export categorization (last 3 months)",
                        icon = Icons.Default.FileUpload,
                        onClick = {
                            appLockController.expectTrustedActivityResult()
                            categorizationExportLauncher.launch("strings-categorization.json")
                        },
                        enabled = !isBusy
                    )
                    SettingsActionCard(
                        title = "Recategorize (last 3 months)",
                        icon = Icons.Default.Autorenew,
                        onClick = { viewModel.recategorizeRecent() },
                        enabled = !isBusy
                    )
                    SettingsActionCard(
                        title = "Export categorization (last 1 year)",
                        icon = Icons.Default.FileUpload,
                        onClick = {
                            appLockController.expectTrustedActivityResult()
                            categorizationExportLastYearLauncher.launch("strings-categorization-1y.json")
                        },
                        enabled = !isBusy
                    )
                    SettingsActionCard(
                        title = "Recategorize (last 1 year)",
                        icon = Icons.Default.Autorenew,
                        onClick = { viewModel.recategorizeLastYear() },
                        enabled = !isBusy
                    )
                }
            }
            SettingsSectionLabel(text = "About")
            val versionName: String = remember {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            }
            SettingsActionCard(
                title = "Check for updates",
                supportingText = "Installed version $versionName. Opens the releases page in your " +
                    "browser -- Strings itself has no internet access.",
                icon = Icons.Default.SystemUpdate,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, RELEASES_URL.toUri()))
                }
            )
        }
    }
    if (showClearFinanceConfirm) {
        AlertDialog(
            onDismissRequest = { showClearFinanceConfirm = false },
            title = { Text("Clear finance data?") },
            text = {
                Text("All transactions, accounts, and Finance tags will be deleted. This can't be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearFinanceConfirm = false
                        viewModel.clearFinanceData()
                    }
                ) {
                    Text("Clear data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearFinanceConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
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
            content()
        }
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    supportingText: String? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(Spacing.lg))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDestructive) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurface
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

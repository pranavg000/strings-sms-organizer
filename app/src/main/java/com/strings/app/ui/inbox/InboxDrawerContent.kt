package com.strings.app.ui.inbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.strings.app.ui.theme.Spacing

private val DrawerLabelStart = 28.dp

/**
 * Navigation drawer with a fixed set of destinations. It deliberately contains no data-driven
 * rows: top-level tags are reachable as inbox tabs, every tag is browsable from the Tags page,
 * and a filter's matches are reachable from its row on the Filters page.
 */
@Composable
fun InboxDrawerContent(
    onAllMessagesClick: () -> Unit,
    onArchivedMessagesClick: () -> Unit,
    onTrashedMessagesClick: () -> Unit,
    onTagsClick: () -> Unit,
    onFiltersClick: () -> Unit,
    onFinanceDashboardClick: () -> Unit,
    onManageAccountsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Strings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = DrawerLabelStart, top = Spacing.xl, bottom = Spacing.md)
            )
            DrawerItem(label = "All messages", icon = Icons.Default.AllInbox, onClick = onAllMessagesClick)
            DrawerItem(label = "Archived", icon = Icons.Default.Archive, onClick = onArchivedMessagesClick)
            DrawerItem(label = "Trash", icon = Icons.Default.Delete, onClick = onTrashedMessagesClick)
            DrawerDivider()
            DrawerItem(label = "Tags", icon = Icons.AutoMirrored.Filled.Label, onClick = onTagsClick)
            DrawerItem(label = "Filters", icon = Icons.Default.FilterList, onClick = onFiltersClick)
            DrawerDivider()
            DrawerItem(label = "Finance", icon = Icons.Default.AccountBalance, onClick = onFinanceDashboardClick)
            DrawerItem(label = "Accounts", icon = Icons.Default.CreditCard, onClick = onManageAccountsClick)
            DrawerDivider()
            DrawerItem(label = "Settings", icon = Icons.Default.Settings, onClick = onSettingsClick)
            DrawerItem(label = "Help", icon = Icons.AutoMirrored.Filled.HelpOutline, onClick = onHelpClick)
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        icon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
private fun DrawerDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = DrawerLabelStart, vertical = Spacing.sm))
}

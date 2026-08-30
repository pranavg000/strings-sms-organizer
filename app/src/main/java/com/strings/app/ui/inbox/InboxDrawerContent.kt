package com.strings.app.ui.inbox

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.Tag
import com.strings.app.ui.components.tagIconFor
import com.strings.app.ui.theme.Spacing

private val DrawerLabelStart = 28.dp

data class TagNode(
    val tag: Tag,
    val children: List<TagNode>
)

fun buildTagTree(tags: List<Tag>): List<TagNode> {
    val byParent: Map<Long?, List<Tag>> = tags.groupBy { it.parentTagId }
    fun build(parentId: Long?): List<TagNode> =
        (byParent[parentId] ?: emptyList())
            .sortedBy { it.sortOrder }
            .map { TagNode(it, build(it.id)) }
    return build(null)
}

@Composable
fun InboxDrawerContent(
    allTags: List<Tag>,
    allFilters: List<Filter>,
    activeTagId: Long? = null,
    onAllMessagesClick: () -> Unit,
    onArchivedMessagesClick: () -> Unit,
    onTrashedMessagesClick: () -> Unit,
    onTagClick: (Long) -> Unit,
    onFilterClick: (Long) -> Unit,
    onFiltersClick: () -> Unit,
    onManageTagsClick: () -> Unit,
    onFinanceDashboardClick: () -> Unit,
    onManageAccountsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    val tree = remember(allTags) { buildTagTree(allTags) }
    val expandedIds = remember { mutableStateListOf<Long>() }
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
            NavigationDrawerItem(
                label = { Text("All messages") },
                selected = false,
                icon = { Icon(Icons.Default.AllInbox, contentDescription = null) },
                onClick = onAllMessagesClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Archived") },
                selected = false,
                icon = { Icon(Icons.Default.Archive, contentDescription = null) },
                onClick = onArchivedMessagesClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Trash") },
                selected = false,
                icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = onTrashedMessagesClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            Text(
                text = "Filter by tags",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = DrawerLabelStart, top = Spacing.sm, bottom = Spacing.xs)
            )
            TagTreeItems(
                nodes = tree,
                depth = 0,
                isExpanded = { it in expandedIds },
                onToggleExpand = { id ->
                    if (id in expandedIds) expandedIds.remove(id) else expandedIds.add(id)
                },
                onSelect = onTagClick,
                activeTagId = activeTagId
            )
            if (allFilters.isNotEmpty()) {
                Text(
                    text = "Filter by rule",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = DrawerLabelStart, top = Spacing.md, bottom = Spacing.xs)
                )
                allFilters.forEach { filter ->
                    NavigationDrawerItem(
                        label = { Text(filter.name.ifEmpty { "Unnamed filter" }) },
                        selected = false,
                        icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                        onClick = { onFilterClick(filter.id) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = DrawerLabelStart, vertical = Spacing.md))
            Text(
                text = "Manage",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = DrawerLabelStart, bottom = Spacing.xs)
            )
            NavigationDrawerItem(
                label = { Text("Filters") },
                selected = false,
                icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                onClick = onFiltersClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Tags") },
                selected = false,
                icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                onClick = onManageTagsClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = DrawerLabelStart, vertical = Spacing.md))
            Text(
                text = "Finance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = DrawerLabelStart, bottom = Spacing.xs)
            )
            NavigationDrawerItem(
                label = { Text("Dashboard") },
                selected = false,
                icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                onClick = onFinanceDashboardClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Accounts") },
                selected = false,
                icon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                onClick = onManageAccountsClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = DrawerLabelStart, vertical = Spacing.md))
            NavigationDrawerItem(
                label = { Text("Settings") },
                selected = false,
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = onSettingsClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Help") },
                selected = false,
                icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                onClick = onHelpClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

@Composable
private fun TagTreeItems(
    nodes: List<TagNode>,
    depth: Int,
    isExpanded: (Long) -> Boolean,
    onToggleExpand: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    activeTagId: Long? = null
) {
    nodes.forEach { node ->
        val hasChildren = node.children.isNotEmpty()
        val expanded = isExpanded(node.tag.id)
        NavigationDrawerItem(
            label = { Text(node.tag.name) },
            selected = node.tag.id == activeTagId,
            icon = { Icon(tagIconFor(node.tag.icon), contentDescription = null) },
            badge = {
                if (hasChildren) {
                    val chevronRotation: Float by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        label = "chevronRotation"
                    )
                    IconButton(onClick = { onToggleExpand(node.tag.id) }) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.rotate(chevronRotation)
                        )
                    }
                }
            },
            onClick = { onSelect(node.tag.id) },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .padding(start = Spacing.lg * depth)
        )
        if (hasChildren && expanded) {
            TagTreeItems(
                nodes = node.children,
                depth = depth + 1,
                isExpanded = isExpanded,
                onToggleExpand = onToggleExpand,
                onSelect = onSelect,
                activeTagId = activeTagId
            )
        }
    }
}

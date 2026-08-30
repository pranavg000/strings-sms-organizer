package com.strings.app.ui.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.Tag
import com.strings.app.domain.model.summary
import com.strings.app.ui.components.InfoTooltipIcon
import com.strings.app.ui.help.HelpTexts
import com.strings.app.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToCreate: () -> Unit,
    viewModel: FilterViewModel = koinViewModel()
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    val orderedFilters by viewModel.orderedFilters.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.moveFilter(from.index, to.index)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Manage filters") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    InfoTooltipIcon(
                        text = HelpTexts.FILTER_LIST,
                        title = "How filters run"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add filter")
            }
        }
    ) { padding ->
        if (orderedFilters.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No filters yet.\nTap + to create one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(items = orderedFilters, key = { it.id }) { filter ->
                    ReorderableItem(reorderableState, key = filter.id) { _ ->
                        FilterCard(
                            filter = filter,
                            tags = state.tags,
                            onClick = { onNavigateToEdit(filter.id) },
                            onToggleEnabled = { enabled ->
                                viewModel.toggleFilterEnabled(filter.id, enabled)
                            },
                            dragHandle = {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Reorder filter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .draggableHandle(
                                            onDragStopped = { viewModel.persistFilterOrder() }
                                        )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterCard(
    filter: Filter,
    tags: List<Tag>,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    dragHandle: @Composable () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filter.name.ifEmpty { "Unnamed filter" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = filter.root.summary().ifEmpty { "No conditions" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = actionsSummary(filter, tags),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Switch(
                checked = filter.isEnabled,
                onCheckedChange = onToggleEnabled,
                thumbContent = if (filter.isEnabled) {
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
            Spacer(modifier = Modifier.width(Spacing.sm))
            dragHandle()
        }
    }
}

private fun actionsSummary(filter: Filter, tags: List<Tag>): String {
    if (filter.actions.isEmpty()) return "No actions"
    return filter.actions.joinToString(" \u00B7 ") { action ->
        when (action.actionType) {
            ActionType.ASSIGN_TAG -> {
                val name = tags.firstOrNull { it.id == action.targetTagId }?.name ?: "tag"
                "Tag: $name"
            }
            ActionType.REMOVE_FROM_INBOX -> "Remove from inbox"
            ActionType.ARCHIVE -> "Archive"
            ActionType.TRASH -> "Trash"
            ActionType.MARK_READ -> "Mark read"
            ActionType.SUPPRESS_NOTIFICATION -> "Suppress notification"
            ActionType.NOTIFY_SILENTLY -> "Notify silently"
            ActionType.STOP_PROCESSING -> "Stop processing"
        }
    }
}

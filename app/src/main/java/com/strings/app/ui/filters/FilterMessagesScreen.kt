package com.strings.app.ui.filters

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.strings.app.ui.components.SelectableMessageScaffold
import org.koin.androidx.compose.koinViewModel

@Composable
fun FilterMessagesScreen(
    filterId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToFilterEdit: () -> Unit,
    viewModel: FilterMessagesViewModel = koinViewModel()
) {
    LaunchedEffect(filterId) {
        viewModel.loadFilter(filterId)
    }
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val messages = remember(filterId) { viewModel.messagesForFilter(filterId) }.collectAsLazyPagingItems()
    val listState = rememberSaveable(filterId, saver = LazyListState.Saver) { LazyListState() }
    val selectedIds by viewModel.selectedMessageIds.collectAsStateWithLifecycle()
    SelectableMessageScaffold(
        title = filter?.name?.ifEmpty { "Filter" } ?: "",
        messages = messages,
        listState = listState,
        selectedIds = selectedIds,
        onNavigateBack = onNavigateBack,
        onToggleSelection = { viewModel.toggleMessageSelection(it) },
        onClearSelection = { viewModel.clearSelection() },
        onOpenMessage = onNavigateToDetail,
        onSelectionAction = { viewModel.performSelectionAction(it) },
        onSelectAll = { viewModel.selectAll(it) },
        onSuggestFilter = { viewModel.suggestFilterFromSelection() },
        onNavigateToFilterEdit = onNavigateToFilterEdit,
        undoEvents = viewModel.undoEvents,
        onUndo = { viewModel.undo(it) },
        emptyText = "No messages match this filter"
    )
}

package com.strings.app.ui.inbox

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.strings.app.ui.common.SelectionAction
import com.strings.app.ui.components.SelectableMessageScaffold
import org.koin.androidx.compose.koinViewModel

@Composable
fun ArchivedMessagesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToFilterEdit: () -> Unit,
    viewModel: ArchivedMessagesViewModel = koinViewModel()
) {
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val selectedIds by viewModel.selectedMessageIds.collectAsStateWithLifecycle()
    SelectableMessageScaffold(
        title = "Archived",
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
        emptyText = "No archived messages",
        selectionActions = listOf(
            SelectionAction.SELECT_ALL,
            SelectionAction.SUGGEST_FILTER,
            SelectionAction.UNARCHIVE,
            SelectionAction.TRASH
        )
    )
}

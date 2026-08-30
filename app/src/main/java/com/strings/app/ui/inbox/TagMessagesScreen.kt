package com.strings.app.ui.inbox

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
fun TagMessagesScreen(
    tagId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToFilterEdit: () -> Unit,
    viewModel: TagMessagesViewModel = koinViewModel()
) {
    LaunchedEffect(tagId) {
        viewModel.loadTag(tagId)
    }
    val tag by viewModel.tag.collectAsStateWithLifecycle()
    val messages = remember(tagId) { viewModel.messagesForTag(tagId) }.collectAsLazyPagingItems()
    val listState = rememberSaveable(tagId, saver = LazyListState.Saver) { LazyListState() }
    val selectedIds by viewModel.selectedMessageIds.collectAsStateWithLifecycle()
    SelectableMessageScaffold(
        title = tag?.name ?: "",
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
        emptyText = "No messages for this tag",
        hiddenTagIds = setOf(tagId)
    )
}

package com.strings.app.ui.inbox

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.strings.app.ui.common.SelectionAction
import com.strings.app.ui.components.SelectableMessageScaffold
import org.koin.androidx.compose.koinViewModel

@Composable
fun TrashedMessagesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToFilterEdit: () -> Unit,
    viewModel: TrashedMessagesViewModel = koinViewModel()
) {
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val selectedIds by viewModel.selectedMessageIds.collectAsStateWithLifecycle()
    var showEmptyTrashConfirm: Boolean by remember { mutableStateOf(false) }
    SelectableMessageScaffold(
        title = "Trash",
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
        emptyText = "No trashed messages",
        selectionActions = listOf(
            SelectionAction.SELECT_ALL,
            SelectionAction.RESTORE,
            SelectionAction.DELETE_FOREVER
        ),
        topBarActions = {
            if (messages.itemCount > 0) {
                IconButton(onClick = { showEmptyTrashConfirm = true }) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Empty Trash")
                }
            }
        }
    )
    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text("Empty Trash?") },
            text = { Text("All trashed messages will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyTrashConfirm = false
                        viewModel.emptyTrash()
                    }
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

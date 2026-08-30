package com.strings.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.strings.app.domain.model.Message
import com.strings.app.ui.common.DefaultSelectionActions
import com.strings.app.ui.common.SelectionAction
import com.strings.app.ui.common.SelectionUndoEvent
import com.strings.app.ui.theme.NavTransitions
import com.strings.app.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSelectionTopBar(
    count: Int,
    actions: List<SelectionAction>,
    onClose: () -> Unit,
    onAction: (SelectionAction) -> Unit
) {
    TopAppBar(
        title = {
            Row {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically { -it } + fadeIn())
                                .togetherWith(slideOutVertically { it } + fadeOut())
                        } else {
                            (slideInVertically { it } + fadeIn())
                                .togetherWith(slideOutVertically { -it } + fadeOut())
                        }
                    },
                    label = "selectionCount"
                ) { value ->
                    Text("$value")
                }
                Text(" selected")
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
        },
        actions = {
            actions.forEach { action ->
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(action.label) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = { onAction(action) }) {
                        Icon(action.icon, contentDescription = action.label)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

/**
 * Collects undo events and shows a snackbar with an Undo action for each.
 * Shared by the scaffold below and the custom inbox/search shells.
 */
@Composable
fun SelectionUndoSnackbarEffect(
    undoEvents: Flow<SelectionUndoEvent>,
    snackbarHostState: SnackbarHostState,
    onUndo: (SelectionUndoEvent) -> Unit
) {
    LaunchedEffect(undoEvents, snackbarHostState) {
        undoEvents.collect { event ->
            val result: SnackbarResult = snackbarHostState.showSnackbar(
                message = event.describe(),
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onUndo(event)
            }
        }
    }
}

@Composable
fun PagedMessageList(
    messages: LazyPagingItems<Message>,
    listState: LazyListState,
    selectedIds: Set<Long>,
    onClick: (Long) -> Unit,
    onLongClick: (Long) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    loadingText: String = "Loading messages…",
    hiddenTagIds: Set<Long> = emptySet(),
    emptyIcon: ImageVector? = null
) {
    val refreshError: LoadState.Error? = messages.loadState.refresh as? LoadState.Error
    val isRefreshing: Boolean = messages.loadState.refresh is LoadState.Loading
    val isAppending: Boolean = messages.loadState.append is LoadState.Loading
    val reachedEnd: Boolean = messages.loadState.append.endOfPaginationReached
    val isEmpty: Boolean = messages.itemCount == 0 && !isRefreshing && !isAppending && reachedEnd
    val uiState: PagedListUiState = when {
        refreshError != null -> PagedListUiState.ERROR
        isRefreshing || (messages.itemCount == 0 && !reachedEnd) -> PagedListUiState.LOADING
        isEmpty -> PagedListUiState.EMPTY
        else -> PagedListUiState.LIST
    }
    Crossfade(
        targetState = uiState,
        modifier = modifier,
        label = "pagedListState"
    ) { state ->
        when (state) {
            PagedListUiState.ERROR -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Couldn't load messages",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { messages.retry() }) {
                        Text("Retry")
                    }
                }
            }
            PagedListUiState.LOADING -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    Text(
                        text = loadingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PagedListUiState.EMPTY -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (emptyIcon != null) {
                        Icon(
                            imageVector = emptyIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(Spacing.lg))
                    }
                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            PagedListUiState.LIST -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = MessageListDefaults.contentPadding,
                    verticalArrangement = MessageListDefaults.arrangement
                ) {
                    messageItems(
                        items = messages,
                        selectedIds = selectedIds,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        hiddenTagIds = hiddenTagIds
                    )
                }
            }
        }
    }
}

private enum class PagedListUiState {
    ERROR,
    LOADING,
    EMPTY,
    LIST
}

/**
 * Standard scaffold for the back-stack message list screens (all messages, tag,
 * filter, archived, trash). Swaps a title + back top bar for the shared selection
 * bar when items are selected, and bundles the destructive confirmation dialogs,
 * undo snackbar, and suggest-filter snackbar so every list behaves exactly like
 * the inbox. The action set is configurable per screen via [selectionActions].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectableMessageScaffold(
    title: String,
    messages: LazyPagingItems<Message>,
    listState: LazyListState,
    selectedIds: Set<Long>,
    onNavigateBack: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onOpenMessage: (Long) -> Unit,
    onSelectionAction: (SelectionAction) -> Unit,
    onSelectAll: (List<Long>) -> Unit,
    onSuggestFilter: suspend () -> Boolean,
    onNavigateToFilterEdit: () -> Unit,
    undoEvents: Flow<SelectionUndoEvent>,
    onUndo: (SelectionUndoEvent) -> Unit,
    emptyText: String,
    selectionActions: List<SelectionAction> = DefaultSelectionActions,
    topBarActions: @Composable RowScope.() -> Unit = {},
    hiddenTagIds: Set<Long> = emptySet()
) {
    val hasSelection: Boolean = selectedIds.isNotEmpty()
    var showTrashConfirm: Boolean by remember { mutableStateOf(false) }
    var showDeleteForeverConfirm: Boolean by remember { mutableStateOf(false) }
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    SelectionUndoSnackbarEffect(
        undoEvents = undoEvents,
        snackbarHostState = snackbarHostState,
        onUndo = onUndo
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedContent(
                targetState = hasSelection,
                transitionSpec = { NavTransitions.contentSwap() },
                label = "topBarSwap"
            ) { selecting ->
                if (selecting) {
                    MessageSelectionTopBar(
                        count = selectedIds.size,
                        actions = selectionActions,
                        onClose = onClearSelection,
                        onAction = { action ->
                            when (action) {
                                SelectionAction.SELECT_ALL ->
                                    onSelectAll(messages.itemSnapshotList.items.map { it.id })
                                SelectionAction.SUGGEST_FILTER -> coroutineScope.launch {
                                    val generated: Boolean = onSuggestFilter()
                                    if (generated) {
                                        onClearSelection()
                                        onNavigateToFilterEdit()
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            "Couldn't find a common pattern in the selected messages"
                                        )
                                    }
                                }
                                SelectionAction.TRASH -> showTrashConfirm = true
                                SelectionAction.DELETE_FOREVER -> showDeleteForeverConfirm = true
                                else -> onSelectionAction(action)
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text(title) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = topBarActions
                    )
                }
            }
        }
    ) { padding ->
        PagedMessageList(
            messages = messages,
            listState = listState,
            selectedIds = selectedIds,
            onClick = { id ->
                if (hasSelection) onToggleSelection(id) else onOpenMessage(id)
            },
            onLongClick = onToggleSelection,
            emptyText = emptyText,
            modifier = Modifier.padding(padding),
            hiddenTagIds = hiddenTagIds
        )
    }
    if (showTrashConfirm) {
        val count: Int = selectedIds.size
        AlertDialog(
            onDismissRequest = { showTrashConfirm = false },
            title = { Text("Move to Trash?") },
            text = {
                Text(
                    if (count == 1) "1 message will be moved to Trash."
                    else "$count messages will be moved to Trash."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTrashConfirm = false
                        onSelectionAction(SelectionAction.TRASH)
                    }
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrashConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showDeleteForeverConfirm) {
        val count: Int = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteForeverConfirm = false },
            title = { Text("Delete forever?") },
            text = {
                Text(
                    if (count == 1) "1 message will be permanently deleted. This can't be undone."
                    else "$count messages will be permanently deleted. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteForeverConfirm = false
                        onSelectionAction(SelectionAction.DELETE_FOREVER)
                    }
                ) {
                    Text("Delete forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteForeverConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

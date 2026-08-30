package com.strings.app.ui.inbox

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.strings.app.domain.model.Message
import com.strings.app.ui.common.DefaultSelectionActions
import com.strings.app.ui.common.SelectionAction
import com.strings.app.ui.components.MessageSelectionTopBar
import com.strings.app.ui.components.PagedMessageList
import com.strings.app.ui.components.SelectionUndoSnackbarEffect
import com.strings.app.ui.components.tagIconFor
import com.strings.app.ui.theme.NavTransitions
import com.strings.app.ui.theme.Spacing
import com.strings.app.util.DatabaseSeeder
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val MAX_BOTTOM_TABS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToFilters: () -> Unit,
    onNavigateToTags: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToTagMessages: (Long) -> Unit,
    onNavigateToFilterMessages: (Long) -> Unit,
    onNavigateToFilterEdit: () -> Unit,
    onNavigateToAllMessages: () -> Unit,
    onNavigateToArchivedMessages: () -> Unit,
    onNavigateToTrashedMessages: () -> Unit,
    onNavigateToFinanceDashboard: () -> Unit,
    onNavigateToManageAccounts: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: () -> Unit,
    viewModel: InboxViewModel = koinViewModel()
) {
    val seeder: DatabaseSeeder = koinInject()
    LaunchedEffect(Unit) {
        viewModel.initialize(seeder)
    }
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val inboxTagId by viewModel.inboxTagId.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val allFilters by viewModel.allFilters.collectAsStateWithLifecycle()
    val selectedMessageIds by viewModel.selectedMessageIds.collectAsStateWithLifecycle()
    val hasSelection = selectedMessageIds.isNotEmpty()
    var showTrashConfirm by remember { mutableStateOf(false) }
    var currentPageMessages by remember { mutableStateOf<LazyPagingItems<Message>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { tabs.size.coerceAtMost(MAX_BOTTOM_TABS) })
    val listStates: MutableMap<Long, LazyListState> = remember { mutableMapOf() }
    LaunchedEffect(pagerState.currentPage) {
        viewModel.clearSelection()
    }
    SelectionUndoSnackbarEffect(
        undoEvents = viewModel.undoEvents,
        snackbarHostState = snackbarHostState,
        onUndo = { viewModel.undo(it) }
    )
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !hasSelection,
        drawerContent = {
            InboxDrawerContent(
                allTags = allTags,
                allFilters = allFilters,
                activeTagId = tabs.getOrNull(pagerState.currentPage)?.tag?.id,
                onAllMessagesClick = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToAllMessages()
                },
                onArchivedMessagesClick = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToArchivedMessages()
                },
                onTrashedMessagesClick = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToTrashedMessages()
                },
                onTagClick = { tagId ->
                    coroutineScope.launch { drawerState.close() }
                    val visibleIndex = tabs.take(MAX_BOTTOM_TABS).indexOfFirst { it.tag.id == tagId }
                    if (visibleIndex >= 0) {
                        coroutineScope.launch { pagerState.animateScrollToPage(visibleIndex) }
                    } else {
                        onNavigateToTagMessages(tagId)
                    }
                },
                onFilterClick = { filterId ->
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToFilterMessages(filterId)
                },
                onFiltersClick = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToFilters()
                },
                onManageTagsClick = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToTags()
                },
                onFinanceDashboardClick = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToFinanceDashboard()
                },
                onManageAccountsClick = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToManageAccounts()
                },
                onSettingsClick = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToSettings()
                },
                onHelpClick = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToHelp()
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AnimatedContent(
                    targetState = hasSelection,
                    transitionSpec = { NavTransitions.contentSwap() },
                    label = "inboxTopBar"
                ) { selecting ->
                    if (selecting) {
                        MessageSelectionTopBar(
                            count = selectedMessageIds.size,
                            actions = DefaultSelectionActions,
                            onClose = { viewModel.clearSelection() },
                            onAction = { action ->
                                when (action) {
                                    SelectionAction.SELECT_ALL -> viewModel.selectAll(
                                        currentPageMessages?.itemSnapshotList?.items?.map { it.id }
                                            ?: emptyList()
                                    )
                                    SelectionAction.SUGGEST_FILTER -> coroutineScope.launch {
                                        val generated: Boolean = viewModel.suggestFilterFromSelection()
                                        if (generated) {
                                            viewModel.clearSelection()
                                            onNavigateToFilterEdit()
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                "Couldn't find a common pattern in the selected messages"
                                            )
                                        }
                                    }
                                    SelectionAction.TRASH -> showTrashConfirm = true
                                    else -> viewModel.performSelectionAction(action)
                                }
                            }
                        )
                    } else {
                        SearchHeader(
                            onMenuClick = { coroutineScope.launch { drawerState.open() } },
                            onSearchClick = onNavigateToSearch
                        )
                    }
                }
            },
            bottomBar = {
                val visibleTabs = tabs.take(MAX_BOTTOM_TABS)
                if (visibleTabs.isNotEmpty()) {
                    NavigationBar {
                        visibleTabs.forEachIndexed { index, tabWithTag ->
                            NavigationBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    if (pagerState.currentPage == index) {
                                        coroutineScope.launch {
                                            listStates[tabWithTag.tag.id]?.animateScrollToItem(0)
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = tagIconFor(tabWithTag.tag.icon),
                                        contentDescription = tabWithTag.tag.name
                                    )
                                },
                                label = { Text(tabWithTag.tag.name) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (tabs.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                        androidx.compose.foundation.layout.Spacer(
                            modifier = Modifier.padding(top = Spacing.lg)
                        )
                        Text(
                            text = "Loading…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    return@Column
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val tagId = tabs[page].tag.id
                    val messages = remember(tagId) { viewModel.messagesForTag(tagId) }
                        .collectAsLazyPagingItems()
                    val listState = rememberSaveable(tagId, saver = LazyListState.Saver) {
                        LazyListState()
                    }
                    listStates[tagId] = listState
                    if (page == pagerState.currentPage) {
                        SideEffect { currentPageMessages = messages }
                    }
                    PagedMessageList(
                        messages = messages,
                        listState = listState,
                        selectedIds = selectedMessageIds,
                        onClick = { id ->
                            if (hasSelection) {
                                viewModel.toggleMessageSelection(id)
                            } else {
                                onNavigateToDetail(id)
                            }
                        },
                        onLongClick = { id -> viewModel.toggleMessageSelection(id) },
                        emptyText = "No messages in this category",
                        hiddenTagIds = setOfNotNull(tagId, inboxTagId),
                        emptyIcon = tagIconFor(tabs[page].tag.icon)
                    )
                }
            }
        }
        if (showTrashConfirm) {
            val count = selectedMessageIds.size
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
                            viewModel.performSelectionAction(SelectionAction.TRASH)
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
    }
}

@Composable
private fun SearchHeader(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Open menu")
            }
            Surface(
                onClick = onSearchClick,
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Text(
                        text = "Search messages",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


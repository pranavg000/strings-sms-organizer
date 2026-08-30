package com.strings.app.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strings.app.ui.common.DefaultSelectionActions
import com.strings.app.ui.common.SelectionAction
import com.strings.app.ui.components.MessageListDefaults
import com.strings.app.ui.components.MessageSelectionTopBar
import com.strings.app.ui.components.SelectionUndoSnackbarEffect
import com.strings.app.ui.components.messageItems
import com.strings.app.ui.theme.NavTransitions
import com.strings.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMessage: (Long) -> Unit,
    onNavigateToFilterEdit: () -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedMessageIds.collectAsStateWithLifecycle()
    val hasSelection: Boolean = selectedIds.isNotEmpty()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showTrashConfirm: Boolean by remember { mutableStateOf(false) }
    var showDateRangePicker: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    SelectionUndoSnackbarEffect(
        undoEvents = viewModel.undoEvents,
        snackbarHostState = snackbarHostState,
        onUndo = { viewModel.undo(it) }
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedContent(
                targetState = hasSelection,
                transitionSpec = { NavTransitions.contentSwap() },
                label = "searchTopBar"
            ) { selecting ->
                if (selecting) {
                    MessageSelectionTopBar(
                    count = selectedIds.size,
                    actions = DefaultSelectionActions,
                    onClose = { viewModel.clearSelection() },
                    onAction = { action ->
                        when (action) {
                            SelectionAction.SELECT_ALL ->
                                viewModel.selectAll(state.results.map { it.id })
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
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    title = {
                        TextField(
                            value = state.query,
                            onValueChange = { viewModel.updateQuery(it) },
                            placeholder = { Text("Search messages...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchFilterRow(
                state = state,
                onToggleTag = { viewModel.toggleTagFilter(it) },
                onToggleUnread = { viewModel.toggleUnreadOnly() },
                onToggleOtp = { viewModel.toggleOtpOnly() },
                onDateClick = { showDateRangePicker = true },
                onClearFilters = { viewModel.clearFilters() }
            )
            if (state.query.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Search by sender, message content, or keywords",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Spacing.xxl)
                    )
                }
            } else if (state.isSearching) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    Text(
                        text = "Searching…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (state.results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found for \"${state.query}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Spacing.xxl)
                    )
                }
            } else {
                Text(
                    text = "${state.results.size} results",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = MessageListDefaults.contentPadding,
                    verticalArrangement = MessageListDefaults.arrangement
                ) {
                    messageItems(
                        messages = state.results,
                        selectedIds = selectedIds,
                        onClick = { id ->
                            if (hasSelection) viewModel.toggleMessageSelection(id) else onNavigateToMessage(id)
                        },
                        onLongClick = { id -> viewModel.toggleMessageSelection(id) },
                        highlightQuery = state.query
                    )
                }
            }
        }
    }
    if (showDateRangePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        showDateRangePicker = false
                        val startUtc: Long = pickerState.selectedStartDateMillis ?: return@TextButton
                        val endUtc: Long = pickerState.selectedEndDateMillis ?: startUtc
                        viewModel.setDateRange(localDayStart(startUtc) to localDayEnd(endUtc))
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                if (state.dateRangeMillis != null) {
                    TextButton(
                        onClick = {
                            showDateRangePicker = false
                            viewModel.setDateRange(null)
                        }
                    ) {
                        Text("Clear")
                    }
                }
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier.weight(1f)
            )
        }
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

@Composable
private fun SearchFilterRow(
    state: SearchUiState,
    onToggleTag: (Long) -> Unit,
    onToggleUnread: () -> Unit,
    onToggleOtp: () -> Unit,
    onDateClick: () -> Unit,
    onClearFilters: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (state.hasActiveFilters) {
            AssistChip(
                onClick = onClearFilters,
                label = { Text("Clear") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                }
            )
        }
        FilterChip(
            selected = state.unreadOnly,
            onClick = onToggleUnread,
            label = { Text("Unread") }
        )
        FilterChip(
            selected = state.otpOnly,
            onClick = onToggleOtp,
            label = { Text("OTP") }
        )
        FilterChip(
            selected = state.dateRangeMillis != null,
            onClick = onDateClick,
            label = { Text(state.dateRangeMillis?.let { formatDateRange(it) } ?: "Date") },
            leadingIcon = {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        )
        state.allTags.forEach { tag ->
            FilterChip(
                selected = tag.id in state.selectedTagIds,
                onClick = { onToggleTag(tag.id) },
                label = { Text(tag.name) }
            )
        }
    }
}

private val rangeLabelFormat: SimpleDateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

private fun formatDateRange(range: Pair<Long, Long>): String {
    return "${rangeLabelFormat.format(Date(range.first))} – ${rangeLabelFormat.format(Date(range.second))}"
}

private fun localDayStart(utcDateMillis: Long): Long {
    return Instant.ofEpochMilli(utcDateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private fun localDayEnd(utcDateMillis: Long): Long {
    return localDayStart(utcDateMillis) + 86_399_999L
}

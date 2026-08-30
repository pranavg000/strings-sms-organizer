package com.strings.app.ui.detail

import android.content.ClipData
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MarkunreadMailbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.Tag
import com.strings.app.ui.common.messageSharedBounds
import com.strings.app.ui.components.TagChip
import com.strings.app.ui.finance.SetBalanceDialog
import com.strings.app.ui.finance.formatDiscrepancyMessage
import com.strings.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@Composable
fun MessageDetailScreen(
    messageId: Long,
    onNavigateBack: () -> Unit,
    viewModel: MessageDetailViewModel = koinViewModel()
) {
    LaunchedEffect(messageId) {
        viewModel.loadMessage(messageId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showTrashConfirm by remember { mutableStateOf(false) }
    var showDeleteForeverConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showManageTags by remember { mutableStateOf(false) }
    var showSetBalance by remember { mutableStateOf(false) }
    val message = state.message
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.balanceDiscrepancies.collect { discrepancy ->
            snackbarHostState.showSnackbar(formatDiscrepancyMessage(discrepancy))
        }
    }
    Scaffold(
        modifier = Modifier.messageSharedBounds(messageId),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Message") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (message != null) {
                        IconButton(onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("Message", message.body))
                                )
                            }
                            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy message")
                        }
                        when {
                            message.isTrashed -> {
                                IconButton(onClick = {
                                    viewModel.restore()
                                    onNavigateBack()
                                }) {
                                    Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Restore")
                                }
                                IconButton(onClick = { showDeleteForeverConfirm = true }) {
                                    Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete forever")
                                }
                            }
                            message.isArchived -> {
                                IconButton(onClick = { viewModel.unarchive() }) {
                                    Icon(Icons.Outlined.Unarchive, contentDescription = "Unarchive")
                                }
                                IconButton(onClick = { showTrashConfirm = true }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Move to Trash")
                                }
                            }
                            else -> {
                                IconButton(onClick = {
                                    viewModel.archive()
                                    onNavigateBack()
                                }) {
                                    Icon(Icons.Outlined.Archive, contentDescription = "Archive")
                                }
                                IconButton(onClick = { showTrashConfirm = true }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Move to Trash")
                                }
                            }
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (message.isRead) "Mark as unread" else "Mark as read")
                                },
                                trailingIcon = {
                                    Icon(Icons.Default.MarkunreadMailbox, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleRead()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manage tags") },
                                trailingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    showManageTags = true
                                }
                            )
                            if (state.transaction != null) {
                                DropdownMenuItem(
                                    text = { Text("Set balance") },
                                    trailingIcon = {
                                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showSetBalance = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Send test notification") },
                                trailingIcon = {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.sendTestNotification()
                                    Toast.makeText(
                                        context,
                                        "Test notification triggered — check logcat tag StringsNotify",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                CenteredMessage(text = "Loading…", modifier = Modifier.padding(padding))
            }
            message == null -> {
                CenteredMessage(text = "Message not found", modifier = Modifier.padding(padding))
            }
            else -> {
                val assignedTags: List<Tag> = state.allTags.filter { it.id in state.assignedTagIds }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (message.sender != message.senderName) {
                            Text(
                                text = message.sender,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatFullTimestamp(message.timestamp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (assignedTags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            assignedTags.forEach { tag -> TagChip(tag = tag) }
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        val linkStyle = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                        val annotatedBody = remember(message.body) {
                            buildLinkedText(message.body, linkStyle)
                        }
                        Text(
                            text = annotatedBody,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(Spacing.lg)
                        )
                    }
                    if (message.isOtp && message.otpCode != null) {
                        OtpDetailCard(otpCode = message.otpCode)
                    }
                    if (state.transaction != null) {
                        TransactionDetailCard(
                            transaction = state.transaction!!,
                            account = state.account
                        )
                    }
                }
            }
        }
    }
    if (showManageTags && message != null) {
        ManageTagsSheet(
            allTags = state.allTags,
            assignedTagIds = state.assignedTagIds,
            onToggle = { tagId ->
                if (tagId in state.assignedTagIds) viewModel.removeTag(tagId)
                else viewModel.addTag(tagId)
            },
            onDismiss = { showManageTags = false }
        )
    }
    if (showTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showTrashConfirm = false },
            title = { Text("Move to Trash?") },
            text = { Text("This message will be moved to Trash.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTrashConfirm = false
                        viewModel.trash()
                        onNavigateBack()
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
        AlertDialog(
            onDismissRequest = { showDeleteForeverConfirm = false },
            title = { Text("Delete forever?") },
            text = { Text("This message will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteForeverConfirm = false
                        viewModel.deleteForever(onDeleted = onNavigateBack)
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
    if (showSetBalance) {
        SetBalanceDialog(
            currentBalance = state.transaction?.balanceAfter,
            onDismiss = { showSetBalance = false },
            onConfirm = { balance ->
                viewModel.setBalanceAfter(balance)
                showSetBalance = false
            }
        )
    }
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatFullTimestamp(timestamp: Long): String {
    val format = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return format.format(Date(timestamp))
}

private val URL_REGEX: Regex = Patterns.WEB_URL.toRegex()
private val EMAIL_REGEX: Regex = Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
private val PHONE_REGEX: Regex = Regex(
    """(?<!\d)(?:\+91[\s\-]?|91)?[6-9]\d{9}(?!\d)|(?<!\d)1800\d{6,7}(?!\d)"""
)

private data class DetectedLink(val range: IntRange, val text: String, val uri: String)

private fun buildLinkedText(
    text: String,
    linkStyle: SpanStyle
) = buildAnnotatedString {
    val links: List<DetectedLink> = buildList {
        URL_REGEX.findAll(text).forEach { match ->
            val url: String = match.value
            val uri: String = if (url.startsWith("http://") || url.startsWith("https://")) url
                else "https://$url"
            add(DetectedLink(match.range, url, uri))
        }
        EMAIL_REGEX.findAll(text).forEach { match ->
            add(DetectedLink(match.range, match.value, "mailto:${match.value}"))
        }
        PHONE_REGEX.findAll(text).forEach { match ->
            add(DetectedLink(match.range, match.value, toTelUri(match.value)))
        }
    }
        .sortedBy { it.range.first }
        .fold(mutableListOf<DetectedLink>()) { acc, link ->
            if (acc.isEmpty() || link.range.first > acc.last().range.last) acc.add(link)
            acc
        }
    var lastEnd = 0
    links.forEach { link ->
        append(text.substring(lastEnd, link.range.first))
        withLink(
            LinkAnnotation.Url(
                url = link.uri,
                styles = TextLinkStyles(style = linkStyle)
            )
        ) {
            append(link.text)
        }
        lastEnd = link.range.last + 1
    }
    if (lastEnd < text.length) {
        append(text.substring(lastEnd))
    }
}

private fun toTelUri(raw: String): String {
    val digits: String = raw.replace(Regex("[\\s\\-+]"), "")
    if (digits.startsWith("1800")) return "tel:$digits"
    if (digits.startsWith("91") && digits.length == 12) return "tel:+$digits"
    return "tel:+91$digits"
}

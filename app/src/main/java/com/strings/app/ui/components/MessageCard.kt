package com.strings.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.Tag
import com.strings.app.ui.common.messageSharedBounds
import com.strings.app.ui.theme.Spacing
import com.strings.app.util.SystemTags
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageCard(
    message: Message,
    tags: List<Tag>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightQuery: String? = null,
    hiddenTagIds: Set<Long> = emptySet()
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            !message.isRead -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(150),
        label = "cardContainer"
    )
    val titleColor = if (message.isRead) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .messageSharedBounds(message.id)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = "Select message"
                )
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!message.isRead) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                }
                Text(
                    text = buildHighlightedText(
                        text = message.senderName,
                        query = highlightQuery,
                        highlightColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    fontWeight = if (!message.isRead) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val bodyPreview: AnnotatedString = buildBodyPreview(
                body = message.body,
                query = highlightQuery,
                highlightColor = MaterialTheme.colorScheme.tertiaryContainer
            )
            Text(
                text = bodyPreview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
            if (message.isOtp && message.otpCode != null) {
                val clipboard = LocalClipboard.current
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                AssistChip(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("OTP", message.otpCode))
                            )
                        }
                        Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text("Copy OTP ${message.otpCode}") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                        )
                    },
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
            if (tags.isNotEmpty()) {
                val visibleTags: List<Tag> = tags.filter { tag ->
                    tag.id !in hiddenTagIds &&
                        !(message.isOtp && tag.isSystemTag && tag.name == SystemTags.OTP_NAME)
                }
                if (visibleTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(top = Spacing.sm)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        visibleTags.forEach { tag ->
                            TagChip(tag = tag)
                        }
                    }
                }
            }
        }
    }
}

private const val HIGHLIGHT_LEAD_CHARS: Int = 8

private fun buildBodyPreview(
    body: String,
    query: String?,
    highlightColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(body)
    val matchIndex: Int = body.indexOf(query, ignoreCase = true)
    if (matchIndex < 0) return AnnotatedString(body)
    val previewStart: Int = maxOf(0, matchIndex - HIGHLIGHT_LEAD_CHARS)
    val snippet: String = body.substring(previewStart).replace('\n', ' ')
    val prefix: String = if (previewStart > 0) "\u2026" else ""
    val fullText: String = prefix + snippet
    val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
    val matches = pattern.findAll(fullText).toList()
    if (matches.isEmpty()) return AnnotatedString(fullText)
    return buildAnnotatedString {
        var cursor = 0
        for (match in matches) {
            append(fullText.substring(cursor, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = highlightColor)) {
                append(fullText.substring(match.range.first, match.range.last + 1))
            }
            cursor = match.range.last + 1
        }
        if (cursor < fullText.length) {
            append(fullText.substring(cursor))
        }
    }
}

private fun buildHighlightedText(
    text: String,
    query: String?,
    highlightColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)
    val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
    val matches = pattern.findAll(text).toList()
    if (matches.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        for (match in matches) {
            append(text.substring(cursor, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = highlightColor)) {
                append(text.substring(match.range.first, match.range.last + 1))
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDay = 24 * 60 * 60 * 1000L
    val oneYear = 365 * oneDay
    return when {
        diff < 60_000 -> "Now"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < oneDay -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        diff < 7 * oneDay -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        diff < oneYear -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

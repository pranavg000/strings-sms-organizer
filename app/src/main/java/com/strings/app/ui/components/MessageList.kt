package com.strings.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.strings.app.domain.model.Message
import com.strings.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MessageListDefaults {
    val contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm)
    val arrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.sm)
}

private val monthYearFormat: SimpleDateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
private val monthOnlyFormat: SimpleDateFormat = SimpleDateFormat("MMMM", Locale.getDefault())
private const val DAY_MS: Long = 86_400_000L

private fun startOfDay(timestamp: Long): Long {
    val cal: Calendar = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/**
 * Section label for a message list: recent buckets (Today / Yesterday /
 * This week) then month granularity, with the year added once the month
 * is from a previous year.
 */
fun dateSectionLabel(timestamp: Long): String {
    val daysAgo: Long = (startOfDay(System.currentTimeMillis()) - startOfDay(timestamp)) / DAY_MS
    return when {
        daysAgo <= 0L -> "Today"
        daysAgo == 1L -> "Yesterday"
        daysAgo < 7L -> "This week"
        else -> {
            val date: Calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
            val now: Calendar = Calendar.getInstance()
            if (date.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                monthOnlyFormat.format(Date(timestamp))
            } else {
                monthYearFormat.format(Date(timestamp))
            }
        }
    }
}

@Composable
fun DateSeparator(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    )
}

fun LazyListScope.messageItems(
    items: LazyPagingItems<Message>,
    selectedIds: Set<Long>,
    onClick: (Long) -> Unit,
    onLongClick: (Long) -> Unit,
    hiddenTagIds: Set<Long> = emptySet()
) {
    items(
        count = items.itemCount,
        key = items.itemKey { it.id }
    ) { index ->
        val message: Message = items[index] ?: return@items
        val previous: Message? = if (index > 0) items.peek(index - 1) else null
        if (previous == null || dateSectionLabel(previous.timestamp) != dateSectionLabel(message.timestamp)) {
            DateSeparator(dateSectionLabel(message.timestamp))
        }
        MessageCard(
            message = message,
            tags = message.tags,
            isSelected = message.id in selectedIds,
            onClick = { onClick(message.id) },
            onLongClick = { onLongClick(message.id) },
            modifier = Modifier.animateItem(fadeInSpec = null),
            hiddenTagIds = hiddenTagIds
        )
    }
}

fun LazyListScope.messageItems(
    messages: List<Message>,
    selectedIds: Set<Long> = emptySet(),
    onClick: (Long) -> Unit,
    onLongClick: (Long) -> Unit = {},
    highlightQuery: String? = null,
    hiddenTagIds: Set<Long> = emptySet()
) {
    itemsIndexed(
        items = messages,
        key = { _, message -> message.id }
    ) { index, message ->
        val previous: Message? = messages.getOrNull(index - 1)
        if (previous == null || dateSectionLabel(previous.timestamp) != dateSectionLabel(message.timestamp)) {
            DateSeparator(dateSectionLabel(message.timestamp))
        }
        MessageCard(
            message = message,
            tags = message.tags,
            isSelected = message.id in selectedIds,
            onClick = { onClick(message.id) },
            onLongClick = { onLongClick(message.id) },
            modifier = Modifier.animateItem(fadeInSpec = null),
            highlightQuery = highlightQuery,
            hiddenTagIds = hiddenTagIds
        )
    }
}

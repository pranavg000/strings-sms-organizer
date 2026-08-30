package com.strings.app.ui.tags

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strings.app.domain.model.Tag
import com.strings.app.ui.components.InfoTooltipIcon
import com.strings.app.ui.components.tagIconFor
import com.strings.app.ui.help.HelpTexts
import com.strings.app.ui.theme.Spacing
import com.strings.app.ui.theme.rememberTagColors
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToCreate: () -> Unit,
    viewModel: TagViewModel = koinViewModel()
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Manage tags") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    InfoTooltipIcon(
                        text = HelpTexts.TAG_LIST,
                        title = "How tags work"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add tag")
            }
        }
    ) { padding ->
        if (state.tags.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tags yet.\nTap + to create one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val topLevel = state.tags.filter { it.parentTagId == null }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(items = topLevel, key = { it.id }) { tag ->
                    TagItem(
                        tag = tag,
                        isTab = tag.id in state.tabTagIds,
                        count = state.tagCounts[tag.id] ?: 0,
                        indent = 0,
                        onClick = { onNavigateToEdit(tag.id) },
                        modifier = Modifier.animateItem()
                    )
                    val children = state.tags.filter { it.parentTagId == tag.id }
                    children.forEach { child ->
                        Spacer(modifier = Modifier.size(Spacing.sm))
                        TagItem(
                            tag = child,
                            isTab = child.id in state.tabTagIds,
                            count = state.tagCounts[child.id] ?: 0,
                            indent = 1,
                            onClick = { onNavigateToEdit(child.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagItem(
    tag: Tag,
    isTab: Boolean,
    count: Int,
    indent: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = rememberTagColors(tag)
    val railColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    Box(modifier = modifier.fillMaxWidth()) {
        if (indent > 0) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 12.dp)
            ) {
                val railX = 0f
                drawLine(
                    color = railColor,
                    start = Offset(railX, 0f),
                    end = Offset(railX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
                val tickY = size.height / 2
                drawLine(
                    color = railColor,
                    start = Offset(railX, tickY),
                    end = Offset(railX + 8.dp.toPx(), tickY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (indent * 24).dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.container),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tagIconFor(tag.icon),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.lg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = tag.name, style = MaterialTheme.typography.titleMedium)
                    if (tag.isSystemTag) {
                        Text(
                            text = "System tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isTab) {
                    Icon(
                        Icons.Default.Tab,
                        contentDescription = "Shown as tab",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                }
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent
                )
            }
        }
    }
}

package com.strings.app.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strings.app.ui.components.InfoTooltipIcon
import com.strings.app.ui.components.TagChip
import com.strings.app.ui.components.TagIcons
import com.strings.app.ui.help.HelpTexts
import com.strings.app.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagEditScreen(
    tagId: Long,
    onNavigateBack: () -> Unit,
    viewModel: TagViewModel = koinViewModel()
) {
    LaunchedEffect(tagId) {
        viewModel.loadTagForEdit(tagId)
    }
    val state by viewModel.editState.collectAsStateWithLifecycle()
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val alreadyTab = state.tag.id in listState.tabTagIds
    val canEnableTab = alreadyTab || listState.tabTagIds.size < MAX_VISIBLE_TABS
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New tag" else "Edit tag") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew && !state.tag.isSystemTag) {
                        IconButton(onClick = { viewModel.requestDeleteTag() }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete tag"
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.saveTag(onNavigateBack) },
                        enabled = state.tag.name.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            OutlinedTextField(
                value = state.tag.name,
                onValueChange = { viewModel.updateTagName(it) },
                label = { Text("Tag Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text("Preview", style = MaterialTheme.typography.titleSmall)
            TagChip(tag = state.tag)
            Text("Icon", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                TagIcons.forEach { (key, vector) ->
                    val selected = key == state.tag.icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else Modifier
                            )
                            .clickable { viewModel.updateTagIcon(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = vector,
                            contentDescription = key,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    ParentTagSelector(
                        selectedParentId = state.tag.parentTagId,
                        availableParents = state.availableParents,
                        onSelect = { viewModel.updateParentTag(it) }
                    )
                }
                InfoTooltipIcon(text = HelpTexts.TAG_PARENT)
            }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show as Tab", style = MaterialTheme.typography.bodyLarge)
                        InfoTooltipIcon(text = HelpTexts.TAG_SHOW_AS_TAB)
                    }
                    Switch(
                        checked = state.isTab,
                        onCheckedChange = { viewModel.toggleIsTab(it) },
                        enabled = canEnableTab || state.isTab,
                        thumbContent = if (state.isTab) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
                if (!canEnableTab && !state.isTab) {
                    Text(
                        text = "Tab limit reached ($MAX_VISIBLE_TABS). Remove a tab to add another.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }
        }
    }
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialogs() },
            title = { Text("Delete tag?") },
            text = {
                Text(
                    "\"${state.tag.name}\" will be removed from all messages that have it. " +
                        "This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteTag(onNavigateBack) }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialogs() }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (state.deleteBlockedByFilters.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialogs() },
            title = { Text("Can't delete tag") },
            text = {
                Text(
                    "This tag is used by ${state.deleteBlockedByFilters.size} filter(s): " +
                        "${state.deleteBlockedByFilters.joinToString(", ")}. " +
                        "Remove it from those filters before deleting."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialogs() }) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentTagSelector(
    selectedParentId: Long?,
    availableParents: List<com.strings.app.domain.model.Tag>,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = availableParents.find { it.id == selectedParentId }?.name ?: "None (top-level)"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Parent Tag") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val options: List<Pair<Long?, String>> =
                listOf(null to "None (top-level)") + availableParents.map { it.id to it.name }
            options.forEach { (id, name) ->
                val isSelected: Boolean = id == selectedParentId
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                    modifier = if (isSelected) {
                        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

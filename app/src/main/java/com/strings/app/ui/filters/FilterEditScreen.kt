package com.strings.app.ui.filters

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strings.app.ui.components.InfoTooltipIcon
import com.strings.app.ui.components.SectionHeaderWithInfo
import com.strings.app.ui.help.HelpTexts
import com.strings.app.ui.theme.Spacing
import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.ConditionField
import com.strings.app.domain.model.ConditionGroup
import com.strings.app.domain.model.ConditionLeaf
import com.strings.app.domain.model.ConditionNode
import com.strings.app.domain.model.ConditionOperator
import com.strings.app.domain.model.LogicGroup
import com.strings.app.domain.model.addChild
import com.strings.app.domain.model.hasLeaf
import com.strings.app.domain.model.removeChildAt
import com.strings.app.domain.model.replaceChildAt
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterEditScreen(
    filterId: Long,
    onNavigateBack: () -> Unit,
    viewModel: FilterViewModel = koinViewModel()
) {
    LaunchedEffect(filterId) {
        viewModel.loadFilterForEdit(filterId)
    }
    val state by viewModel.editState.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val isSuggesting by viewModel.isSuggesting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCreateTag by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New filter" else "Edit filter") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(
                            onClick = { viewModel.suggestBetterFilter() },
                            enabled = !isSuggesting && state.filter.root.hasLeaf()
                        ) {
                            if (isSuggesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Suggest filter"
                                )
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete"
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.saveFilter { appliedInBackground ->
                                if (appliedInBackground) {
                                    Toast.makeText(
                                        context,
                                        "Applying filter to existing messages\u2026",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                onNavigateBack()
                            }
                        },
                        enabled = state.filter.name.isNotBlank() && state.filter.root.hasLeaf() && !state.isSaving
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
                value = state.filter.name,
                onValueChange = { viewModel.updateFilterName(it) },
                label = { Text("Filter Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                    InfoTooltipIcon(text = HelpTexts.FILTER_ENABLED)
                }
                Switch(
                    checked = state.filter.isEnabled,
                    onCheckedChange = { viewModel.updateFilterIsEnabled(it) },
                    thumbContent = if (state.filter.isEnabled) {
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
            SectionHeaderWithInfo(
                title = "Conditions",
                tooltipText = HelpTexts.FILTER_CONDITIONS
            )
            GroupEditor(
                group = state.filter.root,
                onChange = { viewModel.updateRoot(it) },
                onRemove = null,
                depth = 0
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            SectionHeaderWithInfo(
                title = "Actions",
                tooltipText = HelpTexts.FILTER_ACTIONS
            )
            ActionsSection(
                selectedActions = state.filter.actions.map { it.actionType }.toSet(),
                onToggleAction = { viewModel.toggleAction(it) }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                SectionHeaderWithInfo(
                    title = "Assign Tags",
                    tooltipText = HelpTexts.FILTER_ASSIGN_TAGS,
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(onClick = { showCreateTag = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create tag")
                }
            }
            if (state.availableTags.isNotEmpty()) {
                TagActionSelector(
                    tags = state.availableTags,
                    selectedTagIds = state.filter.actions
                        .filter { it.actionType == ActionType.ASSIGN_TAG }
                        .mapNotNull { it.targetTagId }
                        .toSet(),
                    onToggleTag = { tagId ->
                        viewModel.toggleAction(ActionType.ASSIGN_TAG, tagId)
                    }
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Apply to existing messages", style = MaterialTheme.typography.bodyLarge)
                    InfoTooltipIcon(text = HelpTexts.FILTER_APPLY_EXISTING)
                }
                Switch(
                    checked = state.applyToExisting,
                    onCheckedChange = { viewModel.toggleApplyToExisting() },
                    thumbContent = if (state.applyToExisting) {
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
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete filter?") },
            text = {
                Text(
                    "\"${state.filter.name}\" will be permanently deleted. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteFilter(state.filter.id)
                        onNavigateBack()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showCreateTag) {
        CreateTagDialog(
            onDismiss = { showCreateTag = false },
            onCreate = { name ->
                showCreateTag = false
                viewModel.createTagInline(name)
            }
        )
    }
    val suggestedRoot: ConditionGroup? = suggestion
    if (suggestedRoot != null) {
        SuggestionComparisonDialog(
            currentRoot = state.filter.root,
            suggestedRoot = suggestedRoot,
            onAccept = { viewModel.applySuggestion() },
            onDismiss = { viewModel.dismissSuggestion() }
        )
    }
}

@Composable
private fun CreateTagDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var tagName: String by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tag") },
        text = {
            OutlinedTextField(
                value = tagName,
                onValueChange = { tagName = it },
                label = { Text("Tag name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(tagName.trim()) },
                enabled = tagName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupEditor(
    group: ConditionGroup,
    onChange: (ConditionGroup) -> Unit,
    onRemove: (() -> Unit)?,
    depth: Int
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text("Match", style = MaterialTheme.typography.labelLarge)
                    val logicOptions: List<Pair<LogicGroup, String>> = listOf(
                        LogicGroup.AND to "ALL",
                        LogicGroup.OR to "ANY"
                    )
                    SingleChoiceSegmentedButtonRow {
                        logicOptions.forEachIndexed { index, (logic, label) ->
                            SegmentedButton(
                                selected = group.logic == logic,
                                onClick = { onChange(group.copy(logic = logic)) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = logicOptions.size
                                ),
                                label = { Text(label) }
                            )
                        }
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Remove group")
                    }
                }
            }
            group.children.forEachIndexed { index, child ->
                if (index > 0) {
                    Text(
                        text = group.logic.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.xs)
                    )
                }
                when (child) {
                    is ConditionLeaf -> LeafEditor(
                        leaf = child,
                        onChange = { onChange(group.replaceChildAt(index, it)) },
                        onRemove = { onChange(group.removeChildAt(index)) }
                    )
                    is ConditionGroup -> GroupEditor(
                        group = child,
                        onChange = { onChange(group.replaceChildAt(index, it)) },
                        onRemove = { onChange(group.removeChildAt(index)) },
                        depth = depth + 1
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(
                    onClick = {
                        onChange(
                            group.addChild(
                                ConditionLeaf(
                                    field = ConditionField.BODY,
                                    operator = ConditionOperator.CONTAINS,
                                    value = ""
                                )
                            )
                        )
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Add condition")
                }
                TextButton(onClick = { onChange(group.addChild(ConditionGroup())) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Add group")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeafEditor(
    leaf: ConditionLeaf,
    onChange: (ConditionLeaf) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DropdownSelector(
                    label = "Field",
                    selected = leaf.field.name,
                    options = ConditionField.entries.map { it.name },
                    onSelect = { onChange(leaf.copy(field = ConditionField.valueOf(it))) },
                    modifier = Modifier.weight(1f)
                )
                DropdownSelector(
                    label = "Operator",
                    selected = leaf.operator.name,
                    options = ConditionOperator.entries.map { it.name },
                    onSelect = { onChange(leaf.copy(operator = ConditionOperator.valueOf(it))) },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove condition")
                }
            }
            OutlinedTextField(
                value = leaf.value,
                onValueChange = { onChange(leaf.copy(value = it)) },
                label = { Text("Value") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.lowercase().replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val isSelected: Boolean = option == selected
                DropdownMenuItem(
                    text = { Text(option.lowercase().replace('_', ' ')) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(option)
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

@Composable
private fun ActionsSection(
    selectedActions: Set<ActionType>,
    onToggleAction: (ActionType) -> Unit
) {
    val nonTagActions = ActionType.entries.filter { it != ActionType.ASSIGN_TAG }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        nonTagActions.forEach { actionType ->
            FilterChip(
                selected = actionType in selectedActions,
                onClick = { onToggleAction(actionType) },
                label = { Text(actionType.name.lowercase().replace('_', ' ')) }
            )
        }
    }
}

@Composable
private fun TagActionSelector(
    tags: List<com.strings.app.domain.model.Tag>,
    selectedTagIds: Set<Long>,
    onToggleTag: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        tags.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { tag ->
                    FilterChip(
                        selected = tag.id in selectedTagIds,
                        onClick = { onToggleTag(tag.id) },
                        label = { Text(tag.name) }
                    )
                }
            }
        }
    }
}

private data class DiffLine(val depth: Int, val text: String)

private enum class DiffStatus { UNCHANGED, ADDED, REMOVED }

private fun describeTree(node: ConditionNode, depth: Int = 0): List<DiffLine> {
    return when (node) {
        is ConditionLeaf -> {
            val op: String = node.operator.name.lowercase().replace('_', ' ')
            listOf(DiffLine(depth, "${node.field.name.lowercase()} $op \"${node.value}\""))
        }
        is ConditionGroup -> {
            val header = if (node.logic == LogicGroup.AND) "ALL of:" else "ANY of:"
            listOf(DiffLine(depth, header)) +
                node.children.flatMap { describeTree(it, depth + 1) }
        }
    }
}

private fun diffLines(
    currentLines: List<DiffLine>,
    suggestedLines: List<DiffLine>
): List<Pair<DiffLine, DiffStatus>> {
    val currentBag: MutableMap<String, Int> = mutableMapOf()
    for (line in currentLines) {
        val key = "${"  ".repeat(line.depth)}${line.text}"
        currentBag[key] = (currentBag[key] ?: 0) + 1
    }
    val suggestedBag: MutableMap<String, Int> = mutableMapOf()
    for (line in suggestedLines) {
        val key = "${"  ".repeat(line.depth)}${line.text}"
        suggestedBag[key] = (suggestedBag[key] ?: 0) + 1
    }
    val result: MutableList<Pair<DiffLine, DiffStatus>> = mutableListOf()
    val usedCurrent: MutableMap<String, Int> = mutableMapOf()
    for (line in currentLines) {
        val key = "${"  ".repeat(line.depth)}${line.text}"
        val usedCount: Int = usedCurrent[key] ?: 0
        val inSuggested: Int = suggestedBag[key] ?: 0
        if (usedCount < inSuggested) {
            result.add(line to DiffStatus.UNCHANGED)
        } else {
            result.add(line to DiffStatus.REMOVED)
        }
        usedCurrent[key] = usedCount + 1
    }
    val usedSuggested: MutableMap<String, Int> = mutableMapOf()
    for (line in suggestedLines) {
        val key = "${"  ".repeat(line.depth)}${line.text}"
        val usedCount: Int = usedSuggested[key] ?: 0
        val inCurrent: Int = currentBag[key] ?: 0
        if (usedCount >= inCurrent) {
            result.add(line to DiffStatus.ADDED)
        }
        usedSuggested[key] = usedCount + 1
    }
    return result
}

@Composable
private fun SuggestionComparisonDialog(
    currentRoot: ConditionGroup,
    suggestedRoot: ConditionGroup,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentLines: List<DiffLine> = remember(currentRoot) { describeTree(currentRoot) }
    val suggestedLines: List<DiffLine> = remember(suggestedRoot) { describeTree(suggestedRoot) }
    val diff: List<Pair<DiffLine, DiffStatus>> = remember(currentLines, suggestedLines) {
        diffLines(currentLines, suggestedLines)
    }
    val hasChanges: Boolean = diff.any { it.second != DiffStatus.UNCHANGED }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasChanges) "Suggested conditions" else "No improvements found") },
        text = {
            if (hasChanges) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    for ((line, status) in diff) {
                        val bgColor = when (status) {
                            DiffStatus.ADDED -> MaterialTheme.colorScheme.tertiaryContainer
                            DiffStatus.REMOVED -> MaterialTheme.colorScheme.errorContainer
                            DiffStatus.UNCHANGED -> MaterialTheme.colorScheme.surface
                        }
                        val textColor = when (status) {
                            DiffStatus.ADDED -> MaterialTheme.colorScheme.onTertiaryContainer
                            DiffStatus.REMOVED -> MaterialTheme.colorScheme.onErrorContainer
                            DiffStatus.UNCHANGED -> MaterialTheme.colorScheme.onSurface
                        }
                        val decoration = if (status == DiffStatus.REMOVED) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        }
                        val prefix = when (status) {
                            DiffStatus.ADDED -> "+ "
                            DiffStatus.REMOVED -> "- "
                            DiffStatus.UNCHANGED -> "  "
                        }
                        Text(
                            text = "$prefix${"  ".repeat(line.depth)}${line.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            textDecoration = decoration,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(bgColor)
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                }
            } else {
                Text(
                    "The current conditions are already well-structured for the matching messages.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic
                )
            }
        },
        confirmButton = {
            if (hasChanges) {
                TextButton(onClick = onAccept) {
                    Text("Apply")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            if (hasChanges) {
                TextButton(onClick = onDismiss) {
                    Text("Keep current")
                }
            }
        }
    )
}

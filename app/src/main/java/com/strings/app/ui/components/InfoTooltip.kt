package com.strings.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A small info icon that reveals an M3 rich tooltip when tapped. Used across
 * screens to explain non-obvious concepts (filters, tags, tabs) to new users.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTooltipIcon(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val tooltipState: TooltipState = rememberTooltipState(isPersistent = true)
    val scope: CoroutineScope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            RichTooltip(
                title = if (title != null) {
                    { Text(title) }
                } else {
                    null
                }
            ) {
                Text(text)
            }
        },
        state = tooltipState,
        modifier = modifier
    ) {
        IconButton(onClick = { scope.launch { tooltipState.show() } }) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Help",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * A section header (plain text, per existing screens) with a trailing info
 * tooltip icon explaining the section.
 */
@Composable
fun SectionHeaderWithInfo(
    title: String,
    tooltipText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    tooltipTitle: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(title, style = style)
        InfoTooltipIcon(text = tooltipText, title = tooltipTitle)
    }
}

package com.strings.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.strings.app.domain.model.Tag
import com.strings.app.ui.theme.Spacing
import com.strings.app.ui.theme.rememberTagColors

@Composable
fun TagChip(
    tag: Tag,
    modifier: Modifier = Modifier
) {
    val colors = rememberTagColors(tag)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Spacing.sm))
            .background(colors.container)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(
            imageVector = tagIconFor(tag.icon),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent
        )
    }
}

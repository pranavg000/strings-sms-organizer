package com.strings.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Contextual actions available in the message multi-select top bar. Each screen
 * passes the subset that makes sense for its list (e.g. Trash offers Restore +
 * Delete forever instead of Archive + Move to Trash).
 */
enum class SelectionAction(val icon: ImageVector, val label: String) {
    SELECT_ALL(Icons.Default.SelectAll, "Select all"),
    SUGGEST_FILTER(Icons.Default.AutoAwesome, "Suggest filter"),
    ARCHIVE(Icons.Default.Archive, "Archive"),
    UNARCHIVE(Icons.Default.Unarchive, "Unarchive"),
    TRASH(Icons.Default.Delete, "Move to Trash"),
    RESTORE(Icons.Default.RestoreFromTrash, "Restore"),
    DELETE_FOREVER(Icons.Default.DeleteForever, "Delete forever")
}

val DefaultSelectionActions: List<SelectionAction> = listOf(
    SelectionAction.SELECT_ALL,
    SelectionAction.SUGGEST_FILTER,
    SelectionAction.ARCHIVE,
    SelectionAction.TRASH
)

/** Reversible selection mutations that can be undone from a snackbar. */
enum class UndoableAction {
    ARCHIVE,
    UNARCHIVE,
    TRASH,
    RESTORE
}

data class SelectionUndoEvent(
    val action: UndoableAction,
    val messageIds: List<Long>
) {
    fun describe(): String {
        val subject: String =
            if (messageIds.size == 1) "1 message" else "${messageIds.size} messages"
        return when (action) {
            UndoableAction.ARCHIVE -> "$subject archived"
            UndoableAction.UNARCHIVE -> "$subject unarchived"
            UndoableAction.TRASH -> "$subject moved to Trash"
            UndoableAction.RESTORE -> "$subject restored"
        }
    }
}

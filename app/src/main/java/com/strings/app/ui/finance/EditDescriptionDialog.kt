package com.strings.app.ui.finance

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * Shared editor for a message's free-text description. Used by the message detail screen
 * and the finance ledgers (where it edits the transaction's message). Saving an empty
 * field clears the description.
 */
@Composable
fun EditDescriptionDialog(
    currentDescription: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var text: String by remember { mutableStateOf(currentDescription.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentDescription == null) "Add description" else "Edit description") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Description") },
                placeholder = { Text("What was this for?") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                maxLines = 5
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim().takeIf { it.isNotEmpty() }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

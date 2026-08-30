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
import androidx.compose.ui.text.input.KeyboardType

/**
 * Shared dialog for editing a transaction's balance-after value. Used by the
 * finance dashboard, account detail, and message detail screens.
 */
@Composable
fun SetBalanceDialog(
    currentBalance: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit
) {
    var text: String by remember {
        mutableStateOf(
            currentBalance?.let { balance: Double ->
                if (balance % 1.0 == 0.0) balance.toLong().toString() else balance.toString()
            } ?: ""
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set balance after transaction") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Balance (INR)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toDoubleOrNull()) }) {
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

package com.strings.app.ui.finance

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.usecase.LinkTransactionResult
import com.strings.app.ui.search.SearchScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Message picker for attaching a sentinel (or re-pointing a linked transaction) to a message.
 * Reuses the search screen in pick mode; choosing a message links it and returns.
 */
@Composable
fun LinkTransactionScreen(
    transactionId: Long,
    onNavigateBack: () -> Unit,
    viewModel: LinkTransactionViewModel = koinViewModel { parametersOf(transactionId) }
) {
    val transaction: Transaction? by viewModel.transaction.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prompt: String = transaction?.let { txn ->
        val verb: String = if (txn.isLinked) "Relink" else "Link"
        "$verb ${formatSignedAmount(txn.amount, txn.type)} to a message"
    } ?: "Link this transaction to a message"
    SearchScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToMessage = {},
        onNavigateToFilterEdit = {},
        pickPrompt = prompt,
        onPickMessage = { messageId ->
            viewModel.link(messageId) { result ->
                val feedback: String = when (result) {
                    LinkTransactionResult.LINKED -> "Transaction linked"
                    LinkTransactionResult.TRANSACTION_NOT_FOUND -> "This transaction no longer exists"
                    LinkTransactionResult.MESSAGE_NOT_FOUND -> "That message no longer exists"
                    LinkTransactionResult.NOT_LINKABLE -> "Detected transactions can't be relinked"
                }
                Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
                onNavigateBack()
            }
        }
    )
}

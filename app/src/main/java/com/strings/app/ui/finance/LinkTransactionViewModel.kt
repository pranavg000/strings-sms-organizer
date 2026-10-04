package com.strings.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.usecase.LinkTransactionResult
import com.strings.app.domain.usecase.LinkTransactionToMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the "link to message" picker: exposes the transaction being linked (for the prompt)
 * and performs the link once the user taps a message.
 */
class LinkTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val linkTransaction: LinkTransactionToMessageUseCase,
    private val transactionId: Long
) : ViewModel() {
    private val _transaction: MutableStateFlow<Transaction?> = MutableStateFlow(null)
    val transaction: StateFlow<Transaction?> = _transaction.asStateFlow()

    init {
        viewModelScope.launch {
            _transaction.value = transactionRepository.getTransactionById(transactionId)
        }
    }

    fun link(messageId: Long, onResult: (LinkTransactionResult) -> Unit) {
        viewModelScope.launch {
            val result: LinkTransactionResult = linkTransaction.link(transactionId, messageId)
            onResult(result)
        }
    }
}

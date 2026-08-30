package com.strings.app.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.Tag
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.transaction.BalanceDiscrepancy
import com.strings.app.domain.transaction.ParsedTransaction
import com.strings.app.domain.transaction.TransactionParser
import com.strings.app.domain.usecase.CheckBalanceDiscrepancyUseCase
import com.strings.app.notification.SmsNotifier
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MessageDetailUiState(
    val message: Message? = null,
    val allTags: List<Tag> = emptyList(),
    val assignedTagIds: Set<Long> = emptySet(),
    val transaction: Transaction? = null,
    val account: Account? = null,
    val isLoading: Boolean = true
)

class MessageDetailViewModel(
    private val messageRepository: MessageRepository,
    private val tagRepository: TagRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionParser: TransactionParser,
    private val notifier: SmsNotifier,
    private val checkBalanceDiscrepancy: CheckBalanceDiscrepancyUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessageDetailUiState())
    val uiState: StateFlow<MessageDetailUiState> = _uiState.asStateFlow()

    private val _balanceDiscrepancies = MutableSharedFlow<BalanceDiscrepancy>(extraBufferCapacity = 1)
    val balanceDiscrepancies: SharedFlow<BalanceDiscrepancy> = _balanceDiscrepancies.asSharedFlow()

    fun loadMessage(messageId: Long) {
        viewModelScope.launch {
            val messageDeferred = async { messageRepository.getMessageById(messageId) }
            val allTagsDeferred = async { tagRepository.getAllTagsList() }
            val transactionDeferred = async { transactionRepository.getTransactionForMessage(messageId) }
            val message: Message? = messageDeferred.await()
            val allTags: List<Tag> = allTagsDeferred.await()
            val transaction: Transaction? = transactionDeferred.await()
            val assignedTagIds: Set<Long> = message?.tags?.map { it.id }?.toSet() ?: emptySet()
            val account: Account? = transaction?.let { transactionRepository.getAccountById(it.accountId) }
            _uiState.value = MessageDetailUiState(
                message = message,
                allTags = allTags,
                assignedTagIds = assignedTagIds,
                transaction = transaction,
                account = account,
                isLoading = false
            )
            if (message != null && !message.isRead) {
                messageRepository.setRead(messageId, true)
            }
        }
    }

    fun addTag(tagId: Long) {
        val message = _uiState.value.message ?: return
        viewModelScope.launch {
            messageRepository.addTagToMessage(message.id, tagId)
            _uiState.value = _uiState.value.copy(
                assignedTagIds = _uiState.value.assignedTagIds + tagId
            )
        }
    }

    fun removeTag(tagId: Long) {
        val message = _uiState.value.message ?: return
        viewModelScope.launch {
            messageRepository.removeTagFromMessage(message.id, tagId)
            _uiState.value = _uiState.value.copy(
                assignedTagIds = _uiState.value.assignedTagIds - tagId
            )
        }
    }

    fun archive() {
        val message = _uiState.value.message ?: return
        viewModelScope.launch {
            messageRepository.setArchived(message.id, true)
            _uiState.value = _uiState.value.copy(
                message = message.copy(isArchived = true)
            )
        }
    }

    fun trash() {
        val message = _uiState.value.message ?: return
        viewModelScope.launch {
            messageRepository.setTrashed(message.id, true)
            _uiState.value = _uiState.value.copy(
                message = message.copy(isTrashed = true)
            )
        }
    }

    fun unarchive() {
        val message = _uiState.value.message ?: return
        viewModelScope.launch {
            messageRepository.setArchived(message.id, false)
            _uiState.value = _uiState.value.copy(
                message = message.copy(isArchived = false)
            )
        }
    }

    fun restore() {
        val message = _uiState.value.message ?: return
        viewModelScope.launch {
            messageRepository.setTrashed(message.id, false)
            _uiState.value = _uiState.value.copy(
                message = message.copy(isTrashed = false)
            )
        }
    }

    fun deleteForever(onDeleted: () -> Unit) {
        val message = _uiState.value.message ?: return
        viewModelScope.launch {
            messageRepository.deleteMessages(listOf(message.id))
            onDeleted()
        }
    }

    fun toggleRead() {
        val message = _uiState.value.message ?: return
        viewModelScope.launch {
            val newReadState = !message.isRead
            messageRepository.setRead(message.id, newReadState)
            _uiState.value = _uiState.value.copy(
                message = message.copy(isRead = newReadState)
            )
        }
    }

    /**
     * Debug helper: re-runs the live pipeline's notification decision for this
     * message and posts the result, logging every step so the outcome can be
     * traced in logcat (tag "StringsNotify").
     */
    fun sendTestNotification() {
        val message: Message = _uiState.value.message ?: return
        viewModelScope.launch {
            val parsed: ParsedTransaction? = try {
                transactionParser.parseTransaction(
                    message.body, message.sender, transactionRepository.getAllAccountsOnce()
                )
            } catch (e: Exception) {
                Log.e(TAG, "test-notify: parser threw for message ${message.id}", e)
                null
            }
            Log.d(
                TAG,
                "test-notify: id=${message.id} sender=${message.sender} " +
                    "isOtp=${message.isOtp} parsed=${parsed != null} " +
                    (parsed?.let { "amount=${it.amount} type=${it.type} source=${it.account.bankName}" } ?: "")
            )
            try {
                when {
                    message.isOtp && message.otpCode != null -> notifier.notifyOtp(message)
                    parsed != null -> notifier.notifyTransaction(message, parsed)
                    else -> notifier.notifyNewMessage(message)
                }
                Log.d(TAG, "test-notify: notifier call returned without error")
            } catch (e: Exception) {
                Log.e(TAG, "test-notify: notifier threw", e)
            }
        }
    }

    fun setBalanceAfter(balance: Double?) {
        val transaction = _uiState.value.transaction ?: return
        viewModelScope.launch {
            transactionRepository.updateBalanceAfter(transaction.id, balance)
            _uiState.value = _uiState.value.copy(
                transaction = transaction.copy(balanceAfter = balance)
            )
            if (balance != null) {
                checkBalanceDiscrepancy.forTransaction(transaction.id)?.let { discrepancy ->
                    _balanceDiscrepancies.tryEmit(discrepancy)
                }
            }
        }
    }

    companion object {
        private const val TAG: String = "StringsNotify"
    }
}

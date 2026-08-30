package com.strings.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strings.app.domain.filter.FilterDraftHolder
import com.strings.app.domain.filter.FilterSuggester
import com.strings.app.domain.filter.SuggestedFilter
import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel holding the multiselect state and the contextual actions
 * (archive, trash, restore, delete forever, suggest filter) shared by every
 * message list screen (inbox, all messages, tag, filter, search, archived,
 * trash). Subclasses provide the list data.
 */
abstract class SelectableMessagesViewModel(
    private val messageRepository: MessageRepository,
    private val filterSuggester: FilterSuggester,
    private val filterDraftHolder: FilterDraftHolder
) : ViewModel() {
    private val _selectedMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMessageIds: StateFlow<Set<Long>> = _selectedMessageIds.asStateFlow()
    private val _undoEvents = MutableSharedFlow<SelectionUndoEvent>(extraBufferCapacity = 1)
    val undoEvents: SharedFlow<SelectionUndoEvent> = _undoEvents.asSharedFlow()

    fun toggleMessageSelection(messageId: Long) {
        val current: Set<Long> = _selectedMessageIds.value
        _selectedMessageIds.value =
            if (messageId in current) current - messageId else current + messageId
    }

    fun clearSelection() {
        _selectedMessageIds.value = emptySet()
    }

    fun selectAll(messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        _selectedMessageIds.value = messageIds.toSet()
    }

    suspend fun suggestFilterFromSelection(): Boolean {
        val selectedIds: Set<Long> = _selectedMessageIds.value
        if (selectedIds.isEmpty()) return false
        val messages: List<Message> = selectedIds.mapNotNull { id ->
            messageRepository.getMessageById(id)
        }
        val draft: SuggestedFilter = filterSuggester.suggest(messages) ?: return false
        filterDraftHolder.set(draft)
        return true
    }

    fun performSelectionAction(action: SelectionAction) {
        when (action) {
            SelectionAction.ARCHIVE -> mutateSelected(UndoableAction.ARCHIVE)
            SelectionAction.UNARCHIVE -> mutateSelected(UndoableAction.UNARCHIVE)
            SelectionAction.TRASH -> mutateSelected(UndoableAction.TRASH)
            SelectionAction.RESTORE -> mutateSelected(UndoableAction.RESTORE)
            SelectionAction.DELETE_FOREVER -> deleteSelectedForever()
            SelectionAction.SELECT_ALL -> Unit
            SelectionAction.SUGGEST_FILTER -> Unit
        }
    }

    fun undo(event: SelectionUndoEvent) {
        viewModelScope.launch {
            applyAction(inverseOf(event.action), event.messageIds)
        }
    }

    private fun mutateSelected(action: UndoableAction) {
        val ids: List<Long> = _selectedMessageIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            applyAction(action, ids)
            clearSelection()
            _undoEvents.tryEmit(SelectionUndoEvent(action, ids))
        }
    }

    private fun deleteSelectedForever() {
        val ids: List<Long> = _selectedMessageIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            messageRepository.deleteMessages(ids)
            clearSelection()
        }
    }

    private suspend fun applyAction(action: UndoableAction, ids: List<Long>) {
        when (action) {
            UndoableAction.ARCHIVE -> messageRepository.setArchivedBulk(ids, true)
            UndoableAction.UNARCHIVE -> messageRepository.setArchivedBulk(ids, false)
            UndoableAction.TRASH -> messageRepository.setTrashedBulk(ids, true)
            UndoableAction.RESTORE -> messageRepository.setTrashedBulk(ids, false)
        }
    }

    private fun inverseOf(action: UndoableAction): UndoableAction = when (action) {
        UndoableAction.ARCHIVE -> UndoableAction.UNARCHIVE
        UndoableAction.UNARCHIVE -> UndoableAction.ARCHIVE
        UndoableAction.TRASH -> UndoableAction.RESTORE
        UndoableAction.RESTORE -> UndoableAction.TRASH
    }
}

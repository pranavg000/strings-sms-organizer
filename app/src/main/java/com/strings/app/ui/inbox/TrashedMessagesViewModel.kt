package com.strings.app.ui.inbox

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.strings.app.domain.filter.FilterDraftHolder
import com.strings.app.domain.filter.FilterSuggester
import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.ui.common.SelectableMessagesViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TrashedMessagesViewModel(
    private val messageRepository: MessageRepository,
    filterSuggester: FilterSuggester,
    filterDraftHolder: FilterDraftHolder
) : SelectableMessagesViewModel(messageRepository, filterSuggester, filterDraftHolder) {
    val messages: Flow<PagingData<Message>> =
        messageRepository.getPagedTrashedMessages().cachedIn(viewModelScope)

    fun emptyTrash() {
        viewModelScope.launch {
            messageRepository.deleteAllTrashed()
            clearSelection()
        }
    }
}

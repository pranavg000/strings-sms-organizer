package com.strings.app.ui.inbox

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.strings.app.domain.filter.FilterDraftHolder
import com.strings.app.domain.filter.FilterSuggester
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.Tag
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.ui.common.SelectableMessagesViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class TagMessagesViewModel(
    private val messageRepository: MessageRepository,
    private val tagRepository: TagRepository,
    filterSuggester: FilterSuggester,
    filterDraftHolder: FilterDraftHolder
) : SelectableMessagesViewModel(messageRepository, filterSuggester, filterDraftHolder) {
    private val _tag = MutableStateFlow<Tag?>(null)
    val tag: StateFlow<Tag?> = _tag.asStateFlow()

    private val pagingCache: MutableMap<Long, Flow<PagingData<Message>>> = mutableMapOf()

    fun loadTag(tagId: Long) {
        viewModelScope.launch {
            _tag.value = tagRepository.getTagById(tagId)
        }
    }

    fun messagesForTag(tagId: Long): Flow<PagingData<Message>> {
        return pagingCache.getOrPut(tagId) {
            flow {
                val descendantIds: List<Long> = tagRepository.getDescendantTagIds(tagId)
                emitAll(messageRepository.getPagedMessagesByTagIds(descendantIds))
            }.cachedIn(viewModelScope)
        }
    }
}

package com.strings.app.ui.filters

import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.strings.app.domain.filter.FilterDraftHolder
import com.strings.app.domain.filter.FilterEngine
import com.strings.app.domain.filter.FilterSuggester
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.ui.common.SelectableMessagesViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FilterMessagesViewModel(
    private val messageRepository: MessageRepository,
    private val filterRepository: FilterRepository,
    private val filterEngine: FilterEngine,
    filterSuggester: FilterSuggester,
    filterDraftHolder: FilterDraftHolder
) : SelectableMessagesViewModel(messageRepository, filterSuggester, filterDraftHolder) {
    private val _filter = MutableStateFlow<Filter?>(null)
    val filter: StateFlow<Filter?> = _filter.asStateFlow()

    private val pagingCache: MutableMap<Long, Flow<PagingData<Message>>> = mutableMapOf()

    fun loadFilter(filterId: Long) {
        viewModelScope.launch {
            _filter.value = filterRepository.getFilterById(filterId)
        }
    }

    fun messagesForFilter(filterId: Long): Flow<PagingData<Message>> {
        return pagingCache.getOrPut(filterId) {
            flow {
                val filter: Filter? = filterRepository.getFilterById(filterId)
                if (filter == null) {
                    emit(
                        PagingData.empty(
                            sourceLoadStates = LoadStates(
                                refresh = LoadState.NotLoading(endOfPaginationReached = true),
                                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                                append = LoadState.NotLoading(endOfPaginationReached = true)
                            )
                        )
                    )
                    return@flow
                }
                emitAll(
                    messageRepository.getPagedAllMessages().map { paging ->
                        paging.filter { message -> filterEngine.matches(filter, message) }
                    }
                )
            }.cachedIn(viewModelScope)
        }
    }
}

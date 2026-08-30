package com.strings.app.ui.search

import androidx.lifecycle.viewModelScope
import com.strings.app.domain.filter.FilterDraftHolder
import com.strings.app.domain.filter.FilterSuggester
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.Tag
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.usecase.SearchMessagesUseCase
import com.strings.app.ui.common.SelectableMessagesViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Message> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val unreadOnly: Boolean = false,
    val otpOnly: Boolean = false,
    val dateRangeMillis: Pair<Long, Long>? = null,
    val isSearching: Boolean = false
) {
    val hasActiveFilters: Boolean
        get() = selectedTagIds.isNotEmpty() || unreadOnly || otpOnly || dateRangeMillis != null
}

class SearchViewModel(
    private val searchMessagesUseCase: SearchMessagesUseCase,
    messageRepository: MessageRepository,
    tagRepository: TagRepository,
    filterSuggester: FilterSuggester,
    filterDraftHolder: FilterDraftHolder
) : SelectableMessagesViewModel(messageRepository, filterSuggester, filterDraftHolder) {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var rawResults: List<Message> = emptyList()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(allTags = tagRepository.getAllTagsList())
        }
    }

    fun updateQuery(query: String) {
        clearSelection()
        _uiState.value = _uiState.value.copy(query = query)
        debounceSearch(query)
    }

    fun toggleTagFilter(tagId: Long) {
        val current: Set<Long> = _uiState.value.selectedTagIds
        val updated: Set<Long> = if (tagId in current) current - tagId else current + tagId
        updateFilters { it.copy(selectedTagIds = updated) }
    }

    fun toggleUnreadOnly() {
        updateFilters { it.copy(unreadOnly = !it.unreadOnly) }
    }

    fun toggleOtpOnly() {
        updateFilters { it.copy(otpOnly = !it.otpOnly) }
    }

    fun setDateRange(range: Pair<Long, Long>?) {
        updateFilters { it.copy(dateRangeMillis = range) }
    }

    fun clearFilters() {
        updateFilters {
            it.copy(
                selectedTagIds = emptySet(),
                unreadOnly = false,
                otpOnly = false,
                dateRangeMillis = null
            )
        }
    }

    private fun updateFilters(transform: (SearchUiState) -> SearchUiState) {
        clearSelection()
        _uiState.value = transform(_uiState.value)
        _uiState.value = _uiState.value.copy(results = applyFilters(rawResults))
    }

    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            rawResults = emptyList()
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isSearching = true)
            searchMessagesUseCase.execute(query).collectLatest { messages ->
                rawResults = messages
                _uiState.value = _uiState.value.copy(
                    results = applyFilters(messages),
                    isSearching = false
                )
            }
        }
    }

    private fun applyFilters(messages: List<Message>): List<Message> {
        val state: SearchUiState = _uiState.value
        if (!state.hasActiveFilters) return messages
        return messages.filter { message ->
            val tagMatch: Boolean = state.selectedTagIds.isEmpty() ||
                message.tags.any { it.id in state.selectedTagIds }
            val unreadMatch: Boolean = !state.unreadOnly || !message.isRead
            val otpMatch: Boolean = !state.otpOnly || message.isOtp
            val dateMatch: Boolean = state.dateRangeMillis?.let { (from, to) ->
                message.timestamp in from..to
            } ?: true
            tagMatch && unreadMatch && otpMatch && dateMatch
        }
    }
}

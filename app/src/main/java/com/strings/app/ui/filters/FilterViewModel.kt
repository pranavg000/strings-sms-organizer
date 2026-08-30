package com.strings.app.ui.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.strings.app.domain.filter.FilterDraftHolder
import com.strings.app.domain.filter.FilterEngine
import com.strings.app.domain.filter.FilterSuggester
import com.strings.app.domain.filter.SuggestedFilter
import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.ConditionGroup
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.FilterAction
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.Tag
import com.strings.app.domain.model.prune
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.work.ApplyFilterWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FilterListUiState(
    val filters: List<Filter> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isLoading: Boolean = true
)

data class FilterEditUiState(
    val filter: Filter = Filter(name = "", actions = emptyList()),
    val availableTags: List<Tag> = emptyList(),
    val isNew: Boolean = true,
    val isSaving: Boolean = false,
    val applyToExisting: Boolean = true
)

class FilterViewModel(
    private val filterRepository: FilterRepository,
    private val tagRepository: TagRepository,
    private val filterDraftHolder: FilterDraftHolder,
    private val workManager: WorkManager,
    private val messageRepository: MessageRepository,
    private val filterEngine: FilterEngine,
    private val filterSuggester: FilterSuggester
) : ViewModel() {
    private val _listState = MutableStateFlow(FilterListUiState())
    val listState: StateFlow<FilterListUiState> = _listState.asStateFlow()
    private val _orderedFilters = MutableStateFlow<List<Filter>>(emptyList())
    val orderedFilters: StateFlow<List<Filter>> = _orderedFilters.asStateFlow()
    private val _editState = MutableStateFlow(FilterEditUiState())
    val editState: StateFlow<FilterEditUiState> = _editState.asStateFlow()
    private val _suggestion = MutableStateFlow<ConditionGroup?>(null)
    val suggestion: StateFlow<ConditionGroup?> = _suggestion.asStateFlow()
    private val _isSuggesting = MutableStateFlow(false)
    val isSuggesting: StateFlow<Boolean> = _isSuggesting.asStateFlow()

    init {
        loadFilters()
    }

    private fun loadFilters() {
        viewModelScope.launch {
            filterRepository.getAllFilters().collectLatest { filters ->
                val tags = tagRepository.getAllTags().first()
                _listState.value = _listState.value.copy(
                    filters = filters,
                    tags = tags,
                    isLoading = false
                )
                val current = _orderedFilters.value
                val sameMembers = current.map { it.id }.toSet() == filters.map { it.id }.toSet()
                _orderedFilters.value = if (sameMembers && current.isNotEmpty()) {
                    current.mapNotNull { existing -> filters.find { it.id == existing.id } }
                } else {
                    filters
                }
            }
        }
    }

    fun moveFilter(fromIndex: Int, toIndex: Int) {
        val current = _orderedFilters.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _orderedFilters.value = current
    }

    fun persistFilterOrder() {
        viewModelScope.launch {
            filterRepository.setFilterOrder(_orderedFilters.value.map { it.id })
        }
    }

    fun loadFilterForEdit(filterId: Long) {
        viewModelScope.launch {
            val tags = tagRepository.getAllTags().first()
            if (filterId == -1L) {
                val draft: SuggestedFilter? = filterDraftHolder.consume()
                val draftFilter: Filter = if (draft != null) {
                    Filter(
                        name = draft.name,
                        isEnabled = true,
                        root = draft.root,
                        actions = emptyList()
                    )
                } else {
                    Filter(name = "", actions = emptyList())
                }
                _editState.value = FilterEditUiState(
                    filter = draftFilter,
                    availableTags = tags,
                    isNew = true
                )
            } else {
                val filter = filterRepository.getFilterById(filterId)
                if (filter != null) {
                    _editState.value = FilterEditUiState(
                        filter = filter,
                        availableTags = tags,
                        isNew = false
                    )
                }
            }
        }
    }

    fun updateFilterName(name: String) {
        _editState.value = _editState.value.copy(
            filter = _editState.value.filter.copy(name = name)
        )
    }

    fun updateFilterIsEnabled(isEnabled: Boolean) {
        _editState.value = _editState.value.copy(
            filter = _editState.value.filter.copy(isEnabled = isEnabled)
        )
    }

    fun toggleApplyToExisting() {
        _editState.value = _editState.value.copy(
            applyToExisting = !_editState.value.applyToExisting
        )
    }

    fun updateRoot(root: ConditionGroup) {
        _editState.value = _editState.value.copy(
            filter = _editState.value.filter.copy(root = root)
        )
    }

    fun toggleAction(actionType: ActionType, targetTagId: Long? = null) {
        val current = _editState.value.filter
        val actions = current.actions.toMutableList()
        val existingIndex = actions.indexOfFirst {
            it.actionType == actionType && (actionType != ActionType.ASSIGN_TAG || it.targetTagId == targetTagId)
        }
        if (existingIndex >= 0) {
            actions.removeAt(existingIndex)
        } else {
            actions.add(FilterAction(actionType = actionType, targetTagId = targetTagId))
        }
        _editState.value = _editState.value.copy(
            filter = current.copy(actions = actions)
        )
    }

    fun createTagInline(name: String) {
        viewModelScope.launch {
            val newId: Long = tagRepository.insertTag(Tag(name = name, color = ""))
            val tags: List<Tag> = tagRepository.getAllTags().first()
            val current: Filter = _editState.value.filter
            val actions: List<FilterAction> = current.actions +
                FilterAction(actionType = ActionType.ASSIGN_TAG, targetTagId = newId)
            _editState.value = _editState.value.copy(
                availableTags = tags,
                filter = current.copy(actions = actions)
            )
        }
    }

    fun suggestBetterFilter() {
        viewModelScope.launch {
            _isSuggesting.value = true
            try {
                val prunedRoot: ConditionGroup = _editState.value.filter.root.prune()
                val suggestedRoot: ConditionGroup? = withContext(Dispatchers.Default) {
                    val allMessages: List<Message> = messageRepository.getAllMessages().first()
                    val tempFilter = Filter(name = "temp", root = prunedRoot)
                    val matched: List<Message> = allMessages.filter { filterEngine.matches(tempFilter, it) }
                    filterSuggester.suggest(matched)?.root
                }
                _suggestion.value = suggestedRoot
            } finally {
                _isSuggesting.value = false
            }
        }
    }

    fun applySuggestion() {
        val root: ConditionGroup = _suggestion.value ?: return
        updateRoot(root)
        _suggestion.value = null
    }

    fun dismissSuggestion() {
        _suggestion.value = null
    }

    fun saveFilter(onSaved: (appliedInBackground: Boolean) -> Unit) {
        viewModelScope.launch {
            _editState.value = _editState.value.copy(isSaving = true)
            val current = _editState.value.filter
            val filter = current.copy(root = current.root.prune())
            val savedId = if (_editState.value.isNew) {
                val nextPriority = filterRepository.getMaxPriority() + 1
                filterRepository.insertFilter(filter.copy(priority = nextPriority))
            } else {
                filterRepository.updateFilter(filter)
                filter.id
            }
            val applyInBackground = _editState.value.applyToExisting && filter.isEnabled
            if (applyInBackground) {
                enqueueApplyToExisting(savedId)
            }
            _editState.value = _editState.value.copy(isSaving = false)
            onSaved(applyInBackground)
        }
    }

    private fun enqueueApplyToExisting(filterId: Long) {
        val request = OneTimeWorkRequestBuilder<ApplyFilterWorker>()
            .setInputData(workDataOf(ApplyFilterWorker.KEY_FILTER_ID to filterId))
            .build()
        workManager.enqueueUniqueWork(
            "apply_filter_$filterId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun deleteFilter(filterId: Long) {
        viewModelScope.launch {
            filterRepository.deleteFilter(filterId)
        }
    }

    fun toggleFilterEnabled(filterId: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            filterRepository.setEnabled(filterId, isEnabled)
        }
    }
}

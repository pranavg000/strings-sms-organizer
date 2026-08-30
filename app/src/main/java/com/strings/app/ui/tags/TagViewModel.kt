package com.strings.app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strings.app.domain.model.TabConfig
import com.strings.app.domain.model.Tag
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val MAX_VISIBLE_TABS: Int = 5

data class TagListUiState(
    val tags: List<Tag> = emptyList(),
    val tabTagIds: Set<Long> = emptySet(),
    val tagCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true
)

data class TagEditUiState(
    val tag: Tag = Tag(name = "", color = ""),
    val availableParents: List<Tag> = emptyList(),
    val isNew: Boolean = true,
    val isTab: Boolean = false,
    val isSaving: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val deleteBlockedByFilters: List<String> = emptyList()
)

class TagViewModel(
    private val tagRepository: TagRepository,
    private val messageRepository: MessageRepository,
    private val filterRepository: FilterRepository
) : ViewModel() {
    private val _listState = MutableStateFlow(TagListUiState())
    val listState: StateFlow<TagListUiState> = _listState.asStateFlow()
    private val _editState = MutableStateFlow(TagEditUiState())
    val editState: StateFlow<TagEditUiState> = _editState.asStateFlow()

    init {
        loadTags()
    }

    private fun loadTags() {
        viewModelScope.launch {
            combine(
                tagRepository.getAllTags(),
                tagRepository.getVisibleTabs(),
                tagRepository.getTagMessageCounts()
            ) { tags, tabs, counts ->
                TagListUiState(
                    tags = tags,
                    tabTagIds = tabs.map { it.tagId }.toSet(),
                    tagCounts = counts,
                    isLoading = false
                )
            }.collectLatest { state ->
                _listState.value = state
            }
        }
    }

    fun loadTagForEdit(tagId: Long) {
        viewModelScope.launch {
            val allTags = tagRepository.getAllTags().first()
            if (tagId == -1L) {
                _editState.value = TagEditUiState(
                    tag = Tag(name = "", color = ""),
                    availableParents = allTags.filter { it.parentTagId == null },
                    isNew = true
                )
            } else {
                val tag = tagRepository.getTagById(tagId)
                if (tag != null) {
                    _editState.value = TagEditUiState(
                        tag = tag,
                        availableParents = allTags.filter { it.id != tagId && it.parentTagId == null },
                        isNew = false,
                        isTab = tagId in _listState.value.tabTagIds
                    )
                }
            }
        }
    }

    fun updateTagName(name: String) {
        _editState.value = _editState.value.copy(
            tag = _editState.value.tag.copy(name = name)
        )
    }

    fun updateTagIcon(icon: String) {
        _editState.value = _editState.value.copy(
            tag = _editState.value.tag.copy(icon = icon)
        )
    }

    fun updateParentTag(parentId: Long?) {
        _editState.value = _editState.value.copy(
            tag = _editState.value.tag.copy(parentTagId = parentId)
        )
    }

    fun toggleIsTab(isTab: Boolean) {
        _editState.value = _editState.value.copy(isTab = isTab)
    }

    fun saveTag(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _editState.value = _editState.value.copy(isSaving = true)
            val tag = _editState.value.tag
            val savedId = if (_editState.value.isNew) {
                tagRepository.insertTag(tag)
            } else {
                tagRepository.updateTag(tag)
                tag.id
            }
            if (_editState.value.isTab && savedId !in _listState.value.tabTagIds) {
                val currentTabCount = _listState.value.tabTagIds.size
                if (currentTabCount < MAX_VISIBLE_TABS) {
                    tagRepository.insertTabConfig(
                        TabConfig(tagId = savedId, position = currentTabCount)
                    )
                }
            } else if (!_editState.value.isTab && savedId in _listState.value.tabTagIds) {
                tagRepository.deleteTabConfigByTagId(savedId)
            }
            _editState.value = _editState.value.copy(isSaving = false)
            onSuccess()
        }
    }

    fun requestDeleteTag() {
        viewModelScope.launch {
            val tagId = _editState.value.tag.id
            val blockingFilters = filterRepository.getFilterNamesUsingTag(tagId)
            if (blockingFilters.isNotEmpty()) {
                _editState.value = _editState.value.copy(deleteBlockedByFilters = blockingFilters)
            } else {
                _editState.value = _editState.value.copy(showDeleteConfirm = true)
            }
        }
    }

    fun confirmDeleteTag(onDeleted: () -> Unit) {
        viewModelScope.launch {
            tagRepository.deleteTag(_editState.value.tag.id)
            _editState.value = _editState.value.copy(showDeleteConfirm = false)
            onDeleted()
        }
    }

    fun dismissDeleteDialogs() {
        _editState.value = _editState.value.copy(
            showDeleteConfirm = false,
            deleteBlockedByFilters = emptyList()
        )
    }
}

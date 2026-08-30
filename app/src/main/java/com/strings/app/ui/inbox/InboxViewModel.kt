package com.strings.app.ui.inbox

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.strings.app.data.prefs.SettingsDataStore
import com.strings.app.domain.filter.FilterDraftHolder
import com.strings.app.domain.filter.FilterSuggester
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.TabConfig
import com.strings.app.domain.model.Tag
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.usecase.AdoptLegacyWalletAccountsUseCase
import com.strings.app.ui.common.SelectableMessagesViewModel
import com.strings.app.util.DatabaseSeeder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TabWithTag(
    val config: TabConfig,
    val tag: Tag
)

class InboxViewModel(
    private val messageRepository: MessageRepository,
    private val tagRepository: TagRepository,
    private val filterRepository: FilterRepository,
    private val settingsDataStore: SettingsDataStore,
    private val adoptLegacyWalletAccountsUseCase: AdoptLegacyWalletAccountsUseCase,
    filterSuggester: FilterSuggester,
    filterDraftHolder: FilterDraftHolder
) : SelectableMessagesViewModel(messageRepository, filterSuggester, filterDraftHolder) {
    val inboxTagId: StateFlow<Long?> = flow {
        val storedId: Long = settingsDataStore.getInboxTagId()
        emit(storedId.takeIf { it > 0L })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val tabs: StateFlow<List<TabWithTag>> = tagRepository.getVisibleTabs()
        .map { configs ->
            configs.mapNotNull { config ->
                tagRepository.getTagById(config.tagId)?.let { tag ->
                    TabWithTag(config = config, tag = tag)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val allTags: StateFlow<List<Tag>> = tagRepository.getAllTags()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val allFilters: StateFlow<List<Filter>> = filterRepository.getAllFilters()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val pagingCache: MutableMap<Long, Flow<PagingData<Message>>> = mutableMapOf()
    private var seederRun: Boolean = false

    fun initialize(seeder: DatabaseSeeder) {
        if (seederRun) return
        seederRun = true
        viewModelScope.launch {
            seeder.setupFirstRun()
            adoptLegacyWalletAccountsUseCase.execute()
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

    fun archiveMessage(messageId: Long) {
        viewModelScope.launch {
            messageRepository.setArchived(messageId, true)
        }
    }

    fun trashMessage(messageId: Long) {
        viewModelScope.launch {
            messageRepository.setTrashed(messageId, true)
        }
    }

}

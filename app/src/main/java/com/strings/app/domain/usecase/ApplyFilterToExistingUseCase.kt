package com.strings.app.domain.usecase

import com.strings.app.data.prefs.SettingsDataStore
import com.strings.app.domain.filter.FilterEngine
import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import kotlinx.coroutines.flow.first

class ApplyFilterToExistingUseCase(
    private val filterRepository: FilterRepository,
    private val messageRepository: MessageRepository,
    private val tagRepository: TagRepository,
    private val filterEngine: FilterEngine,
    private val settings: SettingsDataStore
) {
    suspend fun execute(filterId: Long) {
        val filter: Filter = filterRepository.getFilterById(filterId) ?: return
        val allMessages: List<Message> = messageRepository.getAllMessages().first()
        val matchingMessages: List<Message> = allMessages.filter { filterEngine.matches(filter, it) }
        for (action in filter.actions) {
            when (action.actionType) {
                ActionType.ASSIGN_TAG -> {
                    val tagId: Long = action.targetTagId ?: continue
                    matchingMessages.forEach { message ->
                        messageRepository.addTagToMessage(message.id, tagId)
                    }
                }
                ActionType.REMOVE_FROM_INBOX -> {
                    val inboxTagId: Long = settings.getInboxTagId()
                    if (inboxTagId <= 0L) continue
                    matchingMessages.forEach { message ->
                        messageRepository.removeTagFromMessage(message.id, inboxTagId)
                    }
                }
                ActionType.ARCHIVE -> {
                    matchingMessages.forEach { message ->
                        messageRepository.setArchived(message.id, true)
                    }
                }
                ActionType.TRASH -> {
                    matchingMessages.forEach { message ->
                        messageRepository.setTrashed(message.id, true)
                    }
                }
                ActionType.MARK_READ -> {
                    matchingMessages.forEach { message ->
                        messageRepository.setRead(message.id, true)
                    }
                }
                ActionType.SUPPRESS_NOTIFICATION -> { }
                ActionType.NOTIFY_SILENTLY -> { }
                ActionType.STOP_PROCESSING -> { }
            }
        }
    }
}

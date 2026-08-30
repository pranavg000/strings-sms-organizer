package com.strings.app.domain.usecase

import com.strings.app.data.prefs.SettingsDataStore
import com.strings.app.domain.filter.FilterEngine
import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository

/**
 * Notification-related outcome of running the filters over one message.
 * [suppressNotification] wins over [notifySilently] when both are set.
 */
data class FilterOutcome(
    val suppressNotification: Boolean = false,
    val notifySilently: Boolean = false
)

class ApplyFiltersUseCase(
    private val filterRepository: FilterRepository,
    private val messageRepository: MessageRepository,
    private val filterEngine: FilterEngine,
    private val settings: SettingsDataStore
) {
    suspend fun applyToMessage(message: Message): FilterOutcome {
        var suppressNotification = false
        var notifySilently = false
        val filters = filterRepository.getEnabledFilters()
        val matchingFilters = filterEngine.findMatchingFilters(filters, message)
        for (filter in matchingFilters) {
            var stopProcessing = false
            for (action in filter.actions) {
                when (action.actionType) {
                    ActionType.ASSIGN_TAG -> {
                        action.targetTagId?.let { tagId ->
                            messageRepository.addTagToMessage(message.id, tagId)
                        }
                    }
                    ActionType.REMOVE_FROM_INBOX -> {
                        val inboxTagId = settings.getInboxTagId()
                        if (inboxTagId > 0L) {
                            messageRepository.removeTagFromMessage(message.id, inboxTagId)
                        }
                    }
                    ActionType.ARCHIVE -> {
                        messageRepository.setArchived(message.id, true)
                    }
                    ActionType.TRASH -> {
                        messageRepository.setTrashed(message.id, true)
                    }
                    ActionType.MARK_READ -> {
                        messageRepository.setRead(message.id, true)
                    }
                    ActionType.SUPPRESS_NOTIFICATION -> {
                        suppressNotification = true
                    }
                    ActionType.NOTIFY_SILENTLY -> {
                        notifySilently = true
                    }
                    ActionType.STOP_PROCESSING -> {
                        stopProcessing = true
                    }
                }
            }
            // The stopping filter's own actions all run first; only the
            // lower-priority filters are skipped for this message.
            if (stopProcessing) break
        }
        return FilterOutcome(
            suppressNotification = suppressNotification,
            notifySilently = notifySilently
        )
    }
}

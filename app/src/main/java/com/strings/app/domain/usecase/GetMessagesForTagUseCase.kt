package com.strings.app.domain.usecase

import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow

class GetMessagesForTagUseCase(
    private val messageRepository: MessageRepository,
    private val tagRepository: TagRepository
) {
    suspend fun execute(tagId: Long): Flow<List<Message>> {
        val descendantIds = tagRepository.getDescendantTagIds(tagId)
        return if (descendantIds.size == 1) {
            messageRepository.getMessagesByTagId(tagId)
        } else {
            messageRepository.getMessagesByTagIds(descendantIds)
        }
    }
}

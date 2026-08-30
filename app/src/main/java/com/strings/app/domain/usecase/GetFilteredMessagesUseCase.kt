package com.strings.app.domain.usecase

import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow

class GetFilteredMessagesUseCase(
    private val messageRepository: MessageRepository
) {
    fun execute(): Flow<List<Message>> {
        return messageRepository.getAllMessages()
    }
}

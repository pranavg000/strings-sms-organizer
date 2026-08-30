package com.strings.app.domain.usecase

import com.strings.app.domain.model.Message
import com.strings.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow

class SearchMessagesUseCase(
    private val messageRepository: MessageRepository
) {
    fun execute(query: String): Flow<List<Message>> {
        return messageRepository.searchMessages(query)
    }
}

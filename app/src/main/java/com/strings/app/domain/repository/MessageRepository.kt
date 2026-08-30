package com.strings.app.domain.repository

import androidx.paging.PagingData
import com.strings.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getAllMessages(): Flow<List<Message>>
    fun getMessagesByTagId(tagId: Long): Flow<List<Message>>
    fun getMessagesByTagIds(tagIds: List<Long>): Flow<List<Message>>
    fun getPagedMessagesByTagIds(tagIds: List<Long>): Flow<PagingData<Message>>
    fun getPagedAllMessages(): Flow<PagingData<Message>>
    fun getPagedArchivedMessages(): Flow<PagingData<Message>>
    fun getPagedTrashedMessages(): Flow<PagingData<Message>>
    fun searchMessages(query: String): Flow<List<Message>>
    fun getArchivedMessages(): Flow<List<Message>>
    fun getTrashedMessages(): Flow<List<Message>>
    suspend fun getMessageById(id: Long): Message?
    suspend fun getMessagesSince(since: Long): List<Message>
    suspend fun insertMessage(message: Message): Long
    suspend fun updateMessage(message: Message)
    suspend fun setArchived(messageId: Long, isArchived: Boolean)
    suspend fun setTrashed(messageId: Long, isTrashed: Boolean)
    suspend fun setArchivedBulk(messageIds: List<Long>, isArchived: Boolean)
    suspend fun setTrashedBulk(messageIds: List<Long>, isTrashed: Boolean)
    suspend fun deleteMessages(messageIds: List<Long>)
    suspend fun deleteAllTrashed()
    suspend fun setRead(messageId: Long, isRead: Boolean)
    suspend fun addTagToMessage(messageId: Long, tagId: Long)
    suspend fun removeTagFromMessage(messageId: Long, tagId: Long)
    suspend fun getTagIdsForMessage(messageId: Long): List<Long>
    suspend fun getAllMessagesOnce(): List<Message>
    suspend fun getTagIdsByMessage(): Map<Long, List<Long>>
    suspend fun replaceTagsForMessage(messageId: Long, tagIds: List<Long>)
    suspend fun getMessageCount(): Int
    suspend fun getKnownDeviceMessageIds(): List<Long>
    suspend fun findMessageIdByContent(sender: String, body: String, timestamp: Long): Long?
    suspend fun findUnlinkedMessageByContent(sender: String, body: String, timestamp: Long): Long?
    suspend fun setDeviceMessageId(messageId: Long, deviceMessageId: Long)
    suspend fun reconcileImported(messageId: Long, deviceMessageId: Long, sender: String, timestamp: Long)
    suspend fun deleteDuplicates()
    suspend fun deleteUnlinkedDuplicates()
}

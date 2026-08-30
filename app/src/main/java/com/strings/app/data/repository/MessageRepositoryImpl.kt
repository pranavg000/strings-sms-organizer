package com.strings.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.strings.app.data.contacts.ContactNameResolver
import com.strings.app.data.local.db.dao.MessageDao
import com.strings.app.data.local.db.dao.MessageWithTagsRelation
import com.strings.app.data.local.db.dao.TagDao
import com.strings.app.data.local.db.entity.MessageEntity
import com.strings.app.data.local.db.entity.MessageTagEntity
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.Tag
import com.strings.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class MessageRepositoryImpl(
    private val messageDao: MessageDao,
    private val tagDao: TagDao,
    private val contactNameResolver: ContactNameResolver
) : MessageRepository {
    override fun getAllMessages(): Flow<List<Message>> {
        return messageDao.getAllMessages().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMessagesByTagId(tagId: Long): Flow<List<Message>> {
        return messageDao.getMessagesByTag(tagId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMessagesByTagIds(tagIds: List<Long>): Flow<List<Message>> {
        return messageDao.getMessagesByTags(tagIds).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getPagedMessagesByTagIds(tagIds: List<Long>): Flow<PagingData<Message>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            messageDao.pagingMessagesByTags(tagIds)
        }.flow.map { pagingData ->
            pagingData.map { relation -> relation.toDomain() }
        }
    }

    override fun getPagedAllMessages(): Flow<PagingData<Message>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            messageDao.pagingAllMessages()
        }.flow.map { pagingData ->
            pagingData.map { relation -> relation.toDomain() }
        }
    }

    override fun getPagedArchivedMessages(): Flow<PagingData<Message>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            messageDao.pagingArchivedMessages()
        }.flow.map { pagingData ->
            pagingData.map { relation -> relation.toDomain() }
        }
    }

    override fun getPagedTrashedMessages(): Flow<PagingData<Message>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            messageDao.pagingTrashedMessages()
        }.flow.map { pagingData ->
            pagingData.map { relation -> relation.toDomain() }
        }
    }

    override fun searchMessages(query: String): Flow<List<Message>> {
        val matchingSenders: List<String> = contactNameResolver.sendersMatchingName(query)
        val textFlow: Flow<List<MessageWithTagsRelation>> = messageDao.searchMessagesWithTags(query)
        if (matchingSenders.isEmpty()) {
            return textFlow.map { relations -> relations.map { it.toDomain() } }
        }
        val contactFlow: Flow<List<MessageWithTagsRelation>> = messageDao.getMessagesBySenders(matchingSenders)
        return textFlow.combine(contactFlow) { textResults, contactResults ->
            val ids: MutableSet<Long> = mutableSetOf()
            val merged: MutableList<Message> = mutableListOf()
            for (r in textResults) {
                if (ids.add(r.message.id)) merged.add(r.toDomain())
            }
            for (r in contactResults) {
                if (ids.add(r.message.id)) merged.add(r.toDomain())
            }
            merged.sortedByDescending { it.timestamp }
        }
    }

    override fun getArchivedMessages(): Flow<List<Message>> {
        return messageDao.getArchivedMessages().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTrashedMessages(): Flow<List<Message>> {
        return messageDao.getTrashedMessages().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMessageById(id: Long): Message? {
        val relation = messageDao.getMessageWithTagsById(id) ?: return null
        return relation.toDomain()
    }

    override suspend fun getMessagesSince(since: Long): List<Message> {
        return messageDao.getMessagesSince(since).map { it.toDomain() }
    }

    override suspend fun insertMessage(message: Message): Long {
        return messageDao.insertMessage(message.toEntity())
    }

    override suspend fun updateMessage(message: Message) {
        messageDao.updateMessage(message.toEntity())
    }

    override suspend fun setArchived(messageId: Long, isArchived: Boolean) {
        messageDao.setArchived(messageId, isArchived)
    }

    override suspend fun setTrashed(messageId: Long, isTrashed: Boolean) {
        messageDao.setTrashed(messageId, isTrashed)
    }

    override suspend fun setArchivedBulk(messageIds: List<Long>, isArchived: Boolean) {
        messageDao.setArchivedBulk(messageIds, isArchived)
    }

    override suspend fun setTrashedBulk(messageIds: List<Long>, isTrashed: Boolean) {
        messageDao.setTrashedBulk(messageIds, isTrashed)
    }

    override suspend fun deleteMessages(messageIds: List<Long>) {
        messageDao.deleteMessages(messageIds)
    }

    override suspend fun deleteAllTrashed() {
        messageDao.deleteAllTrashed()
    }

    override suspend fun setRead(messageId: Long, isRead: Boolean) {
        messageDao.setRead(messageId, isRead)
    }

    override suspend fun addTagToMessage(messageId: Long, tagId: Long) {
        messageDao.insertMessageTag(MessageTagEntity(messageId = messageId, tagId = tagId))
    }

    override suspend fun removeTagFromMessage(messageId: Long, tagId: Long) {
        messageDao.removeMessageTag(messageId, tagId)
    }

    override suspend fun getTagIdsForMessage(messageId: Long): List<Long> {
        return messageDao.getTagIdsForMessage(messageId)
    }

    override suspend fun getAllMessagesOnce(): List<Message> {
        return messageDao.getAllMessagesOnce().map { it.toDomain() }
    }

    override suspend fun getTagIdsByMessage(): Map<Long, List<Long>> {
        return messageDao.getAllMessageTags()
            .groupBy(keySelector = { it.messageId }, valueTransform = { it.tagId })
    }

    override suspend fun replaceTagsForMessage(messageId: Long, tagIds: List<Long>) {
        messageDao.replaceMessageTags(
            messageId = messageId,
            tags = tagIds.map { MessageTagEntity(messageId = messageId, tagId = it) }
        )
    }

    override suspend fun getMessageCount(): Int {
        return messageDao.getMessageCount()
    }

    override suspend fun getKnownDeviceMessageIds(): List<Long> {
        return messageDao.getKnownDeviceMessageIds()
    }

    override suspend fun findMessageIdByContent(sender: String, body: String, timestamp: Long): Long? {
        return messageDao.findMessageIdByContent(sender, body, timestamp)
    }

    override suspend fun findUnlinkedMessageByContent(
        sender: String,
        body: String,
        timestamp: Long
    ): Long? {
        val candidates = messageDao.findUnlinkedCandidates(
            body = body,
            timestamp = timestamp,
            minTimestamp = timestamp - RECONCILE_WINDOW_MS,
            maxTimestamp = timestamp + RECONCILE_WINDOW_MS
        )
        val normalizedTarget: String = normalizeSender(sender)
        return candidates.firstOrNull { normalizeSender(it.sender) == normalizedTarget }?.id
    }

    private fun normalizeSender(sender: String): String {
        val digits: String = sender.filter { it.isDigit() }
        return if (digits.length >= MIN_PHONE_DIGITS) {
            digits.takeLast(MIN_PHONE_DIGITS)
        } else {
            sender.filter { it.isLetterOrDigit() }.uppercase()
        }
    }

    override suspend fun setDeviceMessageId(messageId: Long, deviceMessageId: Long) {
        messageDao.setDeviceMessageId(messageId, deviceMessageId)
    }

    override suspend fun reconcileImported(
        messageId: Long,
        deviceMessageId: Long,
        sender: String,
        timestamp: Long
    ) {
        messageDao.reconcileImported(messageId, deviceMessageId, sender, timestamp)
    }

    override suspend fun deleteDuplicates() {
        messageDao.deleteDuplicateMessages()
    }

    override suspend fun deleteUnlinkedDuplicates() {
        messageDao.deleteUnlinkedDuplicates(RECONCILE_WINDOW_MS)
    }

    private fun MessageWithTagsRelation.toDomain(): Message =
        message.toDomain(tags.map { it.toDomain() })

    private fun MessageEntity.toDomain(tags: List<Tag> = emptyList()): Message = Message(
        id = id,
        sender = sender,
        senderName = contactNameResolver.resolve(sender) ?: senderName,
        body = body,
        timestamp = timestamp,
        isRead = isRead,
        isArchived = isArchived,
        isTrashed = isTrashed,
        isOtp = isOtp,
        otpCode = otpCode,
        deviceMessageId = deviceMessageId,
        tags = tags
    )

    private fun Message.toEntity(): MessageEntity = MessageEntity(
        id = id,
        sender = sender,
        senderName = senderName,
        body = body,
        timestamp = timestamp,
        isRead = isRead,
        isArchived = isArchived,
        isTrashed = isTrashed,
        isOtp = isOtp,
        otpCode = otpCode,
        deviceMessageId = deviceMessageId
    )

    private fun com.strings.app.data.local.db.entity.TagEntity.toDomain(): Tag = Tag(
        id = id,
        name = name,
        color = color,
        icon = icon,
        parentTagId = parentTagId,
        sortOrder = sortOrder,
        isSystemTag = isSystemTag
    )

    companion object {
        private const val RECONCILE_WINDOW_MS: Long = 5 * 60 * 1000L
        private const val MIN_PHONE_DIGITS: Int = 10
    }
}

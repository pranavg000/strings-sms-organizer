package com.strings.app.data.local.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.strings.app.data.local.db.entity.MessageEntity
import com.strings.app.data.local.db.entity.MessageTagEntity
import com.strings.app.data.local.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class MessageWithTags(
    val message: MessageEntity,
    val tagIds: List<Long>
)

data class UnlinkedCandidate(
    val id: Long,
    val sender: String
)

data class MessageWithTagsRelation(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MessageTagEntity::class,
            parentColumn = "messageId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE isTrashed = 0 AND isArchived = 0 ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isTrashed = 0 ORDER BY timestamp DESC")
    fun getAllIncludingArchived(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageWithTagsById(id: Long): MessageWithTagsRelation?

    @Query("SELECT * FROM messages WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getMessagesSince(since: Long): List<MessageEntity>

    @Query("""
        SELECT DISTINCT m.* FROM messages m 
        INNER JOIN message_tags mt ON m.id = mt.messageId 
        WHERE mt.tagId = :tagId AND m.isTrashed = 0 AND m.isArchived = 0
        ORDER BY m.timestamp DESC
    """)
    fun getMessagesByTag(tagId: Long): Flow<List<MessageEntity>>

    @Query("""
        SELECT DISTINCT m.* FROM messages m
        INNER JOIN message_tags mt ON m.id = mt.messageId
        WHERE mt.tagId IN (:tagIds) AND m.isTrashed = 0 AND m.isArchived = 0
        ORDER BY m.timestamp DESC
    """)
    fun getMessagesByTags(tagIds: List<Long>): Flow<List<MessageEntity>>

    @Transaction
    @Query("""
        SELECT DISTINCT m.* FROM messages m
        INNER JOIN message_tags mt ON m.id = mt.messageId
        WHERE mt.tagId IN (:tagIds) AND m.isTrashed = 0 AND m.isArchived = 0
        ORDER BY m.timestamp DESC
    """)
    fun pagingMessagesByTags(tagIds: List<Long>): PagingSource<Int, MessageWithTagsRelation>

    @Transaction
    @Query("""
        SELECT * FROM messages
        WHERE isTrashed = 0
        ORDER BY timestamp DESC
    """)
    fun pagingAllMessages(): PagingSource<Int, MessageWithTagsRelation>

    @Transaction
    @Query("""
        SELECT * FROM messages
        WHERE isArchived = 1 AND isTrashed = 0
        ORDER BY timestamp DESC
    """)
    fun pagingArchivedMessages(): PagingSource<Int, MessageWithTagsRelation>

    @Transaction
    @Query("""
        SELECT * FROM messages
        WHERE isTrashed = 1
        ORDER BY timestamp DESC
    """)
    fun pagingTrashedMessages(): PagingSource<Int, MessageWithTagsRelation>

    @Transaction
    @Query("""
        SELECT * FROM messages
        WHERE isTrashed = 0 AND isArchived = 0
        AND (sender LIKE '%' || :query || '%' 
            OR senderName LIKE '%' || :query || '%' 
            OR body LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
    """)
    fun searchMessagesWithTags(query: String): Flow<List<MessageWithTagsRelation>>

    @Transaction
    @Query("""
        SELECT * FROM messages
        WHERE isTrashed = 0 AND isArchived = 0
        AND sender IN (:senders)
        ORDER BY timestamp DESC
    """)
    fun getMessagesBySenders(senders: List<String>): Flow<List<MessageWithTagsRelation>>

    @Query("SELECT * FROM messages WHERE isArchived = 1 AND isTrashed = 0 ORDER BY timestamp DESC")
    fun getArchivedMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isTrashed = 1 ORDER BY timestamp DESC")
    fun getTrashedMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>): List<Long>

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET isArchived = :isArchived WHERE id = :messageId")
    suspend fun setArchived(messageId: Long, isArchived: Boolean)

    @Query("UPDATE messages SET isTrashed = :isTrashed WHERE id = :messageId")
    suspend fun setTrashed(messageId: Long, isTrashed: Boolean)

    @Query("UPDATE messages SET isArchived = :isArchived WHERE id IN (:messageIds)")
    suspend fun setArchivedBulk(messageIds: List<Long>, isArchived: Boolean)

    @Query("UPDATE messages SET isTrashed = :isTrashed WHERE id IN (:messageIds)")
    suspend fun setTrashedBulk(messageIds: List<Long>, isTrashed: Boolean)

    @Query("DELETE FROM messages WHERE id IN (:messageIds)")
    suspend fun deleteMessages(messageIds: List<Long>)

    @Query("DELETE FROM messages WHERE isTrashed = 1")
    suspend fun deleteAllTrashed()

    @Query("UPDATE messages SET isRead = :isRead WHERE id = :messageId")
    suspend fun setRead(messageId: Long, isRead: Boolean)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM messages WHERE id NOT IN (SELECT MIN(id) FROM messages GROUP BY sender, body, timestamp)")
    suspend fun deleteDuplicateMessages()

    @Query(
        """
        DELETE FROM messages
        WHERE deviceMessageId IS NULL
        AND EXISTS (
            SELECT 1 FROM messages twin
            WHERE twin.deviceMessageId IS NOT NULL
            AND twin.body = messages.body
            AND ABS(twin.timestamp - messages.timestamp) <= :windowMs
        )
        """
    )
    suspend fun deleteUnlinkedDuplicates(windowMs: Long)

    @Query("SELECT deviceMessageId FROM messages WHERE deviceMessageId IS NOT NULL")
    suspend fun getKnownDeviceMessageIds(): List<Long>

    @Query("SELECT id FROM messages WHERE sender = :sender AND body = :body AND timestamp = :timestamp LIMIT 1")
    suspend fun findMessageIdByContent(sender: String, body: String, timestamp: Long): Long?

    @Query(
        """
        SELECT id, sender FROM messages
        WHERE deviceMessageId IS NULL AND body = :body
        AND timestamp BETWEEN :minTimestamp AND :maxTimestamp
        ORDER BY ABS(timestamp - :timestamp) ASC
        """
    )
    suspend fun findUnlinkedCandidates(
        body: String,
        timestamp: Long,
        minTimestamp: Long,
        maxTimestamp: Long
    ): List<UnlinkedCandidate>

    @Query("UPDATE messages SET deviceMessageId = :deviceMessageId WHERE id = :messageId")
    suspend fun setDeviceMessageId(messageId: Long, deviceMessageId: Long)

    @Query("UPDATE messages SET deviceMessageId = :deviceMessageId, sender = :sender, timestamp = :timestamp WHERE id = :messageId")
    suspend fun reconcileImported(messageId: Long, deviceMessageId: Long, sender: String, timestamp: Long)

    @Query("SELECT * FROM messages")
    suspend fun getAllMessagesOnce(): List<MessageEntity>

    @Query("SELECT * FROM message_tags")
    suspend fun getAllMessageTags(): List<MessageTagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageTag(messageTag: MessageTagEntity)

    @Query("DELETE FROM message_tags WHERE messageId = :messageId AND tagId = :tagId")
    suspend fun removeMessageTag(messageId: Long, tagId: Long)

    @Query("DELETE FROM message_tags WHERE messageId = :messageId")
    suspend fun clearTagsForMessage(messageId: Long)

    @Transaction
    suspend fun replaceMessageTags(messageId: Long, tags: List<MessageTagEntity>) {
        clearTagsForMessage(messageId)
        for (tag in tags) {
            insertMessageTag(tag)
        }
    }

    @Query("SELECT tagId FROM message_tags WHERE messageId = :messageId")
    suspend fun getTagIdsForMessage(messageId: Long): List<Long>

    @Query("SELECT tagId FROM message_tags WHERE messageId = :messageId")
    fun observeTagIdsForMessage(messageId: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int
}

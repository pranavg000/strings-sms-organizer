package com.strings.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.strings.app.data.local.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class TagMessageCount(
    val tagId: Long,
    val count: Int
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY sortOrder ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC")
    suspend fun getAllTagsList(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE parentTagId IS NULL ORDER BY sortOrder ASC")
    fun getTopLevelTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE parentTagId = :parentId ORDER BY sortOrder ASC")
    fun getChildTags(parentId: Long): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>): List<Long>

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Query("DELETE FROM message_tags WHERE tagId = :tagId")
    suspend fun removeTagFromAllMessages(tagId: Long)

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Query("""
        WITH RECURSIVE descendants(id) AS (
            SELECT id FROM tags WHERE id = :parentTagId
            UNION ALL
            SELECT t.id FROM tags t INNER JOIN descendants d ON t.parentTagId = d.id
        )
        SELECT id FROM descendants
    """)
    suspend fun getDescendantTagIds(parentTagId: Long): List<Long>

    @Query("""
        SELECT mt.tagId AS tagId, COUNT(DISTINCT mt.messageId) AS count
        FROM message_tags mt
        INNER JOIN messages m ON m.id = mt.messageId
        WHERE m.isTrashed = 0
        GROUP BY mt.tagId
    """)
    fun getTagMessageCounts(): Flow<List<TagMessageCount>>
}

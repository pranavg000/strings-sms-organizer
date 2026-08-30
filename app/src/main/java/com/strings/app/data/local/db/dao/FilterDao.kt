package com.strings.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.strings.app.data.local.db.entity.FilterActionEntity
import com.strings.app.data.local.db.entity.FilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {
    @Query("SELECT * FROM filters ORDER BY priority ASC")
    fun getAllFilters(): Flow<List<FilterEntity>>

    @Query("SELECT * FROM filters WHERE isEnabled = 1 ORDER BY priority ASC")
    suspend fun getEnabledFilters(): List<FilterEntity>

    @Query("SELECT * FROM filters WHERE id = :id")
    suspend fun getFilterById(id: Long): FilterEntity?

    @Query("SELECT * FROM filter_actions WHERE filterId = :filterId")
    suspend fun getActionsForFilter(filterId: Long): List<FilterActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilter(filter: FilterEntity): Long

    @Update
    suspend fun updateFilter(filter: FilterEntity)

    @Query("DELETE FROM filters WHERE id = :id")
    suspend fun deleteFilter(id: Long)

    @Query("DELETE FROM filters")
    suspend fun deleteAllFilters()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: FilterActionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<FilterActionEntity>)

    @Query("DELETE FROM filter_actions WHERE filterId = :filterId")
    suspend fun deleteActionsForFilter(filterId: Long)

    @Query("UPDATE filters SET isEnabled = :isEnabled WHERE id = :filterId")
    suspend fun setEnabled(filterId: Long, isEnabled: Boolean)

    @Query("UPDATE filters SET priority = :priority WHERE id = :filterId")
    suspend fun updatePriority(filterId: Long, priority: Int)

    @Query("SELECT COALESCE(MAX(priority), -1) FROM filters")
    suspend fun getMaxPriority(): Int

    @Query("""
        SELECT DISTINCT f.name FROM filters f
        INNER JOIN filter_actions fa ON f.id = fa.filterId
        WHERE fa.targetTagId = :tagId
        ORDER BY f.name ASC
    """)
    suspend fun getFilterNamesUsingTag(tagId: Long): List<String>
}

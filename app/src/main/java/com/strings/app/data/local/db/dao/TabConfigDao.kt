package com.strings.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.strings.app.data.local.db.entity.TabConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabConfigDao {
    @Query("SELECT * FROM tab_configs WHERE isVisible = 1 ORDER BY position ASC")
    fun getVisibleTabs(): Flow<List<TabConfigEntity>>

    @Query("SELECT * FROM tab_configs ORDER BY position ASC")
    fun getAllTabs(): Flow<List<TabConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabConfig(tabConfig: TabConfigEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabConfigs(tabConfigs: List<TabConfigEntity>)

    @Update
    suspend fun updateTabConfig(tabConfig: TabConfigEntity)

    @Query("DELETE FROM tab_configs WHERE id = :id")
    suspend fun deleteTabConfig(id: Long)

    @Query("DELETE FROM tab_configs WHERE tagId = :tagId")
    suspend fun deleteTabConfigByTagId(tagId: Long)

    @Query("DELETE FROM tab_configs")
    suspend fun deleteAll()
}

package com.strings.app.domain.repository

import com.strings.app.domain.model.TabConfig
import com.strings.app.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun getAllTags(): Flow<List<Tag>>
    suspend fun getAllTagsList(): List<Tag>
    fun getTopLevelTags(): Flow<List<Tag>>
    fun getChildTags(parentId: Long): Flow<List<Tag>>
    fun getVisibleTabs(): Flow<List<TabConfig>>
    fun getAllTabs(): Flow<List<TabConfig>>
    fun getTagMessageCounts(): Flow<Map<Long, Int>>
    suspend fun getTagById(id: Long): Tag?
    suspend fun getTagByName(name: String): Tag?
    suspend fun insertTag(tag: Tag): Long
    suspend fun updateTag(tag: Tag)
    suspend fun deleteTag(id: Long)
    suspend fun getDescendantTagIds(parentTagId: Long): List<Long>
    suspend fun insertTabConfig(tabConfig: TabConfig): Long
    suspend fun updateTabConfig(tabConfig: TabConfig)
    suspend fun deleteTabConfig(id: Long)
    suspend fun deleteTabConfigByTagId(tagId: Long)
    suspend fun replaceAllTabs(tabs: List<TabConfig>)
}

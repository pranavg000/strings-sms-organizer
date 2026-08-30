package com.strings.app.data.repository

import com.strings.app.data.local.db.dao.TabConfigDao
import com.strings.app.data.local.db.dao.TagDao
import com.strings.app.data.local.db.entity.TabConfigEntity
import com.strings.app.data.local.db.entity.TagEntity
import com.strings.app.domain.model.TabConfig
import com.strings.app.domain.model.Tag
import com.strings.app.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepositoryImpl(
    private val tagDao: TagDao
) : TagRepository {
    private var tabConfigDao: TabConfigDao? = null

    constructor(tagDao: TagDao, tabConfigDao: TabConfigDao) : this(tagDao) {
        this.tabConfigDao = tabConfigDao
    }

    override fun getAllTags(): Flow<List<Tag>> {
        return tagDao.getAllTags().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getAllTagsList(): List<Tag> {
        return tagDao.getAllTagsList().map { it.toDomain() }
    }

    override fun getTopLevelTags(): Flow<List<Tag>> {
        return tagDao.getTopLevelTags().map { entities -> entities.map { it.toDomain() } }
    }

    override fun getChildTags(parentId: Long): Flow<List<Tag>> {
        return tagDao.getChildTags(parentId).map { entities -> entities.map { it.toDomain() } }
    }

    override fun getVisibleTabs(): Flow<List<TabConfig>> {
        return requireTabConfigDao().getVisibleTabs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllTabs(): Flow<List<TabConfig>> {
        return requireTabConfigDao().getAllTabs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTagMessageCounts(): Flow<Map<Long, Int>> {
        return tagDao.getTagMessageCounts().map { rows ->
            rows.associate { it.tagId to it.count }
        }
    }

    override suspend fun getTagById(id: Long): Tag? {
        return tagDao.getTagById(id)?.toDomain()
    }

    override suspend fun getTagByName(name: String): Tag? {
        return tagDao.getTagByName(name)?.toDomain()
    }

    override suspend fun insertTag(tag: Tag): Long {
        return tagDao.insertTag(tag.toEntity())
    }

    override suspend fun updateTag(tag: Tag) {
        tagDao.updateTag(tag.toEntity())
    }

    override suspend fun deleteTag(id: Long) {
        tagDao.removeTagFromAllMessages(id)
        tagDao.deleteTag(id)
    }

    override suspend fun getDescendantTagIds(parentTagId: Long): List<Long> {
        return tagDao.getDescendantTagIds(parentTagId)
    }

    override suspend fun insertTabConfig(tabConfig: TabConfig): Long {
        return requireTabConfigDao().insertTabConfig(tabConfig.toEntity())
    }

    override suspend fun updateTabConfig(tabConfig: TabConfig) {
        requireTabConfigDao().updateTabConfig(tabConfig.toEntity())
    }

    override suspend fun deleteTabConfig(id: Long) {
        requireTabConfigDao().deleteTabConfig(id)
    }

    override suspend fun deleteTabConfigByTagId(tagId: Long) {
        requireTabConfigDao().deleteTabConfigByTagId(tagId)
    }

    override suspend fun replaceAllTabs(tabs: List<TabConfig>) {
        val dao = requireTabConfigDao()
        dao.deleteAll()
        dao.insertTabConfigs(tabs.map { it.toEntity() })
    }

    private fun requireTabConfigDao(): TabConfigDao {
        return tabConfigDao ?: throw IllegalStateException("TabConfigDao not initialized")
    }

    private fun TagEntity.toDomain(): Tag = Tag(
        id = id,
        name = name,
        color = color,
        icon = icon,
        parentTagId = parentTagId,
        sortOrder = sortOrder,
        isSystemTag = isSystemTag
    )

    private fun Tag.toEntity(): TagEntity = TagEntity(
        id = id,
        name = name,
        color = color,
        icon = icon,
        parentTagId = parentTagId,
        sortOrder = sortOrder,
        isSystemTag = isSystemTag
    )

    private fun TabConfigEntity.toDomain(): TabConfig = TabConfig(
        id = id,
        tagId = tagId,
        position = position,
        isVisible = isVisible
    )

    private fun TabConfig.toEntity(): TabConfigEntity = TabConfigEntity(
        id = id,
        tagId = tagId,
        position = position,
        isVisible = isVisible
    )
}

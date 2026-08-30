package com.strings.app.data.repository

import com.strings.app.data.local.db.dao.FilterDao
import com.strings.app.data.local.db.entity.FilterActionEntity
import com.strings.app.data.local.db.entity.FilterEntity
import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.ConditionGroup
import com.strings.app.domain.model.ConditionNode
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.FilterAction
import com.strings.app.domain.repository.FilterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FilterRepositoryImpl(
    private val filterDao: FilterDao,
    private val json: Json
) : FilterRepository {
    override fun getAllFilters(): Flow<List<Filter>> {
        return filterDao.getAllFilters().map { entities ->
            entities.map { entity -> assembleFilter(entity) }
        }
    }

    override suspend fun getEnabledFilters(): List<Filter> {
        return filterDao.getEnabledFilters().map { assembleFilter(it) }
    }

    override suspend fun getFilterById(id: Long): Filter? {
        val entity = filterDao.getFilterById(id) ?: return null
        return assembleFilter(entity)
    }

    override suspend fun insertFilter(filter: Filter): Long {
        val filterId = filterDao.insertFilter(filter.toEntity())
        filterDao.insertActions(filter.actions.map { it.toEntity(filterId) })
        return filterId
    }

    override suspend fun updateFilter(filter: Filter) {
        filterDao.updateFilter(filter.toEntity())
        filterDao.deleteActionsForFilter(filter.id)
        filterDao.insertActions(filter.actions.map { it.toEntity(filter.id) })
    }

    override suspend fun deleteFilter(id: Long) {
        filterDao.deleteFilter(id)
    }

    override suspend fun setEnabled(filterId: Long, isEnabled: Boolean) {
        filterDao.setEnabled(filterId, isEnabled)
    }

    override suspend fun getFilterNamesUsingTag(tagId: Long): List<String> {
        return filterDao.getFilterNamesUsingTag(tagId)
    }

    override suspend fun getMaxPriority(): Int {
        return filterDao.getMaxPriority()
    }

    override suspend fun setFilterOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            filterDao.updatePriority(id, index)
        }
    }

    private suspend fun assembleFilter(entity: FilterEntity): Filter {
        val actions = filterDao.getActionsForFilter(entity.id)
        return Filter(
            id = entity.id,
            name = entity.name,
            priority = entity.priority,
            isEnabled = entity.isEnabled,
            createdAt = entity.createdAt,
            root = json.decodeFromString<ConditionGroup>(entity.conditionTree),
            actions = actions.map { it.toDomain() }
        )
    }

    private fun Filter.toEntity(): FilterEntity = FilterEntity(
        id = id,
        name = name,
        priority = priority,
        isEnabled = isEnabled,
        createdAt = createdAt,
        conditionTree = json.encodeToString<ConditionNode>(root)
    )

    private fun FilterAction.toEntity(filterId: Long): FilterActionEntity = FilterActionEntity(
        id = id,
        filterId = filterId,
        actionType = actionType.name,
        targetTagId = targetTagId
    )

    private fun FilterActionEntity.toDomain(): FilterAction = FilterAction(
        id = id,
        filterId = filterId,
        actionType = ActionType.valueOf(actionType),
        targetTagId = targetTagId
    )
}

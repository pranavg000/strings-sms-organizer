package com.strings.app.domain.repository

import com.strings.app.domain.model.Filter
import kotlinx.coroutines.flow.Flow

interface FilterRepository {
    fun getAllFilters(): Flow<List<Filter>>
    suspend fun getEnabledFilters(): List<Filter>
    suspend fun getFilterById(id: Long): Filter?
    suspend fun insertFilter(filter: Filter): Long
    suspend fun updateFilter(filter: Filter)
    suspend fun deleteFilter(id: Long)
    suspend fun setEnabled(filterId: Long, isEnabled: Boolean)
    suspend fun getFilterNamesUsingTag(tagId: Long): List<String>
    suspend fun getMaxPriority(): Int
    suspend fun setFilterOrder(orderedIds: List<Long>)
}

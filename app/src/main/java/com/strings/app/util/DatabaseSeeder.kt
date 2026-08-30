package com.strings.app.util

import com.strings.app.data.local.db.dao.FilterDao
import com.strings.app.data.local.db.dao.MessageDao
import com.strings.app.data.local.db.dao.TabConfigDao
import com.strings.app.data.local.db.dao.TagDao
import com.strings.app.data.local.db.entity.TabConfigEntity
import com.strings.app.data.local.db.entity.TagEntity
import com.strings.app.data.prefs.SettingsDataStore
import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.ConditionField
import com.strings.app.domain.model.ConditionGroup
import com.strings.app.domain.model.ConditionLeaf
import com.strings.app.domain.model.ConditionOperator
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.FilterAction
import com.strings.app.domain.model.LogicGroup
import com.strings.app.domain.repository.FilterRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DatabaseSeeder(
    private val messageDao: MessageDao,
    private val tagDao: TagDao,
    private val tabConfigDao: TabConfigDao,
    private val filterDao: FilterDao,
    private val filterRepository: FilterRepository,
    private val settings: SettingsDataStore
) {
    private val mutex = Mutex()

    suspend fun setupFirstRun() {
        mutex.withLock {
            if (settings.isFirstRunCompleted()) return
            clearAllData()
            val inboxTagId = tagDao.insertTag(
                TagEntity(
                    name = SystemTags.INBOX_NAME,
                    color = "",
                    icon = SystemTags.INBOX_ICON,
                    sortOrder = 0,
                    isSystemTag = true
                )
            )
            tabConfigDao.insertTabConfig(
                TabConfigEntity(tagId = inboxTagId, position = 0, isVisible = true)
            )
            settings.setInboxTagId(inboxTagId)
            val otpTagId = tagDao.insertTag(
                TagEntity(
                    name = SystemTags.OTP_NAME,
                    color = "",
                    icon = SystemTags.OTP_ICON,
                    sortOrder = 1,
                    isSystemTag = true
                )
            )
            settings.setOtpTagId(otpTagId)
            seedExampleTemplate()
            settings.setFirstRunCompleted(true)
        }
    }

    /**
     * Seeds a small, clearly named example template on first run so new users
     * can see how tags, tabs, hierarchy, and filters fit together. The filter
     * is disabled by default, so nothing happens until the user opts in.
     */
    private suspend fun seedExampleTemplate() {
        val shoppingTagId: Long = tagDao.insertTag(
            TagEntity(
                name = EXAMPLE_SHOPPING_TAG_NAME,
                color = "",
                icon = "shopping_cart",
                sortOrder = 2
            )
        )
        tabConfigDao.insertTabConfig(
            TabConfigEntity(tagId = shoppingTagId, position = 1, isVisible = true)
        )
        val ordersTagId: Long = tagDao.insertTag(
            TagEntity(
                name = EXAMPLE_ORDERS_TAG_NAME,
                color = "",
                icon = "receipt",
                parentTagId = shoppingTagId,
                sortOrder = 3
            )
        )
        filterRepository.insertFilter(
            Filter(
                name = EXAMPLE_FILTER_NAME,
                priority = 0,
                isEnabled = false,
                root = ConditionGroup(
                    logic = LogicGroup.AND,
                    children = listOf(
                        ConditionLeaf(
                            field = ConditionField.BODY,
                            operator = ConditionOperator.CONTAINS,
                            value = "order"
                        ),
                        ConditionGroup(
                            logic = LogicGroup.OR,
                            children = listOf(
                                ConditionLeaf(
                                    field = ConditionField.SENDER,
                                    operator = ConditionOperator.CONTAINS,
                                    value = "AMZN"
                                ),
                                ConditionLeaf(
                                    field = ConditionField.SENDER,
                                    operator = ConditionOperator.CONTAINS,
                                    value = "FLPKRT"
                                )
                            )
                        )
                    )
                ),
                actions = listOf(
                    FilterAction(actionType = ActionType.ASSIGN_TAG, targetTagId = ordersTagId),
                    FilterAction(actionType = ActionType.MARK_READ)
                )
            )
        )
    }

    suspend fun ensureOtpTagId(): Long = mutex.withLock {
        val storedId: Long = settings.getOtpTagId()
        if (storedId > 0L && tagDao.getTagById(storedId) != null) return@withLock storedId
        val existing: TagEntity? = tagDao.getTagByName(SystemTags.OTP_NAME)
        val resolvedId: Long = existing?.id ?: tagDao.insertTag(
            TagEntity(
                name = SystemTags.OTP_NAME,
                color = "",
                icon = SystemTags.OTP_ICON,
                sortOrder = 1,
                isSystemTag = true
            )
        )
        settings.setOtpTagId(resolvedId)
        resolvedId
    }

    private suspend fun clearAllData() {
        messageDao.deleteAllMessages()
        filterDao.deleteAllFilters()
        tagDao.deleteAllTags()
        tabConfigDao.deleteAll()
    }

    private companion object {
        const val EXAMPLE_SHOPPING_TAG_NAME: String = "Example: Shopping"
        const val EXAMPLE_ORDERS_TAG_NAME: String = "Example: Orders"
        const val EXAMPLE_FILTER_NAME: String = "Example: Order updates"
    }
}

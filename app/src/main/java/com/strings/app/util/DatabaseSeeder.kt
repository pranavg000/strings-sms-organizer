package com.strings.app.util

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

/**
 * Keeps the system tags (Inbox, OTP) and the first-run example template in place.
 *
 * Seeding is keyed on DATABASE state, never on the DataStore flag alone: every system tag
 * is resolved by its stored id, then by name, and inserted only when genuinely absent.
 * The DataStore and the Room DB are separate files that can fall out of sync (a truncated
 * preferences file, a partial auto-backup restore), so this never deletes anything --
 * a missing flag on a populated DB must not wipe the user's data, and a stale id on a
 * fresh DB must not leave the Inbox tag dangling.
 */
class DatabaseSeeder(
    private val tagDao: TagDao,
    private val tabConfigDao: TabConfigDao,
    private val filterRepository: FilterRepository,
    private val settings: SettingsDataStore
) {
    private val mutex = Mutex()

    private data class ResolvedTag(val id: Long, val created: Boolean)

    suspend fun setupFirstRun() {
        mutex.withLock {
            val inbox: ResolvedTag = resolveInboxTag()
            if (inbox.created) {
                tabConfigDao.insertTabConfig(
                    TabConfigEntity(tagId = inbox.id, position = 0, isVisible = true)
                )
            }
            resolveOtpTag()
            if (!settings.isFirstRunCompleted()) {
                if (tagDao.getTagByName(EXAMPLE_SHOPPING_TAG_NAME) == null) {
                    seedExampleTemplate()
                }
                settings.setFirstRunCompleted(true)
            }
        }
    }

    /** Resolves the Inbox tag, recreating it if it has gone missing, and returns its id. */
    suspend fun ensureInboxTagId(): Long = mutex.withLock { resolveInboxTag().id }

    /** Resolves the OTP tag, recreating it if it has gone missing, and returns its id. */
    suspend fun ensureOtpTagId(): Long = mutex.withLock { resolveOtpTag().id }

    private suspend fun resolveInboxTag(): ResolvedTag {
        val storedId: Long = settings.getInboxTagId()
        val resolved: ResolvedTag = resolveSystemTag(
            name = SystemTags.INBOX_NAME,
            icon = SystemTags.INBOX_ICON,
            sortOrder = INBOX_SORT_ORDER,
            storedId = storedId
        )
        if (resolved.id != storedId) settings.setInboxTagId(resolved.id)
        return resolved
    }

    private suspend fun resolveOtpTag(): ResolvedTag {
        val storedId: Long = settings.getOtpTagId()
        val resolved: ResolvedTag = resolveSystemTag(
            name = SystemTags.OTP_NAME,
            icon = SystemTags.OTP_ICON,
            sortOrder = OTP_SORT_ORDER,
            storedId = storedId
        )
        if (resolved.id != storedId) settings.setOtpTagId(resolved.id)
        return resolved
    }

    /** Stored id -> existing tag by name -> fresh insert. Caller must hold [mutex]. */
    private suspend fun resolveSystemTag(
        name: String,
        icon: String,
        sortOrder: Int,
        storedId: Long
    ): ResolvedTag {
        if (storedId > 0L && tagDao.getTagById(storedId) != null) {
            return ResolvedTag(id = storedId, created = false)
        }
        val existing: TagEntity? = tagDao.getTagByName(name)
        if (existing != null) return ResolvedTag(id = existing.id, created = false)
        val newId: Long = tagDao.insertTag(
            TagEntity(
                name = name,
                color = "",
                icon = icon,
                sortOrder = sortOrder,
                isSystemTag = true
            )
        )
        return ResolvedTag(id = newId, created = true)
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

    private companion object {
        const val INBOX_SORT_ORDER: Int = 0
        const val OTP_SORT_ORDER: Int = 1
        const val EXAMPLE_SHOPPING_TAG_NAME: String = "Example: Shopping"
        const val EXAMPLE_ORDERS_TAG_NAME: String = "Example: Orders"
        const val EXAMPLE_FILTER_NAME: String = "Example: Order updates"
    }
}

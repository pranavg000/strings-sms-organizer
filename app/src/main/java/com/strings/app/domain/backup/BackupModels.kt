package com.strings.app.domain.backup

import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.ConditionGroup
import kotlinx.serialization.Serializable

// Version history:
// 1 - initial format (tags + filters)
// 2 - new ActionType values (NOTIFY_SILENTLY, STOP_PROCESSING); older app
//     versions can't parse them, so their import guard must reject v2 bundles
// 3 - full backup: tab configs, per-message state (read/archive/trash + tag
//     assignments + balance overrides, keyed by deviceMessageId with a
//     sender+timestamp fallback), and app settings. v1/v2 bundles still
//     import (new sections default to empty).
// 4 - user-configured accounts (bank code + tail + type + name + color +
//     parent link by bankCode/tail + enabled flag). Older bundles still
//     import (accounts default to empty).
const val BACKUP_VERSION: Int = 4

@Serializable
data class BackupBundle(
    val version: Int = BACKUP_VERSION,
    val tags: List<TagDto> = emptyList(),
    val filters: List<FilterDto> = emptyList(),
    val tabs: List<TabConfigDto> = emptyList(),
    val accounts: List<AccountDto> = emptyList(),
    val messageStates: List<MessageStateDto> = emptyList(),
    val settings: SettingsDto? = null
)

/**
 * One user-configured account. Cross-device ids differ, so the parent link references the
 * parent by its (bankCode, accountTail) pair. [tagName] preserves a bankName that differs
 * from the display name (defaults to [name] when equal).
 */
@Serializable
data class AccountDto(
    val bankCode: String,
    val accountTail: String,
    val accountType: String,
    val name: String,
    val tagName: String? = null,
    val colorIndex: Int = -1,
    val parentBankCode: String? = null,
    val parentAccountTail: String? = null,
    val isEnabled: Boolean = true
)

@Serializable
data class TagDto(
    val name: String,
    val color: String,
    val icon: String,
    val parentName: String? = null,
    val sortOrder: Int = 0,
    val isSystemTag: Boolean = false
)

@Serializable
data class FilterDto(
    val name: String,
    val priority: Int = 0,
    val isEnabled: Boolean = true,
    val root: ConditionGroup = ConditionGroup(),
    val actions: List<FilterActionDto> = emptyList()
)

@Serializable
data class FilterActionDto(
    val actionType: ActionType,
    val targetTagName: String? = null
)

@Serializable
data class TabConfigDto(
    val tagName: String,
    val position: Int,
    val isVisible: Boolean = true
)

@Serializable
data class MessageStateDto(
    val deviceMessageId: Long? = null,
    val sender: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val tagNames: List<String> = emptyList(),
    val balanceAfter: Double? = null
)

@Serializable
data class SettingsDto(
    val themeMode: String,
    val appLockEnabled: Boolean = false
)

data class ImportResult(
    val tagsAdded: Int = 0,
    val filtersAdded: Int = 0,
    val filtersSkipped: Int = 0,
    val tabsRestored: Int = 0,
    val accountsAdded: Int = 0,
    val messagesRestored: Int = 0,
    val messagesUnmatched: Int = 0,
    val balancesRestored: Int = 0
)

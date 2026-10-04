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
// 5 - per-message "not a transaction" override (MessageStateDto.isTransactionExcluded).
//     Older bundles still import (defaults to false).
// 6 - per-message description (MessageStateDto.description) and user-linked
//     transactions (MessageStateDto.linkedTransactions: sentinels the user attached
//     to a message by hand; not re-derivable, so amount/type/account travel along).
//     Older bundles still import (both default to empty).
// 7 - MessageStateDto.bodyHash (SHA-256 prefix of the body) so message states match by
//     content on a new device; deviceMessageId is per-device and only breaks ties now.
//     Older bundles still import (null hash -> sender+timestamp, then id with a sender check).
const val BACKUP_VERSION: Int = 7

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
    val bodyHash: String? = null,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val tagNames: List<String> = emptyList(),
    val balanceAfter: Double? = null,
    val isTransactionExcluded: Boolean = false,
    val description: String? = null,
    val linkedTransactions: List<LinkedTransactionDto> = emptyList()
)

/**
 * A transaction the user linked to the message by hand (a converted sentinel). The account is
 * referenced by (bankCode, accountTail) because ids differ across devices.
 */
@Serializable
data class LinkedTransactionDto(
    val bankCode: String,
    val accountTail: String,
    val amount: Double,
    val type: String,
    val balanceAfter: Double? = null,
    val rawMatch: String = ""
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
    val balancesRestored: Int = 0,
    val linkedTransactionsRestored: Int = 0
)

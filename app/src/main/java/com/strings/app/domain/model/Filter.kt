package com.strings.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Filter(
    val id: Long = 0L,
    val name: String,
    val priority: Int = 0,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val root: ConditionGroup = ConditionGroup(),
    val actions: List<FilterAction> = emptyList()
)

@Serializable
sealed interface ConditionNode

@Serializable
@SerialName("leaf")
data class ConditionLeaf(
    val field: ConditionField,
    val operator: ConditionOperator,
    val value: String
) : ConditionNode

@Serializable
@SerialName("group")
data class ConditionGroup(
    val logic: LogicGroup = LogicGroup.AND,
    val children: List<ConditionNode> = emptyList()
) : ConditionNode

data class FilterAction(
    val id: Long = 0L,
    val filterId: Long = 0L,
    val actionType: ActionType,
    val targetTagId: Long? = null
)

@Serializable
enum class ConditionField {
    SENDER, BODY, SENDER_NAME
}

@Serializable
enum class ConditionOperator {
    CONTAINS, EQUALS, MATCHES_REGEX, STARTS_WITH
}

@Serializable
enum class LogicGroup {
    AND, OR
}

@Serializable
enum class ActionType {
    ASSIGN_TAG,
    REMOVE_FROM_INBOX,
    ARCHIVE,
    TRASH,
    MARK_READ,
    SUPPRESS_NOTIFICATION,
    NOTIFY_SILENTLY,
    STOP_PROCESSING
}

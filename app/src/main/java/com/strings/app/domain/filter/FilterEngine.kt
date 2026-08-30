package com.strings.app.domain.filter

import com.strings.app.domain.model.ConditionField
import com.strings.app.domain.model.ConditionGroup
import com.strings.app.domain.model.ConditionLeaf
import com.strings.app.domain.model.ConditionNode
import com.strings.app.domain.model.ConditionOperator
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.LogicGroup
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.prune

class FilterEngine {
    fun matches(filter: Filter, message: Message): Boolean {
        val root: ConditionGroup = filter.root.prune()
        if (root.children.isEmpty()) return false
        return evaluate(root, message)
    }

    private fun evaluate(node: ConditionNode, message: Message): Boolean = when (node) {
        is ConditionLeaf -> evaluateLeaf(node, message)
        is ConditionGroup ->
            if (node.children.isEmpty()) false
            else when (node.logic) {
                LogicGroup.AND -> node.children.all { evaluate(it, message) }
                LogicGroup.OR -> node.children.any { evaluate(it, message) }
            }
    }

    private fun evaluateLeaf(leaf: ConditionLeaf, message: Message): Boolean {
        val fieldValue: String = when (leaf.field) {
            ConditionField.SENDER -> message.sender
            ConditionField.BODY -> message.body
            ConditionField.SENDER_NAME -> message.senderName
        }
        return when (leaf.operator) {
            ConditionOperator.CONTAINS -> fieldValue.contains(leaf.value, ignoreCase = true)
            ConditionOperator.EQUALS -> fieldValue.equals(leaf.value, ignoreCase = true)
            ConditionOperator.STARTS_WITH -> fieldValue.startsWith(leaf.value, ignoreCase = true)
            ConditionOperator.MATCHES_REGEX -> {
                try {
                    Regex(leaf.value, RegexOption.IGNORE_CASE).containsMatchIn(fieldValue)
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    fun findMatchingFilters(filters: List<Filter>, message: Message): List<Filter> {
        return filters.filter { it.isEnabled && matches(it, message) }
    }
}

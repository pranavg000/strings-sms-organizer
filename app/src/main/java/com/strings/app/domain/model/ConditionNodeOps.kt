package com.strings.app.domain.model

fun ConditionNode.hasLeaf(): Boolean = when (this) {
    is ConditionLeaf -> true
    is ConditionGroup -> children.any { it.hasLeaf() }
}

fun ConditionGroup.prune(): ConditionGroup {
    val prunedChildren: List<ConditionNode> = children.mapNotNull { child ->
        when (child) {
            is ConditionLeaf -> child
            is ConditionGroup -> {
                val pruned: ConditionGroup = child.prune()
                if (pruned.children.isEmpty()) null else pruned
            }
        }
    }
    return copy(children = prunedChildren)
}

fun ConditionGroup.addChild(node: ConditionNode): ConditionGroup =
    copy(children = children + node)

fun ConditionGroup.replaceChildAt(index: Int, node: ConditionNode): ConditionGroup {
    if (index !in children.indices) return this
    val updated: MutableList<ConditionNode> = children.toMutableList()
    updated[index] = node
    return copy(children = updated)
}

fun ConditionGroup.removeChildAt(index: Int): ConditionGroup {
    if (index !in children.indices) return this
    val updated: MutableList<ConditionNode> = children.toMutableList()
    updated.removeAt(index)
    return copy(children = updated)
}

fun ConditionNode.summary(): String = when (this) {
    is ConditionLeaf -> "${fieldLabel(field)} ${operatorLabel(operator)} \"$value\""
    is ConditionGroup -> {
        val separator: String = " ${logic.name} "
        val parts: List<String> = children.map { child ->
            when (child) {
                is ConditionLeaf -> child.summary()
                is ConditionGroup -> "(${child.summary()})"
            }
        }
        parts.joinToString(separator)
    }
}

private fun fieldLabel(field: ConditionField): String =
    field.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun operatorLabel(operator: ConditionOperator): String =
    operator.name.lowercase().replace('_', ' ')

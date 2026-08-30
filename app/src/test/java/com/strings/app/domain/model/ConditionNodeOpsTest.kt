package com.strings.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionNodeOpsTest {
    private fun leaf(value: String): ConditionLeaf =
        ConditionLeaf(ConditionField.BODY, ConditionOperator.CONTAINS, value)

    @Test
    fun `hasLeaf is false for empty group and nested empties`() {
        assertFalse(ConditionGroup().hasLeaf())
        assertFalse(ConditionGroup(LogicGroup.OR, listOf(ConditionGroup())).hasLeaf())
    }

    @Test
    fun `hasLeaf is true when any descendant is a leaf`() {
        val tree = ConditionGroup(
            LogicGroup.OR,
            listOf(ConditionGroup(LogicGroup.AND, listOf(leaf("x"))))
        )
        assertTrue(tree.hasLeaf())
    }

    @Test
    fun `prune drops empty groups recursively`() {
        val tree = ConditionGroup(
            LogicGroup.AND,
            listOf(
                leaf("x"),
                ConditionGroup(LogicGroup.OR, emptyList()),
                ConditionGroup(LogicGroup.AND, listOf(ConditionGroup(LogicGroup.OR, emptyList())))
            )
        )
        val pruned = tree.prune()
        assertEquals(1, pruned.children.size)
        assertEquals(leaf("x"), pruned.children.first())
    }

    @Test
    fun `addChild appends immutably`() {
        val group = ConditionGroup(LogicGroup.AND, listOf(leaf("a")))
        val updated = group.addChild(leaf("b"))
        assertEquals(1, group.children.size)
        assertEquals(2, updated.children.size)
    }

    @Test
    fun `replaceChildAt replaces the right index`() {
        val group = ConditionGroup(LogicGroup.AND, listOf(leaf("a"), leaf("b")))
        val updated = group.replaceChildAt(1, leaf("c"))
        assertEquals(leaf("c"), updated.children[1])
        assertEquals(leaf("a"), updated.children[0])
    }

    @Test
    fun `removeChildAt removes the right index`() {
        val group = ConditionGroup(LogicGroup.AND, listOf(leaf("a"), leaf("b")))
        val updated = group.removeChildAt(0)
        assertEquals(1, updated.children.size)
        assertEquals(leaf("b"), updated.children.first())
    }

    @Test
    fun `out of bounds operations are no-ops`() {
        val group = ConditionGroup(LogicGroup.AND, listOf(leaf("a")))
        assertEquals(group, group.replaceChildAt(5, leaf("z")))
        assertEquals(group, group.removeChildAt(5))
    }

    @Test
    fun `summary renders parenthesized nested expression`() {
        val tree = ConditionGroup(
            LogicGroup.OR,
            listOf(
                ConditionGroup(
                    LogicGroup.AND,
                    listOf(
                        ConditionLeaf(ConditionField.BODY, ConditionOperator.CONTAINS, "x"),
                        ConditionLeaf(ConditionField.BODY, ConditionOperator.CONTAINS, "y")
                    )
                ),
                ConditionLeaf(ConditionField.SENDER, ConditionOperator.CONTAINS, "z")
            )
        )
        assertEquals("(Body contains \"x\" AND Body contains \"y\") OR Sender contains \"z\"", tree.summary())
    }
}

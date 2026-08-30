package com.strings.app.domain.filter

import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.ConditionField
import com.strings.app.domain.model.ConditionGroup
import com.strings.app.domain.model.ConditionLeaf
import com.strings.app.domain.model.ConditionOperator
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.FilterAction
import com.strings.app.domain.model.LogicGroup
import com.strings.app.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FilterEngineTest {
    private lateinit var engine: FilterEngine
    private val bankMessage = Message(
        id = 1L,
        sender = "HDFCBK",
        senderName = "HDFC Bank",
        body = "Rs 2,500.00 debited from a/c **1234 on 06-Jun-25 at AMAZON. Avl Bal: Rs 45,230.50",
        timestamp = System.currentTimeMillis()
    )
    private val otpMessage = Message(
        id = 2L,
        sender = "VM-HDFCBK",
        senderName = "HDFC Bank",
        body = "Your OTP for transaction of Rs 1,299 on HDFC NetBanking is 847293. Valid for 10 mins.",
        timestamp = System.currentTimeMillis()
    )
    private val promoMessage = Message(
        id = 3L,
        sender = "BZ-ZOMATO",
        senderName = "Zomato",
        body = "Craving something delicious? Get FLAT 60% OFF up to Rs 120 on your next order! Use code YUMMY60.",
        timestamp = System.currentTimeMillis()
    )
    private val personalMessage = Message(
        id = 4L,
        sender = "+919876543210",
        senderName = "Rahul",
        body = "Hey! Are we still meeting at 6pm today?",
        timestamp = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        engine = FilterEngine()
    }

    private fun leaf(
        field: ConditionField,
        operator: ConditionOperator,
        value: String
    ): ConditionLeaf = ConditionLeaf(field = field, operator = operator, value = value)

    private fun filterOf(
        root: ConditionGroup,
        isEnabled: Boolean = true,
        id: Long = 0L
    ): Filter = Filter(
        id = id,
        name = "test",
        isEnabled = isEnabled,
        root = root,
        actions = listOf(FilterAction(actionType = ActionType.ARCHIVE))
    )

    @Test
    fun `contains operator matches case-insensitively`() {
        val filter = filterOf(
            ConditionGroup(LogicGroup.AND, listOf(leaf(ConditionField.BODY, ConditionOperator.CONTAINS, "debited")))
        )
        assertTrue(engine.matches(filter, bankMessage))
        assertFalse(engine.matches(filter, promoMessage))
    }

    @Test
    fun `equals operator matches sender exactly`() {
        val filter = filterOf(
            ConditionGroup(LogicGroup.AND, listOf(leaf(ConditionField.SENDER, ConditionOperator.EQUALS, "HDFCBK")))
        )
        assertTrue(engine.matches(filter, bankMessage))
        assertFalse(engine.matches(filter, promoMessage))
    }

    @Test
    fun `equals is case-insensitive`() {
        val filter = filterOf(
            ConditionGroup(LogicGroup.AND, listOf(leaf(ConditionField.SENDER, ConditionOperator.EQUALS, "hdfcbk")))
        )
        assertTrue(engine.matches(filter, bankMessage))
    }

    @Test
    fun `starts_with matches sender prefix`() {
        val filter = filterOf(
            ConditionGroup(LogicGroup.AND, listOf(leaf(ConditionField.SENDER, ConditionOperator.STARTS_WITH, "VM-")))
        )
        assertTrue(engine.matches(filter, otpMessage))
        assertFalse(engine.matches(filter, bankMessage))
    }

    @Test
    fun `regex matches OTP pattern`() {
        val filter = filterOf(
            ConditionGroup(
                LogicGroup.AND,
                listOf(leaf(ConditionField.BODY, ConditionOperator.MATCHES_REGEX, "\\bOTP\\b.*\\b\\d{4,6}\\b"))
            )
        )
        assertTrue(engine.matches(filter, otpMessage))
        assertFalse(engine.matches(filter, bankMessage))
    }

    @Test
    fun `invalid regex returns false without crashing`() {
        val filter = filterOf(
            ConditionGroup(LogicGroup.AND, listOf(leaf(ConditionField.BODY, ConditionOperator.MATCHES_REGEX, "[invalid(")))
        )
        assertFalse(engine.matches(filter, bankMessage))
    }

    @Test
    fun `AND group all must match`() {
        val filter = filterOf(
            ConditionGroup(
                LogicGroup.AND,
                listOf(
                    leaf(ConditionField.SENDER, ConditionOperator.CONTAINS, "HDFC"),
                    leaf(ConditionField.BODY, ConditionOperator.CONTAINS, "debited")
                )
            )
        )
        assertTrue(engine.matches(filter, bankMessage))
        assertFalse(engine.matches(filter, otpMessage))
    }

    @Test
    fun `OR group any must match`() {
        val filter = filterOf(
            ConditionGroup(
                LogicGroup.OR,
                listOf(
                    leaf(ConditionField.SENDER, ConditionOperator.CONTAINS, "ZOMATO"),
                    leaf(ConditionField.SENDER, ConditionOperator.STARTS_WITH, "+91")
                )
            )
        )
        assertTrue(engine.matches(filter, promoMessage))
        assertTrue(engine.matches(filter, personalMessage))
        assertFalse(engine.matches(filter, bankMessage))
    }

    @Test
    fun `nested groups - OR of AND groups`() {
        val filter = filterOf(
            ConditionGroup(
                LogicGroup.OR,
                listOf(
                    ConditionGroup(
                        LogicGroup.AND,
                        listOf(
                            leaf(ConditionField.SENDER_NAME, ConditionOperator.CONTAINS, "HDFC"),
                            leaf(ConditionField.BODY, ConditionOperator.CONTAINS, "OTP")
                        )
                    ),
                    ConditionGroup(
                        LogicGroup.AND,
                        listOf(
                            leaf(ConditionField.SENDER, ConditionOperator.CONTAINS, "ZOMATO"),
                            leaf(ConditionField.BODY, ConditionOperator.CONTAINS, "OFF")
                        )
                    )
                )
            )
        )
        assertTrue(engine.matches(filter, otpMessage))
        assertTrue(engine.matches(filter, promoMessage))
        assertFalse(engine.matches(filter, bankMessage))
        assertFalse(engine.matches(filter, personalMessage))
    }

    @Test
    fun `empty root never matches`() {
        val filter = filterOf(ConditionGroup(LogicGroup.AND, emptyList()))
        assertFalse(engine.matches(filter, bankMessage))
    }

    @Test
    fun `group containing only empty groups never matches`() {
        val filter = filterOf(
            ConditionGroup(LogicGroup.OR, listOf(ConditionGroup(LogicGroup.AND, emptyList())))
        )
        assertFalse(engine.matches(filter, bankMessage))
    }

    @Test
    fun `disabled filter excluded from findMatchingFilters`() {
        val filter = filterOf(
            ConditionGroup(LogicGroup.AND, listOf(leaf(ConditionField.BODY, ConditionOperator.CONTAINS, "debited"))),
            isEnabled = false
        )
        assertTrue(engine.findMatchingFilters(listOf(filter), bankMessage).isEmpty())
    }

    @Test
    fun `findMatchingFilters returns all matching enabled filters`() {
        val filter1 = filterOf(
            ConditionGroup(LogicGroup.AND, listOf(leaf(ConditionField.SENDER, ConditionOperator.CONTAINS, "HDFC"))),
            id = 1L
        )
        val filter2 = filterOf(
            ConditionGroup(LogicGroup.AND, listOf(leaf(ConditionField.BODY, ConditionOperator.CONTAINS, "debited"))),
            id = 2L
        )
        val filter3 = filterOf(
            ConditionGroup(LogicGroup.AND, listOf(leaf(ConditionField.BODY, ConditionOperator.CONTAINS, "OFF"))),
            id = 3L
        )
        val matches = engine.findMatchingFilters(listOf(filter1, filter2, filter3), bankMessage)
        assertEquals(2, matches.size)
        assertTrue(matches.any { it.id == 1L })
        assertTrue(matches.any { it.id == 2L })
    }

    @Test
    fun `sender_name field matches correctly`() {
        val filter = filterOf(
            ConditionGroup(LogicGroup.AND, listOf(leaf(ConditionField.SENDER_NAME, ConditionOperator.EQUALS, "Zomato")))
        )
        assertTrue(engine.matches(filter, promoMessage))
        assertFalse(engine.matches(filter, bankMessage))
    }
}

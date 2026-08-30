package com.strings.app.domain.filter

import com.strings.app.domain.model.ConditionField
import com.strings.app.domain.model.ConditionGroup
import com.strings.app.domain.model.ConditionLeaf
import com.strings.app.domain.model.ConditionNode
import com.strings.app.domain.model.ConditionOperator
import com.strings.app.domain.model.LogicGroup
import com.strings.app.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterSuggesterTest {
    private val suggester = FilterSuggester()

    private fun msg(sender: String, body: String): Message =
        Message(sender = sender, senderName = sender, body = body, timestamp = 0L)

    private fun bodyLeaves(result: SuggestedFilter): List<ConditionLeaf> =
        result.root.children
            .filterIsInstance<ConditionLeaf>()
            .filter { it.field == ConditionField.BODY }

    private fun bodyValues(result: SuggestedFilter): List<String> =
        bodyLeaves(result).map { it.value }

    private fun senderLeaf(result: SuggestedFilter): ConditionLeaf? =
        result.root.children
            .filterIsInstance<ConditionLeaf>()
            .firstOrNull { it.field == ConditionField.SENDER }

    private fun senderCondition(result: SuggestedFilter): ConditionNode? =
        senderLeaf(result) ?: result.root.children
            .filterIsInstance<ConditionGroup>()
            .firstOrNull { group ->
                group.children.all { it is ConditionLeaf && it.field == ConditionField.SENDER }
            }

    @Test
    fun `shared sender produces SENDER EQUALS leaf and a body phrase`() {
        val result: SuggestedFilter = suggester.suggest(
            listOf(
                msg("VM-HDFCBK", "Dear customer your OTP is 123456 for login"),
                msg("VM-HDFCBK", "Dear customer your OTP is 987654 for login")
            )
        )!!
        val sender: ConditionLeaf = senderLeaf(result)!!
        assertEquals(ConditionOperator.EQUALS, sender.operator)
        assertEquals("VM-HDFCBK", sender.value)
        assertTrue(bodyValues(result).isNotEmpty())
        assertEquals("HDFCBK messages", result.name)
    }

    @Test
    fun `shared DLT principal across operators produces SENDER CONTAINS principal`() {
        val result: SuggestedFilter = suggester.suggest(
            listOf(
                msg("AX-BSELTD-S", "Your order is confirmed and will ship soon"),
                msg("VM-BSELTD-T", "Your order is confirmed and will ship today")
            )
        )!!
        val sender: ConditionLeaf = senderLeaf(result)!!
        assertEquals(ConditionOperator.CONTAINS, sender.operator)
        assertEquals("BSELTD", sender.value)
        assertEquals("BSELTD messages", result.name)
    }

    @Test
    fun `ordered phrases chain into a single MATCHES_REGEX`() {
        val first = "Your account is credited XXXX amount in your bank account"
        val second = "Your account is credited YYY amount in your bank account"
        val result: SuggestedFilter = suggester.suggest(
            listOf(msg("BANK", first), msg("BANK", second))
        )!!
        val body: List<ConditionLeaf> = bodyLeaves(result)
        assertEquals(1, body.size)
        assertEquals(ConditionOperator.MATCHES_REGEX, body.first().operator)
        val regex = Regex(body.first().value, RegexOption.IGNORE_CASE)
        assertTrue(regex.containsMatchIn(first))
        assertTrue(regex.containsMatchIn(second))
        // Order is enforced: the same phrases in reverse order must not match.
        assertFalse(regex.containsMatchIn("amount in your bank account then Your account is credited"))
    }

    @Test
    fun `out-of-order phrase falls back to a separate AND CONTAINS`() {
        val result: SuggestedFilter = suggester.suggest(
            listOf(
                msg("ALERT", "Payment received from Alice to Bob"),
                msg("ALERT", "Payment received from Bob to Alice")
            )
        )!!
        val body: List<ConditionLeaf> = bodyLeaves(result)
        val regexLeaves = body.filter { it.operator == ConditionOperator.MATCHES_REGEX }
        val containsLeaves = body.filter { it.operator == ConditionOperator.CONTAINS }
        // The consistently-ordered prefix chains into a regex; the swapped name is a separate CONTAINS.
        assertEquals(1, regexLeaves.size)
        assertTrue(containsLeaves.any { it.value == "Bob" })
    }

    @Test
    fun `falls back to a single common word when no longer phrase is shared`() {
        val result: SuggestedFilter = suggester.suggest(
            listOf(
                msg("AD-FLPKRT", "Flipkart order shipped today"),
                msg("VM-AMAZON", "Amazon order delivered yesterday")
            )
        )!!
        assertEquals(listOf("order"), bodyValues(result))
        val sender: ConditionNode = senderCondition(result)!!
        assertTrue(sender is ConditionGroup)
        val group: ConditionGroup = sender as ConditionGroup
        assertEquals(LogicGroup.OR, group.logic)
        val principals: List<String> = group.children.map { (it as ConditionLeaf).value }
        assertEquals(listOf("AMAZON", "FLPKRT"), principals)
    }

    @Test
    fun `different principals with no common phrase still produces sender OR group`() {
        val result: SuggestedFilter = suggester.suggest(
            listOf(
                msg("AD-FLPKRT", "Meeting scheduled noon"),
                msg("VM-AMAZON", "Lunch tomorrow afternoon")
            )
        )!!
        val sender: ConditionNode = senderCondition(result)!!
        assertTrue(sender is ConditionGroup)
    }

    @Test
    fun `word-boundary aware so noon does not match afternoon`() {
        val result: SuggestedFilter? = suggester.suggest(
            listOf(
                msg("AD-ONE", "Reminder noon"),
                msg("VM-TWO", "Reminder afternoon")
            )
        )
        // Only "Reminder" is a real shared word; "noon" must NOT match inside "afternoon".
        assertNotNull(result)
        assertEquals(listOf("Reminder"), bodyValues(result!!))
    }
}

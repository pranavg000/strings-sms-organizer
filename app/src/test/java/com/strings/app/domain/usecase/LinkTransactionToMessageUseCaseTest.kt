package com.strings.app.domain.usecase

import com.strings.app.domain.fakes.FakeMessageRepository
import com.strings.app.domain.fakes.FakeTagRepository
import com.strings.app.domain.fakes.FakeTransactionRepository
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.Tag
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionOrigin
import com.strings.app.domain.model.TransactionType
import com.strings.app.domain.transaction.TransactionCategorizer
import com.strings.app.domain.transaction.TransactionParser
import com.strings.app.domain.transaction.defaultBankParsers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LinkTransactionToMessageUseCaseTest {
    private lateinit var tagRepository: FakeTagRepository
    private lateinit var messageRepository: FakeMessageRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categorizer: TransactionCategorizer
    private lateinit var useCase: LinkTransactionToMessageUseCase
    private var inboxTagId: Long = 0L
    private var accountId: Long = 0L
    private var anchorMessageId: Long = 0L
    private var anchorTransactionId: Long = 0L
    private var sentinelId: Long = 0L
    private var swiggyMessageId: Long = 0L
    private var zomatoMessageId: Long = 0L

    @Before
    fun setUp() = runTest {
        tagRepository = FakeTagRepository()
        messageRepository = FakeMessageRepository()
        transactionRepository = FakeTransactionRepository()
        categorizer = TransactionCategorizer(
            transactionParser = TransactionParser(defaultBankParsers()),
            transactionRepository = transactionRepository,
            messageRepository = messageRepository,
            tagRepository = tagRepository
        )
        useCase = LinkTransactionToMessageUseCase(transactionRepository, messageRepository, categorizer)
        inboxTagId = tagRepository.seedTag(Tag(name = "Inbox", color = "", icon = "inbox", isSystemTag = true))
        accountId = transactionRepository.insertAccount(
            Account(
                bankName = "HDFC",
                accountTail = "2210",
                accountType = AccountType.SAVINGS,
                displayName = "HDFC Savings",
                bankCode = "HDFC"
            )
        )
        anchorMessageId = messageRepository.seedMessage(
            message("VM-HDFCBK", timestamp = 5_000L, body = "Rs.100.00 debited from a/c XX2210. Avl bal 900.00"),
            setOf(inboxTagId)
        )
        anchorTransactionId = transactionRepository.seedTransaction(
            Transaction(
                messageId = anchorMessageId,
                accountId = accountId,
                amount = 100.0,
                type = TransactionType.DEBIT,
                balanceAfter = 900.0,
                timestamp = 5_000L,
                rawMatch = "raw"
            )
        )
        sentinelId = transactionRepository.seedTransaction(
            Transaction(
                messageId = anchorMessageId,
                accountId = accountId,
                amount = 636.94,
                type = TransactionType.DEBIT,
                balanceAfter = null,
                timestamp = 4_999L,
                rawMatch = "Balance check: expected 1536.94, reported 900.0",
                origin = TransactionOrigin.SENTINEL
            )
        )
        swiggyMessageId = messageRepository.seedMessage(
            message("AX-SWIGGY", timestamp = 3_000L, body = "Your Swiggy order is on its way"),
            setOf(inboxTagId)
        )
        zomatoMessageId = messageRepository.seedMessage(
            message("AX-ZOMATO", timestamp = 2_000L, body = "Your Zomato order is on its way"),
            setOf(inboxTagId)
        )
    }

    private fun message(sender: String, timestamp: Long, body: String): Message = Message(
        sender = sender,
        senderName = sender,
        body = body,
        timestamp = timestamp
    )

    private fun financeTagIds(): Set<Long> {
        val finance: Tag = tagRepository.tags.first { it.name == TransactionCategorizer.FINANCE_TAG_NAME }
        val bank: Tag = tagRepository.tags.first { it.name == "HDFC" }
        return setOf(finance.id, bank.id)
    }

    @Test
    fun linkTurnsSentinelIntoLinkedTransactionOnTheMessage() = runTest {
        val result: LinkTransactionResult = useCase.link(sentinelId, swiggyMessageId)
        assertEquals(LinkTransactionResult.LINKED, result)
        val linked: Transaction? = transactionRepository.getTransactionForMessage(swiggyMessageId)
        assertNotNull(linked)
        assertEquals(sentinelId, linked!!.id)
        assertEquals(TransactionOrigin.LINKED, linked.origin)
        assertEquals(swiggyMessageId, linked.messageId)
        assertEquals(3_000L, linked.timestamp)
        assertEquals(636.94, linked.amount, 0.0001)
        assertEquals(TransactionType.DEBIT, linked.type)
        assertEquals(accountId, linked.accountId)
        assertNull(linked.balanceAfter)
        // The anchor message keeps its own parsed transaction and no longer "owns" the sentinel.
        assertEquals(anchorTransactionId, transactionRepository.getTransactionForMessage(anchorMessageId)!!.id)
        assertTrue(messageRepository.tagIdsOf(swiggyMessageId).containsAll(financeTagIds()))
        assertTrue(inboxTagId in messageRepository.tagIdsOf(swiggyMessageId))
    }

    @Test
    fun relinkMovesTransactionAndReleasesPreviousMessage() = runTest {
        useCase.link(sentinelId, swiggyMessageId)
        val result: LinkTransactionResult = useCase.link(sentinelId, zomatoMessageId)
        assertEquals(LinkTransactionResult.LINKED, result)
        assertNull(transactionRepository.getTransactionForMessage(swiggyMessageId))
        val moved: Transaction? = transactionRepository.getTransactionForMessage(zomatoMessageId)
        assertNotNull(moved)
        assertEquals(sentinelId, moved!!.id)
        assertEquals(2_000L, moved.timestamp)
        assertEquals(TransactionOrigin.LINKED, moved.origin)
        assertEquals(setOf(inboxTagId), messageRepository.tagIdsOf(swiggyMessageId))
        assertTrue(messageRepository.tagIdsOf(zomatoMessageId).containsAll(financeTagIds()))
        assertEquals(2, transactionRepository.transactions.size)
    }

    @Test
    fun linkedTransactionSurvivesRecategorizationOfItsMessage() = runTest {
        useCase.link(sentinelId, swiggyMessageId)
        val swiggy: Message = messageRepository.getMessageById(swiggyMessageId)!!
        assertNull(categorizer.categorize(swiggy))
        val stillThere: Transaction? = transactionRepository.getTransactionForMessage(swiggyMessageId)
        assertNotNull(stillThere)
        assertEquals(TransactionOrigin.LINKED, stillThere!!.origin)
    }

    @Test
    fun removeDeletesLinkedRowAndStripsFinanceTags() = runTest {
        useCase.link(sentinelId, swiggyMessageId)
        useCase.remove(sentinelId)
        assertNull(transactionRepository.getTransactionById(sentinelId))
        assertNull(transactionRepository.getTransactionForMessage(swiggyMessageId))
        assertEquals(setOf(inboxTagId), messageRepository.tagIdsOf(swiggyMessageId))
        assertEquals(1, transactionRepository.transactions.size)
    }

    @Test
    fun removeIgnoresParsedAndSentinelRows() = runTest {
        useCase.remove(anchorTransactionId)
        useCase.remove(sentinelId)
        assertEquals(2, transactionRepository.transactions.size)
    }

    @Test
    fun parsedTransactionsCannotBeLinked() = runTest {
        val result: LinkTransactionResult = useCase.link(anchorTransactionId, swiggyMessageId)
        assertEquals(LinkTransactionResult.NOT_LINKABLE, result)
        assertEquals(anchorMessageId, transactionRepository.getTransactionById(anchorTransactionId)!!.messageId)
        assertFalse(messageRepository.tagIdsOf(swiggyMessageId).size > 1)
    }

    @Test
    fun linkReportsMissingTargets() = runTest {
        assertEquals(LinkTransactionResult.TRANSACTION_NOT_FOUND, useCase.link(999L, swiggyMessageId))
        assertEquals(LinkTransactionResult.MESSAGE_NOT_FOUND, useCase.link(sentinelId, 999L))
        assertEquals(TransactionOrigin.SENTINEL, transactionRepository.getTransactionById(sentinelId)!!.origin)
    }

    @Test
    fun createBuildsLinkedRowForRestore() = runTest {
        val id: Long? = useCase.create(
            messageId = zomatoMessageId,
            accountId = accountId,
            amount = 250.0,
            type = TransactionType.CREDIT,
            balanceAfter = 1_150.0,
            rawMatch = "restored"
        )
        assertNotNull(id)
        val created: Transaction = transactionRepository.getTransactionById(id!!)!!
        assertEquals(TransactionOrigin.LINKED, created.origin)
        assertEquals(zomatoMessageId, created.messageId)
        assertEquals(2_000L, created.timestamp)
        assertEquals(1_150.0, created.balanceAfter!!, 0.0001)
        assertTrue(messageRepository.tagIdsOf(zomatoMessageId).containsAll(financeTagIds()))
        assertNull(useCase.create(999L, accountId, 1.0, TransactionType.DEBIT, null, ""))
        assertNull(useCase.create(zomatoMessageId, 999L, 1.0, TransactionType.DEBIT, null, ""))
    }
}

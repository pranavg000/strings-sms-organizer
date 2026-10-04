package com.strings.app.domain.usecase

import com.strings.app.domain.backup.BackupBundle
import com.strings.app.domain.backup.ImportResult
import com.strings.app.domain.fakes.FakeBackupSettings
import com.strings.app.domain.fakes.FakeFilterRepository
import com.strings.app.domain.fakes.FakeMessageRepository
import com.strings.app.domain.fakes.FakeTagRepository
import com.strings.app.domain.fakes.FakeTransactionRepository
import com.strings.app.domain.fakes.FakeTransactionRunner
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.ConditionField
import com.strings.app.domain.model.ConditionGroup
import com.strings.app.domain.model.ConditionLeaf
import com.strings.app.domain.model.ConditionOperator
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.FilterAction
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.TabConfig
import com.strings.app.domain.model.Tag
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionOrigin
import com.strings.app.domain.model.TransactionType
import com.strings.app.domain.transaction.TransactionCategorizer
import com.strings.app.domain.transaction.TransactionParser
import com.strings.app.domain.transaction.defaultBankParsers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class Device {
    val tagRepository = FakeTagRepository()
    val filterRepository = FakeFilterRepository()
    val messageRepository = FakeMessageRepository()
    val transactionRepository = FakeTransactionRepository()
    val settings = FakeBackupSettings()
    val transactionRunner = FakeTransactionRunner()
    val inboxTagId: Long = tagRepository.seedTag(
        Tag(name = "Inbox", color = "", icon = "inbox", sortOrder = 0, isSystemTag = true)
    )
    val otpTagId: Long = tagRepository.seedTag(
        Tag(name = "OTP", color = "", icon = "security", sortOrder = 1, isSystemTag = true)
    )
    fun exportUseCase(): ExportDataUseCase = ExportDataUseCase(
        tagRepository = tagRepository,
        filterRepository = filterRepository,
        messageRepository = messageRepository,
        transactionRepository = transactionRepository,
        backupSettings = settings
    )
    val categorizer: TransactionCategorizer = TransactionCategorizer(
        transactionParser = TransactionParser(defaultBankParsers()),
        transactionRepository = transactionRepository,
        messageRepository = messageRepository,
        tagRepository = tagRepository
    )
    fun recategorizeUseCase(): RecategorizeTransactionsUseCase = RecategorizeTransactionsUseCase(
        messageRepository = messageRepository,
        transactionRepository = transactionRepository,
        transactionCategorizer = categorizer
    )
    fun toggleTransactionUseCase(): ToggleTransactionUseCase = ToggleTransactionUseCase(
        messageRepository = messageRepository,
        transactionCategorizer = categorizer
    )
    fun linkTransactionUseCase(): LinkTransactionToMessageUseCase = LinkTransactionToMessageUseCase(
        transactionRepository = transactionRepository,
        messageRepository = messageRepository,
        transactionCategorizer = categorizer
    )
    fun importUseCase(): ImportDataUseCase = ImportDataUseCase(
        tagRepository = tagRepository,
        filterRepository = filterRepository,
        messageRepository = messageRepository,
        transactionRepository = transactionRepository,
        backupSettings = settings,
        json = testJson,
        recategorizeTransactionsUseCase = recategorizeUseCase(),
        toggleTransactionUseCase = toggleTransactionUseCase(),
        linkTransactionUseCase = linkTransactionUseCase(),
        transactionRunner = transactionRunner
    )
    companion object {
        val testJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

class ExportImportDataUseCaseTest {
    private fun message(
        sender: String,
        timestamp: Long,
        deviceMessageId: Long? = null,
        isRead: Boolean = false,
        isArchived: Boolean = false,
        isTrashed: Boolean = false,
        isOtp: Boolean = false,
        isTransactionExcluded: Boolean = false,
        body: String? = null,
        description: String? = null
    ): Message = Message(
        sender = sender,
        senderName = sender,
        body = body ?: "body of $sender",
        timestamp = timestamp,
        isRead = isRead,
        isArchived = isArchived,
        isTrashed = isTrashed,
        isOtp = isOtp,
        deviceMessageId = deviceMessageId,
        isTransactionExcluded = isTransactionExcluded,
        description = description
    )

    /** Parseable HDFC savings debits (no balance in body, so the 5000.5 override is user data). */
    private companion object {
        const val BALANCE_SENDER: String = "VM-HDFCBK"
        const val BALANCE_BODY: String =
            "Rs.100.00 debited from a/c XX2210 on 10-06-26. -HDFC Bank"
        /** Parseable too, but the user marked it "not a transaction" on the source device. */
        const val EXCLUDED_SENDER: String = "AD-HDFCBK"
        const val EXCLUDED_BODY: String =
            "Rs.250.00 debited from a/c XX2210 on 11-06-26. -HDFC Bank"
        /** Not parseable: the user linked a sentinel to it by hand, so the row must travel in the bundle. */
        const val LINKED_SENDER: String = "AX-SWIGGY"
        const val LINKED_BODY: String = "Your Swiggy order has been delivered. Enjoy!"
        const val LINKED_DESCRIPTION: String = "Dinner with friends"
        /** Plain message carrying only a description. */
        const val NOTED_SENDER: String = "noted"
        const val NOTED_DESCRIPTION: String = "Remember to follow up"
    }

    private fun transaction(messageId: Long, balanceAfter: Double?): Transaction = Transaction(
        messageId = messageId,
        accountId = 1L,
        amount = 100.0,
        type = TransactionType.DEBIT,
        balanceAfter = balanceAfter,
        timestamp = 1000L,
        rawMatch = "raw"
    )

    /** Populates a source device with 3 months of "curation" for round-trip tests. */
    private suspend fun populateSource(source: Device): Long {
        val financeTagId: Long = source.tagRepository.seedTag(Tag(name = "Finance", color = "#000000", icon = "wallet"))
        val hdfcTagId: Long = source.tagRepository.seedTag(
            Tag(name = "HDFC", color = "#000000", icon = "bank", parentTagId = financeTagId)
        )
        source.tagRepository.seedTab(TabConfig(tagId = source.inboxTagId, position = 1, isVisible = true))
        source.tagRepository.seedTab(TabConfig(tagId = financeTagId, position = 0, isVisible = false))
        source.filterRepository.insertFilter(
            Filter(
                name = "Bank alerts",
                priority = 1,
                isEnabled = true,
                root = ConditionGroup(
                    children = listOf(ConditionLeaf(ConditionField.SENDER, ConditionOperator.CONTAINS, "HDFCBK"))
                ),
                actions = listOf(FilterAction(actionType = ActionType.ASSIGN_TAG, targetTagId = financeTagId))
            )
        )
        source.filterRepository.insertFilter(
            Filter(
                name = "Old promo cleanup",
                priority = 2,
                isEnabled = false,
                root = ConditionGroup(
                    children = listOf(ConditionLeaf(ConditionField.BODY, ConditionOperator.CONTAINS, "sale"))
                ),
                actions = listOf(FilterAction(actionType = ActionType.ARCHIVE, targetTagId = null))
            )
        )
        source.messageRepository.seedMessage(
            message(sender = "plain", timestamp = 1L, deviceMessageId = 101L),
            setOf(source.inboxTagId)
        )
        source.messageRepository.seedMessage(
            message(sender = "read", timestamp = 2L, deviceMessageId = 102L, isRead = true),
            setOf(source.inboxTagId)
        )
        source.messageRepository.seedMessage(
            message(sender = "tagged", timestamp = 3L, deviceMessageId = 103L),
            setOf(source.inboxTagId, financeTagId, hdfcTagId)
        )
        source.messageRepository.seedMessage(
            message(sender = "otp", timestamp = 4L, deviceMessageId = 104L, isOtp = true),
            setOf(source.inboxTagId, source.otpTagId)
        )
        source.messageRepository.seedMessage(
            message(sender = "archived", timestamp = 5L, deviceMessageId = 105L, isArchived = true),
            setOf(source.inboxTagId)
        )
        source.messageRepository.seedMessage(
            message(sender = "trashed", timestamp = 6L, deviceMessageId = 106L, isTrashed = true),
            setOf(source.inboxTagId)
        )
        source.messageRepository.seedMessage(
            message(sender = "inboxRemoved", timestamp = 7L, deviceMessageId = 107L),
            setOf(financeTagId)
        )
        val balanceMessageId: Long = source.messageRepository.seedMessage(
            message(sender = BALANCE_SENDER, timestamp = 8L, deviceMessageId = 108L, body = BALANCE_BODY),
            setOf(source.inboxTagId)
        )
        source.transactionRepository.seedTransaction(transaction(balanceMessageId, balanceAfter = 5000.5))
        source.messageRepository.seedMessage(
            message(
                sender = EXCLUDED_SENDER,
                timestamp = 11L,
                deviceMessageId = 111L,
                isTransactionExcluded = true,
                body = EXCLUDED_BODY
            ),
            setOf(source.inboxTagId)
        )
        val savingsAccountId: Long = source.transactionRepository.insertAccount(
            Account(
                bankName = "HDFC",
                accountTail = "2210",
                accountType = AccountType.SAVINGS,
                displayName = "HDFC Savings",
                bankCode = "HDFC",
                colorIndex = 1
            )
        )
        val linkedMessageId: Long = source.messageRepository.seedMessage(
            message(
                sender = LINKED_SENDER,
                timestamp = 12L,
                deviceMessageId = 112L,
                body = LINKED_BODY,
                description = LINKED_DESCRIPTION
            ),
            setOf(source.inboxTagId, financeTagId, hdfcTagId)
        )
        source.transactionRepository.seedTransaction(
            Transaction(
                messageId = linkedMessageId,
                accountId = savingsAccountId,
                amount = 636.94,
                type = TransactionType.DEBIT,
                balanceAfter = null,
                timestamp = 12L,
                rawMatch = "Balance check: expected 1000.0, reported 363.06",
                origin = TransactionOrigin.LINKED
            )
        )
        source.messageRepository.seedMessage(
            message(sender = NOTED_SENDER, timestamp = 13L, deviceMessageId = 113L, description = NOTED_DESCRIPTION),
            setOf(source.inboxTagId)
        )
        val primaryCardId: Long = source.transactionRepository.insertAccount(
            Account(
                bankName = "HDFC Card",
                accountTail = "8802",
                accountType = AccountType.CREDIT_CARD,
                displayName = "HDFC Card",
                bankCode = "HDFC",
                colorIndex = 2
            )
        )
        source.transactionRepository.insertAccount(
            Account(
                bankName = "HDFC Card",
                accountTail = "9911",
                accountType = AccountType.CREDIT_CARD,
                displayName = "Addon Card",
                bankCode = "HDFC",
                colorIndex = 3,
                parentAccountId = primaryCardId
            )
        )
        source.transactionRepository.insertAccount(
            Account(
                bankName = "Zomato Money",
                accountTail = "",
                accountType = AccountType.WALLET,
                displayName = "Zomato Money",
                bankCode = "ZOMATO",
                colorIndex = 4,
                isEnabled = false
            )
        )
        source.messageRepository.seedMessage(
            message(sender = "noDeviceId", timestamp = 9L, deviceMessageId = null, isRead = true),
            setOf(source.inboxTagId)
        )
        source.messageRepository.seedMessage(
            message(sender = "goneFromDevice", timestamp = 10L, deviceMessageId = 110L, isRead = true),
            setOf(source.inboxTagId)
        )
        source.settings.themeMode = "DARK"
        source.settings.appLockEnabled = true
        return financeTagId
    }

    /**
     * Simulates the fresh release install AFTER importAll ran: every device
     * SMS re-imported with new local ids, baseline tags, default flags, and
     * re-parsed transactions (balance not captured by the parser). The
     * message excluded on the source device parses fine here, so importAll
     * has already turned it into a transaction.
     */
    private suspend fun populateTarget(target: Device) {
        target.tagRepository.seedTab(TabConfig(tagId = target.inboxTagId, position = 0, isVisible = true))
        val senderToDeviceId: Map<String, Long?> = mapOf(
            "plain" to 101L,
            "read" to 102L,
            "tagged" to 103L,
            "otp" to 104L,
            "archived" to 105L,
            "trashed" to 106L,
            "inboxRemoved" to 107L,
            BALANCE_SENDER to 108L,
            "noDeviceId" to null,
            EXCLUDED_SENDER to 111L,
            LINKED_SENDER to 112L,
            NOTED_SENDER to 113L
        )
        var timestamp = 1L
        for ((sender, deviceId) in senderToDeviceId) {
            val isOtp: Boolean = sender == "otp"
            val isBalance: Boolean = sender == BALANCE_SENDER
            val isExcluded: Boolean = sender == EXCLUDED_SENDER
            val baseline: Set<Long> = if (isOtp) setOf(target.inboxTagId, target.otpTagId) else setOf(target.inboxTagId)
            val id: Long = target.messageRepository.seedMessage(
                message(
                    sender = sender,
                    timestamp = timestamp,
                    deviceMessageId = deviceId,
                    isOtp = isOtp,
                    body = when {
                        isBalance -> BALANCE_BODY
                        isExcluded -> EXCLUDED_BODY
                        sender == LINKED_SENDER -> LINKED_BODY
                        else -> null
                    }
                ),
                baseline
            )
            if (isBalance || isExcluded) {
                target.transactionRepository.seedTransaction(transaction(id, balanceAfter = null))
            }
            timestamp++
        }
    }

    @Test
    fun exportIncludesOnlyMessagesWithRestorableState() = runTest {
        val source = Device()
        populateSource(source)
        val exported: String = source.exportUseCase().execute()
        val bundle: BackupBundle = Device.testJson.decodeFromString(BackupBundle.serializer(), exported)
        assertEquals(7, bundle.version)
        assertTrue(bundle.messageStates.all { it.bodyHash != null })
        val exportedSenders: Set<String> = bundle.messageStates.map { it.sender }.toSet()
        assertEquals(
            setOf(
                "read", "tagged", "archived", "trashed", "inboxRemoved",
                BALANCE_SENDER, "noDeviceId", "goneFromDevice", EXCLUDED_SENDER,
                LINKED_SENDER, NOTED_SENDER
            ),
            exportedSenders
        )
        assertFalse(exportedSenders.contains("plain"))
        assertFalse(exportedSenders.contains("otp"))
        val taggedState = bundle.messageStates.first { it.sender == "tagged" }
        assertEquals(listOf("Finance", "HDFC", "Inbox"), taggedState.tagNames)
        val balanceState = bundle.messageStates.first { it.sender == BALANCE_SENDER }
        assertEquals(5000.5, balanceState.balanceAfter!!, 0.0001)
        assertFalse(balanceState.isTransactionExcluded)
        assertTrue(balanceState.linkedTransactions.isEmpty())
        val excludedState = bundle.messageStates.first { it.sender == EXCLUDED_SENDER }
        assertTrue(excludedState.isTransactionExcluded)
        val linkedState = bundle.messageStates.first { it.sender == LINKED_SENDER }
        assertEquals(LINKED_DESCRIPTION, linkedState.description)
        assertNull(linkedState.balanceAfter)
        val linkedDto = linkedState.linkedTransactions.single()
        assertEquals("HDFC", linkedDto.bankCode)
        assertEquals("2210", linkedDto.accountTail)
        assertEquals(636.94, linkedDto.amount, 0.0001)
        assertEquals("DEBIT", linkedDto.type)
        val notedState = bundle.messageStates.first { it.sender == NOTED_SENDER }
        assertEquals(NOTED_DESCRIPTION, notedState.description)
        assertEquals(2, bundle.tabs.size)
        assertEquals(4, bundle.accounts.size)
        val savingsDto = bundle.accounts.first { it.accountTail == "2210" }
        assertEquals("HDFC", savingsDto.bankCode)
        assertEquals("HDFC Savings", savingsDto.name)
        assertEquals("HDFC", savingsDto.tagName)
        val addonDto = bundle.accounts.first { it.accountTail == "9911" }
        assertEquals("HDFC", addonDto.parentBankCode)
        assertEquals("8802", addonDto.parentAccountTail)
        val walletDto = bundle.accounts.first { it.bankCode == "ZOMATO" }
        assertFalse(walletDto.isEnabled)
        assertNotNull(bundle.settings)
        assertEquals("DARK", bundle.settings!!.themeMode)
        assertTrue(bundle.settings!!.appLockEnabled)
    }

    @Test
    fun roundTripRestoresEverythingOnFreshDevice() = runTest {
        val source = Device()
        populateSource(source)
        val exported: String = source.exportUseCase().execute()
        val target = Device()
        populateTarget(target)
        val result: ImportResult = target.importUseCase().execute(exported)
        // Finance + HDFC tags pre-exist by the time tags import: restoring the
        // accounts triggers recategorization, which recreates them via ensureTag.
        assertEquals(0, result.tagsAdded)
        assertEquals(2, result.filtersAdded)
        assertEquals(0, result.filtersSkipped)
        assertEquals(2, result.tabsRestored)
        assertEquals(4, result.accountsAdded)
        assertEquals(10, result.messagesRestored)
        assertEquals(1, result.messagesUnmatched)
        assertEquals(1, result.balancesRestored)
        assertEquals(1, result.linkedTransactionsRestored)
        val savings: Account? = target.transactionRepository.findAccountByCodeAndTail("HDFC", "2210")
        val primary: Account? = target.transactionRepository.findAccountByCodeAndTail("HDFC", "8802")
        val addon: Account? = target.transactionRepository.findAccountByCodeAndTail("HDFC", "9911")
        val wallet: Account? = target.transactionRepository.findAccountByCodeAndTail("ZOMATO", "")
        assertNotNull(savings)
        assertNotNull(primary)
        assertNotNull(addon)
        assertNotNull(wallet)
        assertEquals("HDFC Savings", savings!!.displayName)
        assertEquals("HDFC", savings.bankName)
        assertEquals(primary!!.id, addon!!.parentAccountId)
        assertFalse(wallet!!.isEnabled)
        val financeTag: Tag? = target.tagRepository.getTagByName("Finance")
        val hdfcTag: Tag? = target.tagRepository.getTagByName("HDFC")
        assertNotNull(financeTag)
        assertNotNull(hdfcTag)
        assertEquals(financeTag!!.id, hdfcTag!!.parentTagId)
        val bankFilter: Filter = target.filterRepository.filters.first { it.name == "Bank alerts" }
        assertTrue(bankFilter.isEnabled)
        assertEquals(financeTag.id, bankFilter.actions.single().targetTagId)
        val promoFilter: Filter = target.filterRepository.filters.first { it.name == "Old promo cleanup" }
        assertFalse(promoFilter.isEnabled)
        val inboxTab: TabConfig = target.tagRepository.tabs.first { it.tagId == target.inboxTagId }
        assertEquals(1, inboxTab.position)
        val financeTab: TabConfig = target.tagRepository.tabs.first { it.tagId == financeTag.id }
        assertEquals(0, financeTab.position)
        assertFalse(financeTab.isVisible)
        val messages = target.messageRepository
        assertTrue(messages.messageBySender("read").isRead)
        assertTrue(messages.messageBySender("archived").isArchived)
        assertTrue(messages.messageBySender("trashed").isTrashed)
        assertTrue(messages.messageBySender("noDeviceId").isRead)
        val taggedId: Long = messages.messageBySender("tagged").id
        assertEquals(setOf(target.inboxTagId, financeTag.id, hdfcTag.id), messages.tagIdsOf(taggedId))
        val inboxRemovedId: Long = messages.messageBySender("inboxRemoved").id
        assertEquals(setOf(financeTag.id), messages.tagIdsOf(inboxRemovedId))
        val plainMessage: Message = messages.messageBySender("plain")
        assertFalse(plainMessage.isRead)
        assertEquals(setOf(target.inboxTagId), messages.tagIdsOf(plainMessage.id))
        val balanceMessageId: Long = messages.messageBySender(BALANCE_SENDER).id
        val restoredTransaction: Transaction? =
            target.transactionRepository.getTransactionForMessage(balanceMessageId)
        assertNotNull(restoredTransaction)
        assertEquals(5000.5, restoredTransaction!!.balanceAfter!!, 0.0001)
        // The "not a transaction" override wins over the target's own parse: the
        // transaction importAll/recategorize created is gone, the Finance tags
        // are stripped, and the flag persists so later re-runs keep skipping it.
        val excludedMessage: Message = messages.messageBySender(EXCLUDED_SENDER)
        assertTrue(excludedMessage.isTransactionExcluded)
        assertNull(target.transactionRepository.getTransactionForMessage(excludedMessage.id))
        assertEquals(setOf(target.inboxTagId), messages.tagIdsOf(excludedMessage.id))
        // The hand-linked transaction is recreated against the restored account, tied to the
        // matched message (its timestamp, its id), carrying the message's description.
        val linkedMessage: Message = messages.messageBySender(LINKED_SENDER)
        assertEquals(LINKED_DESCRIPTION, linkedMessage.description)
        val linkedTransaction: Transaction? = target.transactionRepository.getTransactionForMessage(linkedMessage.id)
        assertNotNull(linkedTransaction)
        assertEquals(TransactionOrigin.LINKED, linkedTransaction!!.origin)
        assertEquals(savings.id, linkedTransaction.accountId)
        assertEquals(636.94, linkedTransaction.amount, 0.0001)
        assertEquals(TransactionType.DEBIT, linkedTransaction.type)
        assertEquals(linkedMessage.timestamp, linkedTransaction.timestamp)
        assertEquals(setOf(target.inboxTagId, financeTag.id, hdfcTag.id), messages.tagIdsOf(linkedMessage.id))
        assertEquals(NOTED_DESCRIPTION, messages.messageBySender(NOTED_SENDER).description)
        assertEquals("DARK", target.settings.themeMode)
        assertTrue(target.settings.appLockEnabled)
    }

    @Test
    fun matchesByContentWhenDeviceIdDiffers() = runTest {
        val source = Device()
        source.messageRepository.seedMessage(
            message(sender = "AX-HDFCBK", timestamp = 42L, deviceMessageId = null, isRead = true),
            setOf(source.inboxTagId)
        )
        val exported: String = source.exportUseCase().execute()
        val target = Device()
        target.messageRepository.seedMessage(
            message(sender = "AX-HDFCBK", timestamp = 42L, deviceMessageId = 999L),
            setOf(target.inboxTagId)
        )
        val result: ImportResult = target.importUseCase().execute(exported)
        assertEquals(1, result.messagesRestored)
        assertEquals(0, result.messagesUnmatched)
        assertTrue(target.messageRepository.messageBySender("AX-HDFCBK").isRead)
    }

    /**
     * `deviceMessageId` is `Telephony.Sms._ID`, which restarts on every device. On a new
     * phone the exported id points at an unrelated SMS, so content must win over the id.
     */
    @Test
    fun deviceIdCollisionOnNewDeviceNeverBeatsContentMatch() = runTest {
        val source = Device()
        source.messageRepository.seedMessage(
            message(
                sender = "AX-HDFCBK",
                timestamp = 100L,
                deviceMessageId = 5L,
                body = "alpha",
                isRead = true,
                description = "keep me"
            ),
            setOf(source.inboxTagId)
        )
        val exported: String = source.exportUseCase().execute()
        val target = Device()
        val unrelatedId: Long = target.messageRepository.seedMessage(
            message(sender = "VK-PROMO", timestamp = 999L, deviceMessageId = 5L, body = "unrelated"),
            setOf(target.inboxTagId)
        )
        val realId: Long = target.messageRepository.seedMessage(
            message(sender = "AX-HDFCBK", timestamp = 100L, deviceMessageId = 77L, body = "alpha"),
            setOf(target.inboxTagId)
        )
        val result: ImportResult = target.importUseCase().execute(exported)
        assertEquals(1, result.messagesRestored)
        assertEquals(0, result.messagesUnmatched)
        val real: Message = target.messageRepository.getMessageById(realId)!!
        assertTrue(real.isRead)
        assertEquals("keep me", real.description)
        val unrelated: Message = target.messageRepository.getMessageById(unrelatedId)!!
        assertFalse(unrelated.isRead)
        assertNull(unrelated.description)
    }

    /** A live-ingested SMSC timestamp vs the provider's DATE can differ; the body hash bridges it. */
    @Test
    fun matchesByBodyHashWhenTimestampAndDeviceIdBothDiffer() = runTest {
        val source = Device()
        source.messageRepository.seedMessage(
            message(sender = "AX-HDFCBK", timestamp = 1_000L, deviceMessageId = 5L, body = "alpha", isArchived = true),
            setOf(source.inboxTagId)
        )
        val exported: String = source.exportUseCase().execute()
        val target = Device()
        target.messageRepository.seedMessage(
            message(sender = "AX-HDFCBK", timestamp = 91_000L, deviceMessageId = 5_000L, body = "alpha"),
            setOf(target.inboxTagId)
        )
        val result: ImportResult = target.importUseCase().execute(exported)
        assertEquals(1, result.messagesRestored)
        assertTrue(target.messageRepository.messageBySender("AX-HDFCBK").isArchived)
    }

    /** Pre-v7 bundles carry no hash: the id is still used, but only when the sender agrees. */
    @Test
    fun legacyBundleUsesDeviceIdOnlyWhenSenderAgrees() = runTest {
        val legacyJson: String = """
            {
              "version": 6,
              "messageStates": [
                {"deviceMessageId": 5, "sender": "AX-HDFCBK", "timestamp": 100, "isRead": true}
              ]
            }
        """.trimIndent()
        val wrongSender = Device()
        wrongSender.messageRepository.seedMessage(
            message(sender = "VK-PROMO", timestamp = 999L, deviceMessageId = 5L),
            setOf(wrongSender.inboxTagId)
        )
        val rejected: ImportResult = wrongSender.importUseCase().execute(legacyJson)
        assertEquals(0, rejected.messagesRestored)
        assertEquals(1, rejected.messagesUnmatched)
        assertFalse(wrongSender.messageRepository.messageBySender("VK-PROMO").isRead)
        val sameSender = Device()
        sameSender.messageRepository.seedMessage(
            message(sender = "AX-HDFCBK", timestamp = 999L, deviceMessageId = 5L),
            setOf(sameSender.inboxTagId)
        )
        val accepted: ImportResult = sameSender.importUseCase().execute(legacyJson)
        assertEquals(1, accepted.messagesRestored)
        assertTrue(sameSender.messageRepository.messageBySender("AX-HDFCBK").isRead)
    }

    /**
     * The categorizer marks Finance and bank tags as system tags. They must still import on
     * a device that has never parsed a message from that bank, or every tab, filter action,
     * message tag set and child tag referencing them silently drops.
     */
    @Test
    fun importsFinanceSystemTagsOnTargetThatNeverParsedThatBank() = runTest {
        val source = Device()
        val financeTagId: Long = source.tagRepository.seedTag(
            Tag(name = "Finance", color = "", icon = "wallet", isSystemTag = true)
        )
        val hdfcTagId: Long = source.tagRepository.seedTag(
            Tag(name = "HDFC", color = "", icon = "bank", parentTagId = financeTagId, isSystemTag = true)
        )
        source.tagRepository.seedTag(
            Tag(name = "Bills", color = "", icon = "receipt", parentTagId = hdfcTagId)
        )
        source.tagRepository.seedTab(TabConfig(tagId = financeTagId, position = 1, isVisible = true))
        source.filterRepository.insertFilter(
            Filter(
                name = "HDFC alerts",
                root = ConditionGroup(
                    children = listOf(ConditionLeaf(ConditionField.SENDER, ConditionOperator.CONTAINS, "HDFCBK"))
                ),
                actions = listOf(FilterAction(actionType = ActionType.ASSIGN_TAG, targetTagId = hdfcTagId))
            )
        )
        source.messageRepository.seedMessage(
            message(sender = "VM-HDFCBK", timestamp = 50L, deviceMessageId = 1L, body = "promo, not parseable"),
            setOf(source.inboxTagId, financeTagId, hdfcTagId)
        )
        val exported: String = source.exportUseCase().execute()
        val target = Device()
        val messageId: Long = target.messageRepository.seedMessage(
            message(sender = "VM-HDFCBK", timestamp = 50L, deviceMessageId = 1L, body = "promo, not parseable"),
            setOf(target.inboxTagId)
        )
        val result: ImportResult = target.importUseCase().execute(exported)
        assertEquals(3, result.tagsAdded)
        assertEquals(1, result.tabsRestored)
        val finance: Tag = target.tagRepository.getTagByName("Finance")!!
        val hdfc: Tag = target.tagRepository.getTagByName("HDFC")!!
        val bills: Tag = target.tagRepository.getTagByName("Bills")!!
        assertTrue(finance.isSystemTag)
        assertTrue(hdfc.isSystemTag)
        assertFalse(bills.isSystemTag)
        assertNull(finance.parentTagId)
        assertEquals(finance.id, hdfc.parentTagId)
        assertEquals(hdfc.id, bills.parentTagId)
        assertNotNull(target.tagRepository.tabs.firstOrNull { it.tagId == finance.id })
        val filter: Filter = target.filterRepository.filters.single { it.name == "HDFC alerts" }
        assertEquals(hdfc.id, filter.actions.single().targetTagId)
        assertEquals(setOf(target.inboxTagId, finance.id, hdfc.id), target.messageRepository.tagIdsOf(messageId))
        // Inbox and OTP stay owned by the seeder: never duplicated from the bundle.
        assertEquals(1, target.tagRepository.tags.count { it.name == "Inbox" })
        assertEquals(1, target.tagRepository.tags.count { it.name == "OTP" })
    }

    @Test
    fun importRunsInsideOneTransaction() = runTest {
        val source = Device()
        populateSource(source)
        val exported: String = source.exportUseCase().execute()
        val target = Device()
        populateTarget(target)
        target.importUseCase().execute(exported)
        assertEquals(1, target.transactionRunner.transactionsStarted)
    }

    @Test
    fun importsV2BundleWithoutNewSections() = runTest {
        val v2Json: String = """
            {
              "version": 2,
              "tags": [
                {"name": "Promo", "color": "#FFFFFF", "icon": "label", "parentName": null, "sortOrder": 0, "isSystemTag": false}
              ],
              "filters": []
            }
        """.trimIndent()
        val target = Device()
        val result: ImportResult = target.importUseCase().execute(v2Json)
        assertEquals(1, result.tagsAdded)
        assertEquals(0, result.tabsRestored)
        assertEquals(0, result.messagesRestored)
        assertEquals(0, result.messagesUnmatched)
        assertNotNull(target.tagRepository.getTagByName("Promo"))
        assertEquals("SYSTEM", target.settings.themeMode)
    }

    @Test
    fun excludedMessageStaysExcludedAcrossRecategorizationUntilIncludedAgain() = runTest {
        val device = Device()
        device.transactionRepository.insertAccount(
            Account(
                bankName = "HDFC",
                accountTail = "2210",
                accountType = AccountType.SAVINGS,
                displayName = "HDFC Savings",
                bankCode = "HDFC"
            )
        )
        val messageId: Long = device.messageRepository.seedMessage(
            message(sender = BALANCE_SENDER, timestamp = 1L, deviceMessageId = 1L, body = BALANCE_BODY),
            setOf(device.inboxTagId)
        )
        val recategorize: RecategorizeTransactionsUseCase = device.recategorizeUseCase()
        val toggle: ToggleTransactionUseCase = device.toggleTransactionUseCase()
        recategorize.execute(sinceMillis = 0L)
        assertNotNull(device.transactionRepository.getTransactionForMessage(messageId))
        val financeTag: Tag? = device.tagRepository.getTagByName("Finance")
        assertNotNull(financeTag)
        assertTrue(financeTag!!.id in device.messageRepository.tagIdsOf(messageId))
        toggle.exclude(messageId)
        assertTrue(device.messageRepository.getMessageById(messageId)!!.isTransactionExcluded)
        assertNull(device.transactionRepository.getTransactionForMessage(messageId))
        assertFalse(financeTag.id in device.messageRepository.tagIdsOf(messageId))
        assertTrue(device.inboxTagId in device.messageRepository.tagIdsOf(messageId))
        recategorize.execute(sinceMillis = 0L)
        assertNull(device.transactionRepository.getTransactionForMessage(messageId))
        assertEquals(IncludeTransactionResult.CATEGORIZED, toggle.include(messageId))
        assertFalse(device.messageRepository.getMessageById(messageId)!!.isTransactionExcluded)
        assertNotNull(device.transactionRepository.getTransactionForMessage(messageId))
        assertTrue(financeTag.id in device.messageRepository.tagIdsOf(messageId))
    }

    @Test
    fun includeReportsNotRecognizedWhenDetectionFindsNothing() = runTest {
        val device = Device()
        val messageId: Long = device.messageRepository.seedMessage(
            message(sender = "promo", timestamp = 1L, deviceMessageId = 1L, isTransactionExcluded = true),
            setOf(device.inboxTagId)
        )
        val result: IncludeTransactionResult = device.toggleTransactionUseCase().include(messageId)
        assertEquals(IncludeTransactionResult.NOT_RECOGNIZED, result)
        assertFalse(device.messageRepository.getMessageById(messageId)!!.isTransactionExcluded)
        assertNull(device.transactionRepository.getTransactionForMessage(messageId))
    }

    @Test
    fun rejectsBundleFromNewerVersion() = runTest {
        val target = Device()
        try {
            target.importUseCase().execute("""{"version": 99}""")
            fail("Expected IllegalArgumentException for newer bundle version")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("newer version"))
        }
    }

    @Test
    fun reimportIsIdempotent() = runTest {
        val source = Device()
        populateSource(source)
        val exported: String = source.exportUseCase().execute()
        val target = Device()
        populateTarget(target)
        target.importUseCase().execute(exported)
        val second: ImportResult = target.importUseCase().execute(exported)
        assertEquals(0, second.tagsAdded)
        assertEquals(0, second.filtersAdded)
        assertEquals(2, second.filtersSkipped)
        assertEquals(0, second.accountsAdded)
        assertEquals(4, target.transactionRepository.accounts.size)
        assertEquals(10, second.messagesRestored)
        assertEquals(1, second.linkedTransactionsRestored)
        val linkedMessage: Message = target.messageRepository.messageBySender(LINKED_SENDER)
        assertEquals(1, target.transactionRepository.getTransactionsForMessage(linkedMessage.id).size)
        val excludedMessage: Message = target.messageRepository.messageBySender(EXCLUDED_SENDER)
        assertTrue(excludedMessage.isTransactionExcluded)
        assertNull(target.transactionRepository.getTransactionForMessage(excludedMessage.id))
        val financeTag: Tag? = target.tagRepository.getTagByName("Finance")
        assertNotNull(financeTag)
        assertNull(target.tagRepository.getTagByName("Finance (1)"))
        assertEquals(2, target.tagRepository.tabs.size)
        assertEquals(2, target.filterRepository.filters.size)
    }
}

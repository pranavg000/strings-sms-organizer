package com.strings.app.domain.usecase

import androidx.paging.PagingData
import com.strings.app.domain.backup.BackupBundle
import com.strings.app.domain.backup.BackupSettingsStore
import com.strings.app.domain.backup.ImportResult
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountSuggestion
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
import com.strings.app.domain.model.TransactionType
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.transaction.TransactionCategorizer
import com.strings.app.domain.transaction.TransactionParser
import com.strings.app.domain.transaction.defaultBankParsers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeTagRepository : TagRepository {
    val tags: MutableList<Tag> = mutableListOf()
    val tabs: MutableList<TabConfig> = mutableListOf()
    private var nextTagId: Long = 1L
    private var nextTabId: Long = 1L
    fun seedTag(tag: Tag): Long {
        val id: Long = nextTagId++
        tags.add(tag.copy(id = id))
        return id
    }
    fun seedTab(tab: TabConfig): Long {
        val id: Long = nextTabId++
        tabs.add(tab.copy(id = id))
        return id
    }
    override fun getAllTags(): Flow<List<Tag>> = flowOf(tags.toList())
    override suspend fun getAllTagsList(): List<Tag> = tags.toList()
    override fun getTopLevelTags(): Flow<List<Tag>> = flowOf(tags.filter { it.parentTagId == null })
    override fun getChildTags(parentId: Long): Flow<List<Tag>> = flowOf(tags.filter { it.parentTagId == parentId })
    override fun getVisibleTabs(): Flow<List<TabConfig>> = flowOf(tabs.filter { it.isVisible })
    override fun getAllTabs(): Flow<List<TabConfig>> = flowOf(tabs.toList())
    override fun getTagMessageCounts(): Flow<Map<Long, Int>> = flowOf(emptyMap())
    override suspend fun getTagById(id: Long): Tag? = tags.firstOrNull { it.id == id }
    override suspend fun getTagByName(name: String): Tag? = tags.firstOrNull { it.name == name }
    override suspend fun insertTag(tag: Tag): Long = seedTag(tag)
    override suspend fun updateTag(tag: Tag) {
        val index: Int = tags.indexOfFirst { it.id == tag.id }
        if (index >= 0) tags[index] = tag
    }
    override suspend fun deleteTag(id: Long) {
        tags.removeAll { it.id == id }
    }
    override suspend fun getDescendantTagIds(parentTagId: Long): List<Long> = emptyList()
    override suspend fun insertTabConfig(tabConfig: TabConfig): Long = seedTab(tabConfig)
    override suspend fun updateTabConfig(tabConfig: TabConfig) {
        val index: Int = tabs.indexOfFirst { it.id == tabConfig.id }
        if (index >= 0) tabs[index] = tabConfig
    }
    override suspend fun deleteTabConfig(id: Long) {
        tabs.removeAll { it.id == id }
    }
    override suspend fun deleteTabConfigByTagId(tagId: Long) {
        tabs.removeAll { it.tagId == tagId }
    }
    override suspend fun replaceAllTabs(tabs: List<TabConfig>) {
        this.tabs.clear()
        this.tabs.addAll(tabs)
    }
}

private class FakeFilterRepository : FilterRepository {
    val filters: MutableList<Filter> = mutableListOf()
    private var nextId: Long = 1L
    override fun getAllFilters(): Flow<List<Filter>> = flowOf(filters.sortedBy { it.priority })
    override suspend fun getEnabledFilters(): List<Filter> = filters.filter { it.isEnabled }
    override suspend fun getFilterById(id: Long): Filter? = filters.firstOrNull { it.id == id }
    override suspend fun insertFilter(filter: Filter): Long {
        val id: Long = nextId++
        filters.add(filter.copy(id = id))
        return id
    }
    override suspend fun updateFilter(filter: Filter) {
        val index: Int = filters.indexOfFirst { it.id == filter.id }
        if (index >= 0) filters[index] = filter
    }
    override suspend fun deleteFilter(id: Long) {
        filters.removeAll { it.id == id }
    }
    override suspend fun setEnabled(filterId: Long, isEnabled: Boolean) {
        val index: Int = filters.indexOfFirst { it.id == filterId }
        if (index >= 0) filters[index] = filters[index].copy(isEnabled = isEnabled)
    }
    override suspend fun getFilterNamesUsingTag(tagId: Long): List<String> = emptyList()
    override suspend fun getMaxPriority(): Int = filters.maxOfOrNull { it.priority } ?: 0
    override suspend fun setFilterOrder(orderedIds: List<Long>) = Unit
}

private class FakeMessageRepository : MessageRepository {
    val messages: MutableList<Message> = mutableListOf()
    val messageTags: MutableMap<Long, MutableSet<Long>> = mutableMapOf()
    private var nextId: Long = 1L
    fun seedMessage(message: Message, tagIds: Set<Long>): Long {
        val id: Long = nextId++
        messages.add(message.copy(id = id))
        messageTags[id] = tagIds.toMutableSet()
        return id
    }
    fun messageBySender(sender: String): Message = messages.first { it.sender == sender }
    fun tagIdsOf(messageId: Long): Set<Long> = messageTags[messageId].orEmpty()
    private fun update(messageId: Long, transform: (Message) -> Message) {
        val index: Int = messages.indexOfFirst { it.id == messageId }
        if (index >= 0) messages[index] = transform(messages[index])
    }
    override fun getAllMessages(): Flow<List<Message>> = flowOf(messages.toList())
    override fun getMessagesByTagId(tagId: Long): Flow<List<Message>> = flowOf(emptyList())
    override fun getMessagesByTagIds(tagIds: List<Long>): Flow<List<Message>> = flowOf(emptyList())
    override fun getPagedMessagesByTagIds(tagIds: List<Long>): Flow<PagingData<Message>> =
        throw UnsupportedOperationException()
    override fun getPagedAllMessages(): Flow<PagingData<Message>> = throw UnsupportedOperationException()
    override fun getPagedArchivedMessages(): Flow<PagingData<Message>> = throw UnsupportedOperationException()
    override fun getPagedTrashedMessages(): Flow<PagingData<Message>> = throw UnsupportedOperationException()
    override fun searchMessages(query: String): Flow<List<Message>> = flowOf(emptyList())
    override fun getArchivedMessages(): Flow<List<Message>> = flowOf(messages.filter { it.isArchived })
    override fun getTrashedMessages(): Flow<List<Message>> = flowOf(messages.filter { it.isTrashed })
    override suspend fun getMessageById(id: Long): Message? = messages.firstOrNull { it.id == id }
    override suspend fun getMessagesSince(since: Long): List<Message> = messages.filter { it.timestamp >= since }
    override suspend fun insertMessage(message: Message): Long = seedMessage(message, emptySet())
    override suspend fun updateMessage(message: Message) = update(message.id) { message }
    override suspend fun setArchived(messageId: Long, isArchived: Boolean) =
        update(messageId) { it.copy(isArchived = isArchived) }
    override suspend fun setTrashed(messageId: Long, isTrashed: Boolean) =
        update(messageId) { it.copy(isTrashed = isTrashed) }
    override suspend fun setArchivedBulk(messageIds: List<Long>, isArchived: Boolean) =
        messageIds.forEach { setArchived(it, isArchived) }
    override suspend fun setTrashedBulk(messageIds: List<Long>, isTrashed: Boolean) =
        messageIds.forEach { setTrashed(it, isTrashed) }
    override suspend fun deleteMessages(messageIds: List<Long>) {
        messages.removeAll { it.id in messageIds }
    }
    override suspend fun deleteAllTrashed() {
        messages.removeAll { it.isTrashed }
    }
    override suspend fun setRead(messageId: Long, isRead: Boolean) =
        update(messageId) { it.copy(isRead = isRead) }
    override suspend fun addTagToMessage(messageId: Long, tagId: Long) {
        messageTags.getOrPut(messageId) { mutableSetOf() }.add(tagId)
    }
    override suspend fun removeTagFromMessage(messageId: Long, tagId: Long) {
        messageTags[messageId]?.remove(tagId)
    }
    override suspend fun getTagIdsForMessage(messageId: Long): List<Long> =
        messageTags[messageId].orEmpty().toList()
    override suspend fun getAllMessagesOnce(): List<Message> = messages.toList()
    override suspend fun getTagIdsByMessage(): Map<Long, List<Long>> =
        messageTags.mapValues { it.value.toList() }
    override suspend fun replaceTagsForMessage(messageId: Long, tagIds: List<Long>) {
        messageTags[messageId] = tagIds.toMutableSet()
    }
    override suspend fun getMessageCount(): Int = messages.size
    override suspend fun getKnownDeviceMessageIds(): List<Long> =
        messages.mapNotNull { it.deviceMessageId }
    override suspend fun findMessageIdByContent(sender: String, body: String, timestamp: Long): Long? =
        messages.firstOrNull { it.sender == sender && it.body == body && it.timestamp == timestamp }?.id
    override suspend fun findUnlinkedMessageByContent(sender: String, body: String, timestamp: Long): Long? = null
    override suspend fun setDeviceMessageId(messageId: Long, deviceMessageId: Long) =
        update(messageId) { it.copy(deviceMessageId = deviceMessageId) }
    override suspend fun reconcileImported(messageId: Long, deviceMessageId: Long, sender: String, timestamp: Long) =
        update(messageId) { it.copy(deviceMessageId = deviceMessageId, sender = sender, timestamp = timestamp) }
    override suspend fun deleteDuplicates() = Unit
    override suspend fun deleteUnlinkedDuplicates() = Unit
}

private class FakeTransactionRepository : TransactionRepository {
    val transactions: MutableList<Transaction> = mutableListOf()
    val accounts: MutableList<Account> = mutableListOf()
    private var nextId: Long = 1L
    private var nextAccountId: Long = 1L
    fun seedTransaction(transaction: Transaction): Long {
        val id: Long = nextId++
        transactions.add(transaction.copy(id = id))
        return id
    }
    override fun getAllTransactions(): Flow<List<Transaction>> = flowOf(transactions.toList())
    override fun getTransactionsInRange(from: Long, to: Long): Flow<List<Transaction>> = flowOf(emptyList())
    override fun getTransactionsByAccount(accountId: Long): Flow<List<Transaction>> = flowOf(emptyList())
    override fun getTransactionsByAccountInRange(accountId: Long, from: Long, to: Long): Flow<List<Transaction>> =
        flowOf(emptyList())
    override fun getTransactionsByAccounts(accountIds: List<Long>): Flow<List<Transaction>> = flowOf(emptyList())
    override fun getTransactionsByAccountsInRange(accountIds: List<Long>, from: Long, to: Long): Flow<List<Transaction>> =
        flowOf(emptyList())
    override fun getAllAccounts(): Flow<List<Account>> = flowOf(accounts.toList())
    override suspend fun getAllAccountsOnce(): List<Account> = accounts.toList()
    override suspend fun getAccountById(id: Long): Account? = accounts.firstOrNull { it.id == id }
    override suspend fun findAccountByCodeAndTail(bankCode: String, accountTail: String): Account? =
        accounts.firstOrNull { it.bankCode == bankCode && it.accountTail == accountTail }
    override suspend fun findAccountByName(name: String): Account? =
        accounts.firstOrNull { it.bankName == name }
    override suspend fun insertAccount(account: Account): Long {
        val id: Long = nextAccountId++
        accounts.add(account.copy(id = id))
        return id
    }
    override suspend fun updateAccount(account: Account) {
        val index: Int = accounts.indexOfFirst { it.id == account.id }
        if (index >= 0) accounts[index] = account
    }
    override suspend fun deleteAccount(accountId: Long) {
        accounts.removeAll { it.id == accountId }
    }
    override fun getPendingAccountSuggestions(): Flow<List<AccountSuggestion>> = flowOf(emptyList())
    override suspend fun recordAccountSuggestion(bankCode: String, accountTail: String) = Unit
    override suspend fun dismissAccountSuggestion(id: Long) = Unit
    override suspend fun removeAccountSuggestion(bankCode: String, accountTail: String) = Unit
    override suspend fun getTransactionForMessage(messageId: Long): Transaction? =
        transactions.firstOrNull { it.messageId == messageId }
    override suspend fun getTransactionById(transactionId: Long): Transaction? =
        transactions.firstOrNull { it.id == transactionId }
    override suspend fun getTransactionsByAccountsOnce(accountIds: List<Long>): List<Transaction> = emptyList()
    override suspend fun getTransactionsWithBalanceOnce(): List<Transaction> =
        transactions.filter { it.balanceAfter != null }
    override suspend fun insertTransaction(transaction: Transaction): Long = seedTransaction(transaction)
    override suspend fun updateBalanceAfter(transactionId: Long, balance: Double?) {
        val index: Int = transactions.indexOfFirst { it.id == transactionId }
        if (index >= 0) transactions[index] = transactions[index].copy(balanceAfter = balance)
    }
    override suspend fun isDuplicate(accountId: Long, amount: Double, transactionTime: String): Boolean = false
    override suspend fun deleteTransactionsForMessage(messageId: Long) {
        transactions.removeAll { it.messageId == messageId && !it.isSentinel }
    }
    override suspend fun deleteSentinelsForMessage(messageId: Long) {
        transactions.removeAll { it.messageId == messageId && it.isSentinel }
    }
    override suspend fun deleteTransactionById(transactionId: Long) {
        transactions.removeAll { it.id == transactionId }
    }
    override suspend fun deleteAllTransactions() {
        transactions.clear()
    }
}

private class FakeBackupSettings(
    var themeMode: String = "SYSTEM",
    var appLockEnabled: Boolean = false
) : BackupSettingsStore {
    override suspend fun getThemeMode(): String = themeMode
    override suspend fun setThemeMode(value: String) {
        themeMode = value
    }
    override suspend fun getAppLockEnabled(): Boolean = appLockEnabled
    override suspend fun setAppLockEnabled(value: Boolean) {
        appLockEnabled = value
    }
}

private class Device {
    val tagRepository = FakeTagRepository()
    val filterRepository = FakeFilterRepository()
    val messageRepository = FakeMessageRepository()
    val transactionRepository = FakeTransactionRepository()
    val settings = FakeBackupSettings()
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
    fun importUseCase(): ImportDataUseCase {
        val categorizer = TransactionCategorizer(
            transactionParser = TransactionParser(defaultBankParsers()),
            transactionRepository = transactionRepository,
            messageRepository = messageRepository,
            tagRepository = tagRepository
        )
        return ImportDataUseCase(
            tagRepository = tagRepository,
            filterRepository = filterRepository,
            messageRepository = messageRepository,
            transactionRepository = transactionRepository,
            backupSettings = settings,
            json = testJson,
            recategorizeTransactionsUseCase = RecategorizeTransactionsUseCase(
                messageRepository = messageRepository,
                transactionRepository = transactionRepository,
                transactionCategorizer = categorizer
            )
        )
    }
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
        body: String? = null
    ): Message = Message(
        sender = sender,
        senderName = sender,
        body = body ?: "body of $sender",
        timestamp = timestamp,
        isRead = isRead,
        isArchived = isArchived,
        isTrashed = isTrashed,
        isOtp = isOtp,
        deviceMessageId = deviceMessageId
    )

    /** Parseable HDFC savings debit (no balance in body, so the 5000.5 override is user data). */
    private companion object {
        const val BALANCE_SENDER: String = "VM-HDFCBK"
        const val BALANCE_BODY: String =
            "Rs.100.00 debited from a/c XX2210 on 10-06-26. -HDFC Bank"
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
        source.transactionRepository.insertAccount(
            Account(
                bankName = "HDFC",
                accountTail = "2210",
                accountType = AccountType.SAVINGS,
                displayName = "HDFC Savings",
                bankCode = "HDFC",
                colorIndex = 1
            )
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
     * re-parsed transactions (balance not captured by the parser).
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
            "noDeviceId" to null
        )
        var timestamp = 1L
        for ((sender, deviceId) in senderToDeviceId) {
            val isOtp: Boolean = sender == "otp"
            val isBalance: Boolean = sender == BALANCE_SENDER
            val baseline: Set<Long> = if (isOtp) setOf(target.inboxTagId, target.otpTagId) else setOf(target.inboxTagId)
            val id: Long = target.messageRepository.seedMessage(
                message(
                    sender = sender,
                    timestamp = timestamp,
                    deviceMessageId = deviceId,
                    isOtp = isOtp,
                    body = if (isBalance) BALANCE_BODY else null
                ),
                baseline
            )
            if (isBalance) {
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
        assertEquals(4, bundle.version)
        val exportedSenders: Set<String> = bundle.messageStates.map { it.sender }.toSet()
        assertEquals(
            setOf(
                "read", "tagged", "archived", "trashed", "inboxRemoved",
                BALANCE_SENDER, "noDeviceId", "goneFromDevice"
            ),
            exportedSenders
        )
        assertFalse(exportedSenders.contains("plain"))
        assertFalse(exportedSenders.contains("otp"))
        val taggedState = bundle.messageStates.first { it.sender == "tagged" }
        assertEquals(listOf("Finance", "HDFC", "Inbox"), taggedState.tagNames)
        val balanceState = bundle.messageStates.first { it.sender == BALANCE_SENDER }
        assertEquals(5000.5, balanceState.balanceAfter!!, 0.0001)
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
        assertEquals(7, result.messagesRestored)
        assertEquals(1, result.messagesUnmatched)
        assertEquals(1, result.balancesRestored)
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
        assertEquals(7, second.messagesRestored)
        val financeTag: Tag? = target.tagRepository.getTagByName("Finance")
        assertNotNull(financeTag)
        assertNull(target.tagRepository.getTagByName("Finance (1)"))
        assertEquals(2, target.tagRepository.tabs.size)
        assertEquals(2, target.filterRepository.filters.size)
    }
}

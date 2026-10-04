package com.strings.app.domain.fakes

import androidx.paging.PagingData
import com.strings.app.domain.backup.BackupSettingsStore
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountSuggestion
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.LedgerEntry
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.TabConfig
import com.strings.app.domain.model.Tag
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory implementations of the repository interfaces for JVM use-case tests. They keep
 * the semantics the use cases rely on (ids, origin-aware deletes, tag sets) and nothing more.
 */
class FakeTagRepository : TagRepository {
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
    override suspend fun getDescendantTagIds(parentTagId: Long): List<Long> =
        tags.filter { it.parentTagId == parentTagId }.map { it.id }
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

class FakeFilterRepository : FilterRepository {
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

class FakeMessageRepository : MessageRepository {
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
    override suspend fun setTransactionExcluded(messageId: Long, isExcluded: Boolean) =
        update(messageId) { it.copy(isTransactionExcluded = isExcluded) }
    override suspend fun setDescription(messageId: Long, description: String?) =
        update(messageId) { it.copy(description = description?.trim()?.takeIf { text -> text.isNotEmpty() }) }
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

class FakeTransactionRepository : TransactionRepository {
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
    override fun getTransactionsByAccount(accountId: Long): Flow<List<Transaction>> = flowOf(emptyList())
    override fun getTransactionsByAccounts(accountIds: List<Long>): Flow<List<Transaction>> = flowOf(emptyList())
    override fun getLedgerInRange(from: Long, to: Long): Flow<List<LedgerEntry>> = flowOf(emptyList())
    override fun getLedgerByAccountsInRange(accountIds: List<Long>, from: Long, to: Long): Flow<List<LedgerEntry>> =
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
    override suspend fun getTransactionsForMessage(messageId: Long): List<Transaction> =
        transactions.filter { it.messageId == messageId && !it.isSentinel }.sortedBy { it.id }
    override suspend fun getTransactionForMessage(messageId: Long): Transaction? =
        getTransactionsForMessage(messageId).firstOrNull()
    override suspend fun getTransactionById(transactionId: Long): Transaction? =
        transactions.firstOrNull { it.id == transactionId }
    override suspend fun getAllTransactionsOnce(): List<Transaction> = transactions.toList()
    override suspend fun getTransactionsByAccountsOnce(accountIds: List<Long>): List<Transaction> =
        transactions.filter { it.accountId in accountIds }
    override suspend fun getTransactionsWithBalanceOnce(): List<Transaction> =
        transactions.filter { it.balanceAfter != null }
    override suspend fun insertTransaction(transaction: Transaction): Long = seedTransaction(transaction)
    override suspend fun updateTransaction(transaction: Transaction) {
        val index: Int = transactions.indexOfFirst { it.id == transaction.id }
        if (index >= 0) transactions[index] = transaction
    }
    override suspend fun updateBalanceAfter(transactionId: Long, balance: Double?) {
        val index: Int = transactions.indexOfFirst { it.id == transactionId }
        if (index >= 0) transactions[index] = transactions[index].copy(balanceAfter = balance)
    }
    override suspend fun isDuplicate(accountId: Long, amount: Double, transactionTime: String): Boolean = false
    override suspend fun deleteParsedTransactionsForMessage(messageId: Long) {
        transactions.removeAll { it.messageId == messageId && !it.isSentinel && !it.isLinked }
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

class FakeBackupSettings(
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

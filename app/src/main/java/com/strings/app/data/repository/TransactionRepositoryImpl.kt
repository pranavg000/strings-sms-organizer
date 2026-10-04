package com.strings.app.data.repository

import com.strings.app.data.local.db.dao.AccountDao
import com.strings.app.data.local.db.dao.AccountSuggestionDao
import com.strings.app.data.local.db.dao.TransactionDao
import com.strings.app.data.local.db.entity.AccountEntity
import com.strings.app.data.local.db.entity.AccountSuggestionEntity
import com.strings.app.data.local.db.entity.TransactionEntity
import com.strings.app.data.local.db.entity.TransactionWithDescription
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountSuggestion
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.model.LedgerEntry
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionOrigin
import com.strings.app.domain.model.TransactionType
import com.strings.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl(
    private val accountDao: AccountDao,
    private val accountSuggestionDao: AccountSuggestionDao,
    private val transactionDao: TransactionDao
) : TransactionRepository {
    override fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions().map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTransactionsByAccount(accountId: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByAccount(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTransactionsByAccounts(accountIds: List<Long>): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByAccounts(accountIds).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getLedgerInRange(from: Long, to: Long): Flow<List<LedgerEntry>> {
        return transactionDao.getLedgerInRange(from, to).map { rows -> rows.map { it.toDomain() } }
    }

    override fun getLedgerByAccountsInRange(accountIds: List<Long>, from: Long, to: Long): Flow<List<LedgerEntry>> {
        return transactionDao.getLedgerByAccountsInRange(accountIds, from, to).map { rows ->
            rows.map { it.toDomain() }
        }
    }

    override fun getAllAccounts(): Flow<List<Account>> {
        return accountDao.getAllAccounts().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getAllAccountsOnce(): List<Account> {
        return accountDao.getAllAccountsOnce().map { it.toDomain() }
    }

    override suspend fun getAccountById(id: Long): Account? {
        return accountDao.getAccountById(id)?.toDomain()
    }

    override suspend fun findAccountByCodeAndTail(bankCode: String, accountTail: String): Account? {
        return accountDao.findByCodeAndTail(bankCode, accountTail)?.toDomain()
    }

    override suspend fun findAccountByName(name: String): Account? {
        return accountDao.findByName(name)?.toDomain()
    }

    override suspend fun insertAccount(account: Account): Long {
        return accountDao.insertAccount(account.toEntity())
    }

    override suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account.toEntity())
    }

    override suspend fun deleteAccount(accountId: Long) {
        accountDao.clearParentReferences(accountId)
        accountDao.deleteById(accountId)
    }

    override fun getPendingAccountSuggestions(): Flow<List<AccountSuggestion>> {
        return accountSuggestionDao.getPending().map { entities ->
            entities.map { AccountSuggestion(id = it.id, bankCode = it.bankCode, accountTail = it.accountTail) }
        }
    }

    override suspend fun recordAccountSuggestion(bankCode: String, accountTail: String) {
        accountSuggestionDao.insert(
            AccountSuggestionEntity(bankCode = bankCode, accountTail = accountTail)
        )
    }

    override suspend fun dismissAccountSuggestion(id: Long) {
        accountSuggestionDao.dismiss(id)
    }

    override suspend fun removeAccountSuggestion(bankCode: String, accountTail: String) {
        accountSuggestionDao.delete(bankCode, accountTail)
    }

    override suspend fun getTransactionsForMessage(messageId: Long): List<Transaction> {
        return transactionDao.getTransactionsForMessage(messageId).map { it.toDomain() }
    }

    override suspend fun getTransactionForMessage(messageId: Long): Transaction? {
        return transactionDao.getTransactionsForMessage(messageId).firstOrNull()?.toDomain()
    }

    override suspend fun getTransactionById(transactionId: Long): Transaction? {
        return transactionDao.getTransactionById(transactionId)?.toDomain()
    }

    override suspend fun getAllTransactionsOnce(): List<Transaction> {
        return transactionDao.getAllTransactionsOnce().map { it.toDomain() }
    }

    override suspend fun getTransactionsByAccountsOnce(accountIds: List<Long>): List<Transaction> {
        return transactionDao.getTransactionsByAccountsOnce(accountIds).map { it.toDomain() }
    }

    override suspend fun getTransactionsWithBalanceOnce(): List<Transaction> {
        return transactionDao.getTransactionsWithBalance().map { it.toDomain() }
    }

    override suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insertTransaction(transaction.toEntity())
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction.toEntity())
    }

    override suspend fun updateBalanceAfter(transactionId: Long, balance: Double?) {
        transactionDao.updateBalanceAfter(transactionId, balance)
    }

    override suspend fun isDuplicate(accountId: Long, amount: Double, transactionTime: String): Boolean {
        val recent: List<TransactionEntity> = transactionDao.getRecentByAccount(accountId, 2)
        return recent.any { it.amount == amount && it.transactionTime == transactionTime }
    }

    override suspend fun deleteParsedTransactionsForMessage(messageId: Long) {
        transactionDao.deleteParsedByMessageId(messageId)
    }

    override suspend fun deleteSentinelsForMessage(messageId: Long) {
        transactionDao.deleteSentinelsByMessageId(messageId)
    }

    override suspend fun deleteTransactionById(transactionId: Long) {
        transactionDao.deleteById(transactionId)
    }

    override suspend fun deleteAllTransactions() {
        transactionDao.deleteAll()
    }

    private fun AccountEntity.toDomain(): Account = Account(
        id = id,
        bankName = bankName,
        accountTail = accountTail,
        accountType = AccountType.valueOf(accountType),
        displayName = displayName,
        bankCode = bankCode,
        colorIndex = colorIndex,
        parentAccountId = parentAccountId,
        isEnabled = isEnabled
    )

    private fun Account.toEntity(): AccountEntity = AccountEntity(
        id = id,
        bankName = bankName,
        accountTail = accountTail,
        accountType = accountType.name,
        displayName = displayName,
        bankCode = bankCode,
        colorIndex = colorIndex,
        parentAccountId = parentAccountId,
        isEnabled = isEnabled
    )

    private fun TransactionWithDescription.toDomain(): LedgerEntry = LedgerEntry(
        transaction = transaction.toDomain(),
        messageDescription = messageDescription
    )

    private fun TransactionEntity.toDomain(): Transaction = Transaction(
        id = id,
        messageId = messageId,
        accountId = accountId,
        amount = amount,
        type = TransactionType.valueOf(type),
        balanceAfter = balanceAfter,
        merchant = merchant,
        transactionTime = transactionTime,
        timestamp = timestamp,
        rawMatch = rawMatch,
        origin = TransactionOrigin.valueOf(origin)
    )

    private fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
        id = id,
        messageId = messageId,
        accountId = accountId,
        amount = amount,
        type = type.name,
        balanceAfter = balanceAfter,
        merchant = merchant,
        transactionTime = transactionTime,
        timestamp = timestamp,
        rawMatch = rawMatch,
        origin = origin.name
    )
}

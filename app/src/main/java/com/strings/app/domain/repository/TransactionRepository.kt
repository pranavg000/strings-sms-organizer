package com.strings.app.domain.repository

import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountSuggestion
import com.strings.app.domain.model.LedgerEntry
import com.strings.app.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    fun getTransactionsByAccount(accountId: Long): Flow<List<Transaction>>
    fun getTransactionsByAccounts(accountIds: List<Long>): Flow<List<Transaction>>
    fun getLedgerInRange(from: Long, to: Long): Flow<List<LedgerEntry>>
    fun getLedgerByAccountsInRange(accountIds: List<Long>, from: Long, to: Long): Flow<List<LedgerEntry>>
    fun getAllAccounts(): Flow<List<Account>>
    suspend fun getAllAccountsOnce(): List<Account>
    suspend fun getAccountById(id: Long): Account?
    suspend fun findAccountByCodeAndTail(bankCode: String, accountTail: String): Account?
    suspend fun findAccountByName(name: String): Account?
    suspend fun insertAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun deleteAccount(accountId: Long)
    fun getPendingAccountSuggestions(): Flow<List<AccountSuggestion>>
    suspend fun recordAccountSuggestion(bankCode: String, accountTail: String)
    suspend fun dismissAccountSuggestion(id: Long)
    suspend fun removeAccountSuggestion(bankCode: String, accountTail: String)
    /** The message's own transactions (parsed or linked); sentinels sharing its id are excluded. */
    suspend fun getTransactionsForMessage(messageId: Long): List<Transaction>
    suspend fun getTransactionForMessage(messageId: Long): Transaction?
    suspend fun getTransactionById(transactionId: Long): Transaction?
    suspend fun getAllTransactionsOnce(): List<Transaction>
    suspend fun getTransactionsByAccountsOnce(accountIds: List<Long>): List<Transaction>
    suspend fun getTransactionsWithBalanceOnce(): List<Transaction>
    suspend fun insertTransaction(transaction: Transaction): Long
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun updateBalanceAfter(transactionId: Long, balance: Double?)
    suspend fun isDuplicate(accountId: Long, amount: Double, transactionTime: String): Boolean
    /** Removes only parser-produced rows; sentinels and user-linked rows are left alone. */
    suspend fun deleteParsedTransactionsForMessage(messageId: Long)
    suspend fun deleteSentinelsForMessage(messageId: Long)
    suspend fun deleteTransactionById(transactionId: Long)
    suspend fun deleteAllTransactions()
}

package com.strings.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.strings.app.data.local.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getTransactionsByAccount(accountId: Long): Flow<List<TransactionEntity>>

    // Sentinel rows share the anchor message's id, so message-scoped reads return only the
    // real parsed transaction.
    @Query("SELECT * FROM transactions WHERE messageId = :messageId AND isSentinel = 0")
    suspend fun getTransactionsForMessage(messageId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE accountId IN (:accountIds) ORDER BY timestamp DESC")
    suspend fun getTransactionsByAccountsOnce(accountIds: List<Long>): List<TransactionEntity>

    // Excludes sentinels so re-categorizing the anchor message never wipes an
    // unaccounted-amount placeholder (those are removed manually by the user).
    @Query("DELETE FROM transactions WHERE messageId = :messageId AND isSentinel = 0")
    suspend fun deleteByMessageId(messageId: Long)

    @Query("DELETE FROM transactions WHERE messageId = :messageId AND isSentinel = 1")
    suspend fun deleteSentinelsByMessageId(messageId: Long)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun getTransactionsInRange(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun getTransactionsByAccountInRange(accountId: Long, from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId IN (:accountIds) ORDER BY timestamp DESC")
    fun getTransactionsByAccounts(accountIds: List<Long>): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId IN (:accountIds) AND timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun getTransactionsByAccountsInRange(accountIds: List<Long>, from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE balanceAfter IS NOT NULL")
    suspend fun getTransactionsWithBalance(): List<TransactionEntity>

    @Query("UPDATE transactions SET balanceAfter = :balance WHERE id = :transactionId")
    suspend fun updateBalanceAfter(transactionId: Long, balance: Double?)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentByAccount(accountId: Long, limit: Int): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long
}

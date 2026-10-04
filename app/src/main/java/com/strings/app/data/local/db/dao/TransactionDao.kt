package com.strings.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.strings.app.data.local.db.entity.TransactionEntity
import com.strings.app.data.local.db.entity.TransactionWithDescription
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getTransactionsByAccount(accountId: Long): Flow<List<TransactionEntity>>

    // Sentinel rows share the anchor message's id, so message-scoped reads return only the
    // transactions that belong to the message (parsed or linked by the user).
    @Query("SELECT * FROM transactions WHERE messageId = :messageId AND origin != 'SENTINEL' ORDER BY id")
    suspend fun getTransactionsForMessage(messageId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE accountId IN (:accountIds) ORDER BY timestamp DESC")
    suspend fun getTransactionsByAccountsOnce(accountIds: List<Long>): List<TransactionEntity>

    // Only parser output is owned by re-categorization: sentinels and user-linked rows stay.
    @Query("DELETE FROM transactions WHERE messageId = :messageId AND origin = 'PARSED'")
    suspend fun deleteParsedByMessageId(messageId: Long)

    @Query("DELETE FROM transactions WHERE messageId = :messageId AND origin = 'SENTINEL'")
    suspend fun deleteSentinelsByMessageId(messageId: Long)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    @Query(
        "SELECT t.*, m.description AS messageDescription FROM transactions t " +
            "LEFT JOIN messages m ON m.id = t.messageId " +
            "WHERE t.timestamp BETWEEN :from AND :to ORDER BY t.timestamp DESC"
    )
    fun getLedgerInRange(from: Long, to: Long): Flow<List<TransactionWithDescription>>

    @Query(
        "SELECT t.*, m.description AS messageDescription FROM transactions t " +
            "LEFT JOIN messages m ON m.id = t.messageId " +
            "WHERE t.accountId IN (:accountIds) AND t.timestamp BETWEEN :from AND :to " +
            "ORDER BY t.timestamp DESC"
    )
    fun getLedgerByAccountsInRange(
        accountIds: List<Long>,
        from: Long,
        to: Long
    ): Flow<List<TransactionWithDescription>>

    @Query("SELECT * FROM transactions WHERE accountId IN (:accountIds) ORDER BY timestamp DESC")
    fun getTransactionsByAccounts(accountIds: List<Long>): Flow<List<TransactionEntity>>

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

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)
}

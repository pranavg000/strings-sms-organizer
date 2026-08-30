package com.strings.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.strings.app.data.local.db.entity.AccountSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountSuggestionDao {
    @Query("SELECT * FROM account_suggestions WHERE status = 'PENDING' ORDER BY id DESC")
    fun getPending(): Flow<List<AccountSuggestionEntity>>

    // IGNORE keeps an existing row (including a DISMISSED one) so re-ingesting the same
    // sender never resurrects a dismissed suggestion.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(suggestion: AccountSuggestionEntity): Long

    @Query("UPDATE account_suggestions SET status = 'DISMISSED' WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("DELETE FROM account_suggestions WHERE bankCode = :bankCode AND accountTail = :accountTail")
    suspend fun delete(bankCode: String, accountTail: String)
}

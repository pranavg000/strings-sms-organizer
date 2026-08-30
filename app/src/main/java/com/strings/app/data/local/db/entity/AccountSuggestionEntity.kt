package com.strings.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A detected-but-unconfigured account: a transactional SMS from a supported bank whose
 * tail digits match none of the user's accounts. Unique per (bankCode, accountTail) so
 * repeated messages never duplicate a suggestion; dismissed rows are kept so the same
 * suggestion is not re-raised.
 */
@Entity(
    tableName = "account_suggestions",
    indices = [Index(value = ["bankCode", "accountTail"], unique = true)]
)
data class AccountSuggestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bankCode: String,
    val accountTail: String,
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING: String = "PENDING"
        const val STATUS_DISMISSED: String = "DISMISSED"
    }
}

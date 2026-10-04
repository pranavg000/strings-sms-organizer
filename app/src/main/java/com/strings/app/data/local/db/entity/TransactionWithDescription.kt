package com.strings.app.data.local.db.entity

import androidx.room.Embedded

/**
 * Query projection for ledger lists: a transaction joined with its message's description.
 * Room re-emits the owning Flow when either table changes.
 */
data class TransactionWithDescription(
    @Embedded val transaction: TransactionEntity,
    val messageDescription: String?
)

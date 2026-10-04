package com.strings.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Indices mirror the hot query shapes: every list orders by [timestamp]; the inbox,
 * archived and trash lists filter on [isTrashed]/[isArchived] first; ingest dedups on
 * [deviceMessageId]; content lookups and the live-vs-provider reconcile key on
 * ([sender], [timestamp]). [body] is deliberately not indexed (too wide to help).
 * Adding or changing one requires a schema version bump plus a `CREATE INDEX` migration
 * using Room's generated name (`index_messages_<col>[_<col>...]`).
 */
@Entity(
    tableName = "messages",
    indices = [
        Index("timestamp"),
        Index(value = ["isTrashed", "isArchived", "timestamp"]),
        Index("deviceMessageId"),
        Index(value = ["sender", "timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sender: String,
    val senderName: String,
    val body: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val isOtp: Boolean = false,
    val otpCode: String? = null,
    val deviceMessageId: Long? = null,
    val isTransactionExcluded: Boolean = false,
    val description: String? = null
)

package com.strings.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
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
    val deviceMessageId: Long? = null
)

package com.strings.app.domain.model

data class Message(
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
    val tags: List<Tag> = emptyList()
)

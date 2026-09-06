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
    /**
     * User override: this message must never be treated as a transaction. The categorizer
     * skips parsing while it is set, so a batch re-categorization can't resurrect it.
     */
    val isTransactionExcluded: Boolean = false,
    val tags: List<Tag> = emptyList()
)

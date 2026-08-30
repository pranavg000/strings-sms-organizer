package com.strings.app.domain.transaction

import kotlinx.serialization.Serializable

/**
 * Diagnostic, read-only output of running the parser over a window of messages WITHOUT writing
 * any accounts/transactions. Used to review what the parser would categorize so custom rules can
 * be authored before turning on auto-population.
 */
@Serializable
data class CategorizationReport(
    val generatedAt: Long,
    val windowDays: Int,
    val scanned: Int,
    val categorized: Int,
    val messages: List<CategorizedMessageDto>
)

@Serializable
data class CategorizedMessageDto(
    val messageId: Long,
    val sender: String,
    val timestamp: Long,
    val body: String,
    val amount: Double,
    val type: String,
    val sourceName: String,
    val displayName: String,
    val accountType: String,
    val accountTail: String,
    val balanceAfter: Double? = null,
    val merchant: String? = null,
    val transactionTime: String? = null,
    val rawMatch: String
)

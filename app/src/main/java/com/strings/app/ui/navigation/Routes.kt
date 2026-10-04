package com.strings.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object InboxRoute

@Serializable
object SearchRoute

@Serializable
object AllMessagesRoute

@Serializable
object ArchivedMessagesRoute

@Serializable
object TrashedMessagesRoute

@Serializable
object FilterListRoute

@Serializable
data class FilterEditRoute(val filterId: Long = -1L)

@Serializable
object TagListRoute

@Serializable
data class TagEditRoute(val tagId: Long = -1L)

@Serializable
data class MessageDetailRoute(val messageId: Long)

@Serializable
data class TagMessagesRoute(val tagId: Long)

@Serializable
data class FilterMessagesRoute(val filterId: Long)

@Serializable
object FinanceDashboardRoute

@Serializable
data class AccountDetailRoute(val accountId: Long)

@Serializable
object ManageAccountsRoute

/** Message picker that attaches the given sentinel / linked transaction to the chosen message. */
@Serializable
data class LinkTransactionRoute(val transactionId: Long)

@Serializable
data class AccountEditRoute(
    val accountId: Long = -1L,
    val prefillBankCode: String = "",
    val prefillTail: String = ""
)

@Serializable
object SettingsRoute

@Serializable
object HelpRoute

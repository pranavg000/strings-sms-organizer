package com.strings.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.strings.app.MainActivity
import com.strings.app.R
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.TransactionType
import com.strings.app.domain.sms.SenderPrincipal
import com.strings.app.domain.transaction.BalanceDiscrepancy
import com.strings.app.domain.transaction.ParsedTransaction
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

class SmsNotifier(private val context: Context) {
    init {
        ensureChannels()
    }

    private fun ensureChannels() {
        val messagesChannel = NotificationChannel(
            CHANNEL_ID,
            "Messages",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "New SMS messages"
        }
        val otpChannel = NotificationChannel(
            OTP_CHANNEL_ID,
            "OTP Codes",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "One-time passwords with a copy action"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val transactionChannel = NotificationChannel(
            TRANSACTION_CHANNEL_ID,
            "Transactions",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Bank and wallet transactions"
        }
        val balanceAlertChannel = NotificationChannel(
            BALANCE_ALERT_CHANNEL_ID,
            "Balance Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reported balance doesn't tally with tracked transactions"
        }
        val silentChannel = NotificationChannel(
            SILENT_CHANNEL_ID,
            "Silent Messages",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Messages a filter marked as silent: no sound or pop-up"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(messagesChannel)
        manager.createNotificationChannel(otpChannel)
        manager.createNotificationChannel(transactionChannel)
        manager.createNotificationChannel(balanceAlertChannel)
        manager.createNotificationChannel(silentChannel)
    }

    /** Silent notifications post on the low-importance channel: shown in the shade but no sound/heads-up. */
    private fun channelFor(defaultChannelId: String, silent: Boolean): String =
        if (silent) SILENT_CHANNEL_ID else defaultChannelId

    fun notifyNewMessage(message: Message, silent: Boolean = false) {
        if (!hasPermission()) return
        val title: String = message.senderName.ifBlank { message.sender }
        val notificationId: Int = message.id.toInt()
        val notification = NotificationCompat.Builder(context, channelFor(CHANNEL_ID, silent))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setSubText(message.sender)
            .setWhen(message.timestamp)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(openMessagePendingIntent(message.id, notificationId))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        postGroupSummary()
    }

    fun notifyOtp(message: Message, silent: Boolean = false) {
        if (!hasPermission()) return
        val code: String = message.otpCode ?: return
        val sender: String = displaySender(message)
        val notificationId: Int = message.id.toInt()
        val copyIntent: PendingIntent = copyPendingIntent(code, notificationId)
        val collapsedViews: RemoteViews = buildOtpRemoteViews(
            R.layout.notification_otp, code
        ).apply {
            setTextViewText(R.id.otp_label, sender)
        }
        val expandedViews: RemoteViews = buildOtpRemoteViews(
            R.layout.notification_otp_expanded, code
        ).apply {
            setTextViewText(R.id.otp_body, message.body)
        }
        // Shown on the device lock screen instead of the real notification:
        // no code, no copy action, no message body.
        val publicVersion: Notification = NotificationCompat.Builder(context, OTP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("OTP from $sender")
            .setContentText("Unlock to view the code")
            .setWhen(message.timestamp)
            .setShowWhen(true)
            .setAutoCancel(true)
            .build()
        val notification = NotificationCompat.Builder(context, channelFor(OTP_CHANNEL_ID, silent))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("OTP from $sender")
            .setSubText(sender)
            .setWhen(message.timestamp)
            .setShowWhen(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(openMessagePendingIntent(message.id, notificationId))
            .addAction(android.R.drawable.ic_menu_set_as, "Copy OTP", copyIntent)
            .setTimeoutAfter(OTP_TIMEOUT_MS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        postGroupSummary()
    }

    fun notifyTransaction(message: Message, parsed: ParsedTransaction, silent: Boolean = false) {
        Log.d(TAG, "notifyTransaction: id=${message.id} amount=${parsed.amount} type=${parsed.type}")
        if (!hasPermission()) {
            Log.w(TAG, "notifyTransaction: POST_NOTIFICATIONS not granted, skipping")
            return
        }
        val notificationId: Int = message.id.toInt()
        val isCredit: Boolean = parsed.type == TransactionType.CREDIT
        val prefix: String = if (isCredit) "+ " else "- "
        val amountText: String = prefix + currencyFormat.format(parsed.amount)
        val accentColorRes: Int =
            if (isCredit) R.color.notification_credit else R.color.notification_debit
        val accentColor: Int = ContextCompat.getColor(context, accentColorRes)
        val typeLabel: String = if (isCredit) "Credit" else "Debit"
        val accountLabel: String = formatAccountTail(parsed)
        val sourceLine: String = listOf(parsed.account.displayName, accountLabel)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val collapsedViews: RemoteViews = buildTransactionRemoteViews(
            R.layout.notification_transaction,
            amountText, sourceLine, accentColor
        ).apply {
            setTextViewText(R.id.txn_body_preview, message.body)
        }
        val expandedViews: RemoteViews = buildTransactionRemoteViews(
            R.layout.notification_transaction_expanded,
            amountText, sourceLine, accentColor
        ).apply {
            if (parsed.balanceAfter != null) {
                setTextViewText(
                    R.id.txn_balance,
                    "Balance: ${currencyFormat.format(parsed.balanceAfter)}"
                )
                setViewVisibility(R.id.txn_balance, View.VISIBLE)
            }
            setTextViewText(R.id.txn_body, message.body)
        }
        val notification = NotificationCompat.Builder(context, channelFor(TRANSACTION_CHANNEL_ID, silent))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(parsed.account.displayName)
            .setSubText(typeLabel)
            .setWhen(message.timestamp)
            .setShowWhen(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(openMessagePendingIntent(message.id, notificationId))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        Log.d(
            TAG,
            "notifyTransaction: posted id=$notificationId " +
                "channelEnabled=${isChannelEnabled(TRANSACTION_CHANNEL_ID)} " +
                "appNotificationsEnabled=${NotificationManagerCompat.from(context).areNotificationsEnabled()}"
        )
        postGroupSummary()
    }

    fun notifyBalanceDiscrepancy(message: Message, parsed: ParsedTransaction, discrepancy: BalanceDiscrepancy) {
        if (!hasPermission()) return
        val accountLabel: String = formatAccountTail(parsed)
        val titleSource: String = listOf(parsed.account.displayName, accountLabel)
            .filter { it.isNotBlank() }
            .joinToString(" \u00b7 ")
        val body: String = "SMS reports ${currencyFormat.format(discrepancy.reportedBalance)} but tracked " +
            "transactions expect ${currencyFormat.format(discrepancy.expectedBalance)} \u2014 " +
            "${currencyFormat.format(abs(discrepancy.difference))} unaccounted."
        val notificationId: Int = BALANCE_ALERT_ID_BASE + discrepancy.accountId.toInt()
        val notification = NotificationCompat.Builder(context, BALANCE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Balance mismatch \u00b7 $titleSource")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setWhen(message.timestamp)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(openMessagePendingIntent(message.id, notificationId))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        postGroupSummary()
    }

    /**
     * Human-friendly sender for display: the contact name when resolved,
     * otherwise the DLT principal of the shortcode (JM-HDFCBK-T -> HDFCBK).
     */
    private fun displaySender(message: Message): String {
        if (message.senderName.isNotBlank() && message.senderName != message.sender) {
            return message.senderName
        }
        return SenderPrincipal.principal(message.sender)
    }

    private fun isChannelEnabled(channelId: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel: NotificationChannel? = manager.getNotificationChannel(channelId)
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun buildTransactionRemoteViews(
        layoutRes: Int,
        amountText: String,
        sourceLine: String,
        accentColor: Int
    ): RemoteViews {
        return RemoteViews(context.packageName, layoutRes).apply {
            setTextViewText(R.id.txn_source, sourceLine)
            setTextViewText(R.id.txn_amount, amountText)
            setTextColor(R.id.txn_amount, accentColor)
            setInt(R.id.accent_strip, "setBackgroundColor", accentColor)
        }
    }

    private fun formatAccountTail(parsed: ParsedTransaction): String {
        if (parsed.account.accountTail.isBlank()) return ""
        return when (parsed.account.accountType) {
            AccountType.CREDIT_CARD -> "Card \u2022\u2022${parsed.account.accountTail}"
            AccountType.SAVINGS -> "A/c \u2022\u2022${parsed.account.accountTail}"
            AccountType.WALLET -> ""
        }
    }

    private fun buildOtpRemoteViews(layoutRes: Int, code: String): RemoteViews {
        val digitIds: IntArray = intArrayOf(
            R.id.otp_d0, R.id.otp_d1, R.id.otp_d2, R.id.otp_d3,
            R.id.otp_d4, R.id.otp_d5, R.id.otp_d6, R.id.otp_d7
        )
        return RemoteViews(context.packageName, layoutRes).apply {
            for (i in digitIds.indices) {
                if (i < code.length) {
                    setTextViewText(digitIds[i], code[i].toString())
                    setViewVisibility(digitIds[i], View.VISIBLE)
                } else {
                    setViewVisibility(digitIds[i], View.GONE)
                }
            }
        }
    }

    private fun postGroupSummary() {
        if (!hasPermission()) return
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText("New messages")
            )
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    private fun openMessagePendingIntent(messageId: Long, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_MESSAGE_ID, messageId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun copyPendingIntent(code: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, OtpCopyReceiver::class.java).apply {
            putExtra(OtpCopyReceiver.EXTRA_OTP_CODE, code)
            putExtra(OtpCopyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "StringsNotify"
        private const val CHANNEL_ID = "sms_messages"
        private const val OTP_CHANNEL_ID = "otp_codes"
        private const val TRANSACTION_CHANNEL_ID = "transactions"
        private const val BALANCE_ALERT_CHANNEL_ID = "balance_alerts"
        private const val SILENT_CHANNEL_ID = "silent_messages"
        private const val GROUP_KEY = "strings_messages"
        private const val SUMMARY_NOTIFICATION_ID = 0
        private const val BALANCE_ALERT_ID_BASE = 1_000_000
        private const val OTP_TIMEOUT_MS = 5 * 60 * 1000L
        private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
}

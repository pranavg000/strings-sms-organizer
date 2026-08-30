package com.strings.app.notification

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

class OtpCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code: String = intent.getStringExtra(EXTRA_OTP_CODE) ?: return
        val notificationId: Int = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OTP", code))
        Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show()
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }

    companion object {
        const val EXTRA_OTP_CODE: String = "extra_otp_code"
        const val EXTRA_NOTIFICATION_ID: String = "extra_notification_id"
    }
}

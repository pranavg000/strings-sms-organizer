package com.strings.app.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.strings.app.domain.model.Message
import com.strings.app.domain.usecase.SyncSmsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SmsReceiver : BroadcastReceiver(), KoinComponent {
    private val syncSmsUseCase: SyncSmsUseCase by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val sender: String = parts.first().displayOriginatingAddress
            ?: parts.first().originatingAddress
            ?: "Unknown"
        val body: String = parts.joinToString(separator = "") { it.messageBody ?: "" }
        val timestamp: Long = parts.first().timestampMillis
        val message = Message(
            sender = sender,
            senderName = sender,
            body = body,
            timestamp = timestamp,
            deviceMessageId = null
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncSmsUseCase.ingest(message, notify = true)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

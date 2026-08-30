package com.strings.app.data.sms

import android.content.Context
import android.provider.Telephony
import com.strings.app.domain.model.Message

data class RawSms(
    val deviceMessageId: Long,
    val address: String,
    val body: String,
    val timestamp: Long
)

class SmsContentReader(private val context: Context) {
    fun readInbox(sinceTimestamp: Long = 0L): List<RawSms> {
        val projection: Array<String> = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection: String? = if (sinceTimestamp > 0L) "${Telephony.Sms.DATE} > ?" else null
        val selectionArgs: Array<String>? =
            if (sinceTimestamp > 0L) arrayOf(sinceTimestamp.toString()) else null
        val results: MutableList<RawSms> = mutableListOf()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idIndex: Int = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex: Int = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex: Int = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex: Int = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val deviceMessageId: Long = cursor.getLong(idIndex)
                val address: String = cursor.getString(addressIndex) ?: "Unknown"
                val body: String = cursor.getString(bodyIndex) ?: ""
                val timestamp: Long = cursor.getLong(dateIndex)
                results.add(
                    RawSms(
                        deviceMessageId = deviceMessageId,
                        address = address,
                        body = body,
                        timestamp = timestamp
                    )
                )
            }
        }
        return results
    }

    fun toMessage(raw: RawSms): Message = Message(
        sender = raw.address,
        senderName = raw.address,
        body = raw.body,
        timestamp = raw.timestamp,
        deviceMessageId = raw.deviceMessageId
    )
}

package com.strings.app.domain.usecase

import android.util.Log
import com.strings.app.data.contacts.ContactNameResolver
import com.strings.app.data.prefs.SettingsDataStore
import com.strings.app.data.sms.RawSms
import com.strings.app.data.sms.SmsContentReader
import com.strings.app.domain.model.Message
import com.strings.app.domain.otp.OtpDetector
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.transaction.BalanceDiscrepancy
import com.strings.app.domain.transaction.ParsedTransaction
import com.strings.app.domain.transaction.TransactionCategorizer
import com.strings.app.notification.SmsNotifier
import com.strings.app.util.DatabaseSeeder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncSmsUseCase(
    private val messageRepository: MessageRepository,
    private val applyFiltersUseCase: ApplyFiltersUseCase,
    private val smsContentReader: SmsContentReader,
    private val seeder: DatabaseSeeder,
    private val settings: SettingsDataStore,
    private val notifier: SmsNotifier,
    private val otpDetector: OtpDetector,
    private val contactNameResolver: ContactNameResolver,
    private val transactionCategorizer: TransactionCategorizer,
    private val checkBalanceDiscrepancy: CheckBalanceDiscrepancyUseCase
) {
    suspend fun importAll(): Int = importLock.withLock {
        seeder.setupFirstRun()
        messageRepository.deleteDuplicates()
        messageRepository.deleteUnlinkedDuplicates()
        val since: Long = settings.getLastSyncTimestamp()
        val rawMessages: List<RawSms> = smsContentReader.readInbox(since).sortedBy { it.timestamp }
        if (rawMessages.isEmpty()) return@withLock 0
        val knownDeviceIds: MutableSet<Long> = messageRepository.getKnownDeviceMessageIds().toMutableSet()
        var importedCount = 0
        var maxTimestamp: Long = since
        for (raw in rawMessages) {
            if (raw.deviceMessageId in knownDeviceIds) continue
            val message: Message = smsContentReader.toMessage(raw)
            val existingId: Long? = messageRepository.findMessageIdByContent(
                sender = message.sender,
                body = message.body,
                timestamp = message.timestamp
            ) ?: messageRepository.findUnlinkedMessageByContent(
                sender = message.sender,
                body = message.body,
                timestamp = message.timestamp
            )
            if (existingId != null) {
                messageRepository.reconcileImported(
                    messageId = existingId,
                    deviceMessageId = raw.deviceMessageId,
                    sender = message.sender,
                    timestamp = message.timestamp
                )
            } else {
                persist(message, notify = false)
                importedCount++
            }
            knownDeviceIds.add(raw.deviceMessageId)
            if (raw.timestamp > maxTimestamp) maxTimestamp = raw.timestamp
        }
        if (maxTimestamp > since) settings.setLastSyncTimestamp(maxTimestamp)
        importedCount
    }

    suspend fun ingest(message: Message, notify: Boolean) = importLock.withLock {
        seeder.setupFirstRun()
        val deviceMessageId: Long? = message.deviceMessageId
        if (deviceMessageId != null && deviceMessageId in messageRepository.getKnownDeviceMessageIds()) {
            return@withLock
        }
        persist(message, notify)
        if (message.timestamp > settings.getLastSyncTimestamp()) {
            settings.setLastSyncTimestamp(message.timestamp)
        }
    }

    private suspend fun persist(message: Message, notify: Boolean) {
        val otpCode: String? = otpDetector.detect(message.body)
        val toStore: Message = message.copy(isOtp = otpCode != null, otpCode = otpCode)
        val newId: Long = messageRepository.insertMessage(toStore)
        val inboxTagId: Long = settings.getInboxTagId()
        if (inboxTagId > 0L) {
            messageRepository.addTagToMessage(newId, inboxTagId)
        }
        if (otpCode != null) {
            val otpTagId: Long = seeder.ensureOtpTagId()
            if (otpTagId > 0L) {
                messageRepository.addTagToMessage(newId, otpTagId)
            }
        }
        val parsedTransaction: ParsedTransaction? = if (otpCode == null) {
            runCatching { transactionCategorizer.categorize(toStore.copy(id = newId)) }
                .onFailure { Log.e(TAG, "persist: categorize threw for message $newId", it) }
                .getOrNull()
        } else {
            null
        }
        val displayName: String = contactNameResolver.resolve(message.sender) ?: message.senderName
        val storedMessage: Message = toStore.copy(id = newId, senderName = displayName)
        val outcome: FilterOutcome = applyFiltersUseCase.applyToMessage(storedMessage)
        Log.d(
            TAG,
            "persist: id=$newId sender=${message.sender} notify=$notify " +
                "otp=${otpCode != null} parsed=${parsedTransaction != null} " +
                "suppressed=${outcome.suppressNotification} silent=${outcome.notifySilently}"
        )
        if (notify && !outcome.suppressNotification) {
            val silent: Boolean = outcome.notifySilently
            if (storedMessage.isOtp) {
                notifier.notifyOtp(storedMessage, silent)
            } else if (parsedTransaction != null) {
                notifier.notifyTransaction(storedMessage, parsedTransaction, silent)
            } else {
                notifier.notifyNewMessage(storedMessage, silent)
            }
        }
        if (notify && parsedTransaction?.balanceAfter != null) {
            val discrepancy: BalanceDiscrepancy? =
                runCatching { checkBalanceDiscrepancy.forMessage(newId) }
                    .onFailure { Log.e(TAG, "persist: balance check threw for message $newId", it) }
                    .getOrNull()
            if (discrepancy != null) {
                notifier.notifyBalanceDiscrepancy(storedMessage, parsedTransaction, discrepancy)
            }
        }
    }

    companion object {
        private val importLock = Mutex()
        private const val TAG: String = "StringsNotify"
    }
}

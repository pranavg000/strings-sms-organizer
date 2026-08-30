package com.strings.app.domain.usecase

import com.strings.app.domain.backup.AccountDto
import com.strings.app.domain.backup.BackupBundle
import com.strings.app.domain.backup.BackupSettingsStore
import com.strings.app.domain.backup.FilterActionDto
import com.strings.app.domain.backup.FilterDto
import com.strings.app.domain.backup.MessageStateDto
import com.strings.app.domain.backup.SettingsDto
import com.strings.app.domain.backup.TabConfigDto
import com.strings.app.domain.backup.TagDto
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.TabConfig
import com.strings.app.domain.model.Tag
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.util.SystemTags
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class ExportDataUseCase(
    private val tagRepository: TagRepository,
    private val filterRepository: FilterRepository,
    private val messageRepository: MessageRepository,
    private val transactionRepository: TransactionRepository,
    private val backupSettings: BackupSettingsStore
) {
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun execute(): String {
        val tags: List<Tag> = tagRepository.getAllTags().first()
        val tagNameById: Map<Long, String> = tags.associate { it.id to it.name }
        val tagDtos: List<TagDto> = tags.map { tag ->
            TagDto(
                name = tag.name,
                color = tag.color,
                icon = tag.icon,
                parentName = tag.parentTagId?.let { tagNameById[it] },
                sortOrder = tag.sortOrder,
                isSystemTag = tag.isSystemTag
            )
        }
        val filters = filterRepository.getAllFilters().first()
        val filterDtos: List<FilterDto> = filters.map { filter ->
            FilterDto(
                name = filter.name,
                priority = filter.priority,
                isEnabled = filter.isEnabled,
                root = filter.root,
                actions = filter.actions.map { action ->
                    FilterActionDto(
                        actionType = action.actionType,
                        targetTagName = if (action.actionType == ActionType.ASSIGN_TAG) {
                            action.targetTagId?.let { tagNameById[it] }
                        } else {
                            null
                        }
                    )
                }
            )
        }
        val tabDtos: List<TabConfigDto> = buildTabDtos(tagNameById)
        val accountDtos: List<AccountDto> = buildAccountDtos()
        val messageStateDtos: List<MessageStateDto> = buildMessageStateDtos(tags, tagNameById)
        val settingsDto = SettingsDto(
            themeMode = backupSettings.getThemeMode(),
            appLockEnabled = backupSettings.getAppLockEnabled()
        )
        val bundle = BackupBundle(
            tags = tagDtos,
            filters = filterDtos,
            tabs = tabDtos,
            accounts = accountDtos,
            messageStates = messageStateDtos,
            settings = settingsDto
        )
        return json.encodeToString(BackupBundle.serializer(), bundle)
    }

    /**
     * Exports configured accounts only (a legacy row with no bank code can't match messages
     * on another device). Parent links are exported as the parent's (bankCode, tail) pair
     * because ids differ across devices.
     */
    private suspend fun buildAccountDtos(): List<AccountDto> {
        val accounts: List<Account> = transactionRepository.getAllAccountsOnce()
        val accountsById: Map<Long, Account> = accounts.associateBy { it.id }
        return accounts.filter { it.bankCode.isNotEmpty() }.map { account ->
            val parent: Account? = account.parentAccountId?.let { accountsById[it] }
            AccountDto(
                bankCode = account.bankCode,
                accountTail = account.accountTail,
                accountType = account.accountType.name,
                name = account.displayName,
                tagName = account.bankName.takeIf { it != account.displayName },
                colorIndex = account.colorIndex,
                parentBankCode = parent?.bankCode,
                parentAccountTail = parent?.accountTail,
                isEnabled = account.isEnabled
            )
        }
    }

    private suspend fun buildTabDtos(tagNameById: Map<Long, String>): List<TabConfigDto> {
        val tabs: List<TabConfig> = tagRepository.getAllTabs().first()
        return tabs.mapNotNull { tab ->
            val tagName: String = tagNameById[tab.tagId] ?: return@mapNotNull null
            TabConfigDto(tagName = tagName, position = tab.position, isVisible = tab.isVisible)
        }
    }

    /**
     * Exports state only for messages that carry something the target device
     * can't re-derive from the SMS store: a read/archive/trash flag, a
     * balance override on the message's transaction, or a tag set different
     * from what the ingest pipeline auto-assigns (Inbox, plus OTP for OTP
     * messages).
     */
    private suspend fun buildMessageStateDtos(
        tags: List<Tag>,
        tagNameById: Map<Long, String>
    ): List<MessageStateDto> {
        val messages: List<Message> = messageRepository.getAllMessagesOnce()
        val tagIdsByMessage: Map<Long, List<Long>> = messageRepository.getTagIdsByMessage()
        val balanceByMessageId: Map<Long, Double> = transactionRepository
            .getTransactionsWithBalanceOnce()
            .mapNotNull { transaction: Transaction ->
                transaction.balanceAfter?.let { transaction.messageId to it }
            }
            .toMap()
        val inboxTagId: Long? = tags.firstOrNull { it.isSystemTag && it.name == SystemTags.INBOX_NAME }?.id
        val otpTagId: Long? = tags.firstOrNull { it.isSystemTag && it.name == SystemTags.OTP_NAME }?.id
        return messages.mapNotNull { message ->
            val tagIds: Set<Long> = tagIdsByMessage[message.id].orEmpty().toSet()
            val baseline: Set<Long> = buildSet {
                inboxTagId?.let { add(it) }
                if (message.isOtp) otpTagId?.let { add(it) }
            }
            val balanceAfter: Double? = balanceByMessageId[message.id]
            val hasRestorableState: Boolean = message.isRead ||
                message.isArchived ||
                message.isTrashed ||
                balanceAfter != null ||
                tagIds != baseline
            if (!hasRestorableState) return@mapNotNull null
            MessageStateDto(
                deviceMessageId = message.deviceMessageId,
                sender = message.sender,
                timestamp = message.timestamp,
                isRead = message.isRead,
                isArchived = message.isArchived,
                isTrashed = message.isTrashed,
                tagNames = tagIds.mapNotNull { tagNameById[it] }.sorted(),
                balanceAfter = balanceAfter
            )
        }
    }
}

package com.strings.app.domain.usecase

import com.strings.app.domain.backup.AccountDto
import com.strings.app.domain.backup.BACKUP_VERSION
import com.strings.app.domain.backup.BackupBundle
import com.strings.app.domain.backup.BackupSettingsStore
import com.strings.app.domain.backup.ImportResult
import com.strings.app.domain.backup.MessageStateDto
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.model.ActionType
import com.strings.app.domain.model.Filter
import com.strings.app.domain.model.FilterAction
import com.strings.app.domain.model.Message
import com.strings.app.domain.model.TabConfig
import com.strings.app.domain.model.Tag
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.prune
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class ImportDataUseCase(
    private val tagRepository: TagRepository,
    private val filterRepository: FilterRepository,
    private val messageRepository: MessageRepository,
    private val transactionRepository: TransactionRepository,
    private val backupSettings: BackupSettingsStore,
    private val json: Json,
    private val recategorizeTransactionsUseCase: RecategorizeTransactionsUseCase
) {
    private data class MessageStateResult(
        val restored: Int,
        val unmatched: Int,
        val balancesRestored: Int
    )

    suspend fun execute(jsonString: String): ImportResult {
        val bundle: BackupBundle = try {
            json.decodeFromString(BackupBundle.serializer(), jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("This file is not a valid Strings backup.")
        }
        if (bundle.version > BACKUP_VERSION) {
            throw IllegalArgumentException(
                "This backup was created by a newer version of Strings. Please update the app to import it."
            )
        }
        // Accounts import (and history rebuilds) before message states so
        // balance overrides can land on the re-parsed transactions.
        val accountsAdded: Int = importAccounts(bundle)
        if (accountsAdded > 0) {
            recategorizeTransactionsUseCase.execute(sinceMillis = 0L)
        }
        val tagsAdded: Int = importTags(bundle)
        val (filtersAdded: Int, filtersSkipped: Int) = importFilters(bundle)
        val tagIdByName: Map<String, Long> =
            tagRepository.getAllTags().first().associate { it.name to it.id }
        val tabsRestored: Int = importTabs(bundle, tagIdByName)
        val stateResult: MessageStateResult = importMessageStates(bundle, tagIdByName)
        importSettings(bundle)
        return ImportResult(
            tagsAdded = tagsAdded,
            filtersAdded = filtersAdded,
            filtersSkipped = filtersSkipped,
            tabsRestored = tabsRestored,
            accountsAdded = accountsAdded,
            messagesRestored = stateResult.restored,
            messagesUnmatched = stateResult.unmatched,
            balancesRestored = stateResult.balancesRestored
        )
    }

    /**
     * Find-or-creates accounts by (bankCode, accountTail), then re-links parent references
     * by the same key in a second pass (the parent may itself be created by this import).
     * Existing accounts are left untouched, so re-importing a bundle changes nothing.
     */
    private suspend fun importAccounts(bundle: BackupBundle): Int {
        if (bundle.accounts.isEmpty()) return 0
        var accountsAdded = 0
        for (dto: AccountDto in bundle.accounts) {
            if (dto.bankCode.isEmpty()) continue
            val accountType: AccountType = try {
                AccountType.valueOf(dto.accountType)
            } catch (e: IllegalArgumentException) {
                continue
            }
            val existing: Account? =
                transactionRepository.findAccountByCodeAndTail(dto.bankCode, dto.accountTail)
            if (existing != null) continue
            transactionRepository.insertAccount(
                Account(
                    bankName = dto.tagName ?: dto.name,
                    accountTail = dto.accountTail,
                    accountType = accountType,
                    displayName = dto.name,
                    bankCode = dto.bankCode,
                    colorIndex = dto.colorIndex,
                    parentAccountId = null,
                    isEnabled = dto.isEnabled
                )
            )
            transactionRepository.removeAccountSuggestion(dto.bankCode, dto.accountTail)
            accountsAdded++
        }
        for (dto: AccountDto in bundle.accounts) {
            val parentBankCode: String = dto.parentBankCode ?: continue
            val parentTail: String = dto.parentAccountTail ?: continue
            val child: Account = transactionRepository
                .findAccountByCodeAndTail(dto.bankCode, dto.accountTail) ?: continue
            if (child.parentAccountId != null) continue
            val parent: Account = transactionRepository
                .findAccountByCodeAndTail(parentBankCode, parentTail) ?: continue
            transactionRepository.updateAccount(child.copy(parentAccountId = parent.id))
        }
        return accountsAdded
    }

    private suspend fun importTags(bundle: BackupBundle): Int {
        val existingTags: List<Tag> = tagRepository.getAllTags().first()
        val nameToId: MutableMap<String, Long> = existingTags.associate { it.name to it.id }.toMutableMap()
        val insertedNames: MutableSet<String> = mutableSetOf()
        var tagsAdded = 0
        for (dto in bundle.tags) {
            if (nameToId.containsKey(dto.name)) continue
            if (dto.isSystemTag) continue
            val newId: Long = tagRepository.insertTag(
                Tag(
                    name = dto.name,
                    color = dto.color,
                    icon = dto.icon,
                    parentTagId = null,
                    sortOrder = dto.sortOrder,
                    isSystemTag = false
                )
            )
            nameToId[dto.name] = newId
            insertedNames.add(dto.name)
            tagsAdded++
        }
        for (dto in bundle.tags) {
            if (dto.name !in insertedNames) continue
            val parentName: String = dto.parentName ?: continue
            val parentId: Long = nameToId[parentName] ?: continue
            val tagId: Long = nameToId[dto.name] ?: continue
            val tag: Tag = tagRepository.getTagById(tagId) ?: continue
            tagRepository.updateTag(tag.copy(parentTagId = parentId))
        }
        return tagsAdded
    }

    private suspend fun importFilters(bundle: BackupBundle): Pair<Int, Int> {
        val nameToId: Map<String, Long> = tagRepository.getAllTags().first().associate { it.name to it.id }
        val knownFilters: MutableList<Filter> = filterRepository.getAllFilters().first().toMutableList()
        var maxPriority: Int = filterRepository.getMaxPriority()
        var filtersAdded = 0
        var filtersSkipped = 0
        for (dto in bundle.filters) {
            val prunedRoot = dto.root.prune()
            val isDuplicate: Boolean = knownFilters.any { existing ->
                existing.name == dto.name && existing.root.prune() == prunedRoot
            }
            if (isDuplicate) {
                filtersSkipped++
                continue
            }
            val existingNames: List<String> = knownFilters.map { it.name }
            val finalName: String = if (existingNames.contains(dto.name)) {
                uniqueName(dto.name, existingNames.toSet())
            } else {
                dto.name
            }
            val actions: List<FilterAction> = dto.actions.mapNotNull { action ->
                if (action.actionType == ActionType.ASSIGN_TAG) {
                    val tagId: Long = action.targetTagName?.let { nameToId[it] } ?: return@mapNotNull null
                    FilterAction(actionType = ActionType.ASSIGN_TAG, targetTagId = tagId)
                } else {
                    FilterAction(actionType = action.actionType, targetTagId = null)
                }
            }
            maxPriority += 1
            val newFilter = Filter(
                name = finalName,
                priority = maxPriority,
                isEnabled = dto.isEnabled,
                root = prunedRoot,
                actions = actions
            )
            filterRepository.insertFilter(newFilter)
            knownFilters.add(newFilter)
            filtersAdded++
        }
        return filtersAdded to filtersSkipped
    }

    private suspend fun importTabs(bundle: BackupBundle, tagIdByName: Map<String, Long>): Int {
        if (bundle.tabs.isEmpty()) return 0
        val existingTabs: List<TabConfig> = tagRepository.getAllTabs().first()
        var tabsRestored = 0
        for (dto in bundle.tabs) {
            val tagId: Long = tagIdByName[dto.tagName] ?: continue
            val existing: TabConfig? = existingTabs.firstOrNull { it.tagId == tagId }
            if (existing != null) {
                if (existing.position != dto.position || existing.isVisible != dto.isVisible) {
                    tagRepository.updateTabConfig(
                        existing.copy(position = dto.position, isVisible = dto.isVisible)
                    )
                }
            } else {
                tagRepository.insertTabConfig(
                    TabConfig(tagId = tagId, position = dto.position, isVisible = dto.isVisible)
                )
            }
            tabsRestored++
        }
        return tabsRestored
    }

    /**
     * Matches each exported message state to a local message -- by
     * deviceMessageId first (stable on the same device), then by
     * (sender, timestamp) -- and restores flags, the exact tag set, and any
     * balance override on the message's re-parsed transaction.
     */
    private suspend fun importMessageStates(
        bundle: BackupBundle,
        tagIdByName: Map<String, Long>
    ): MessageStateResult {
        if (bundle.messageStates.isEmpty()) return MessageStateResult(0, 0, 0)
        val allMessages: List<Message> = messageRepository.getAllMessagesOnce()
        val byDeviceId: Map<Long, Message> = allMessages
            .mapNotNull { message -> message.deviceMessageId?.let { it to message } }
            .toMap()
        val byContent: Map<Pair<String, Long>, Message> =
            allMessages.associateBy { it.sender to it.timestamp }
        var restored = 0
        var unmatched = 0
        var balancesRestored = 0
        for (dto: MessageStateDto in bundle.messageStates) {
            val message: Message? = dto.deviceMessageId?.let { byDeviceId[it] }
                ?: byContent[dto.sender to dto.timestamp]
            if (message == null) {
                unmatched++
                continue
            }
            if (message.isRead != dto.isRead) {
                messageRepository.setRead(message.id, dto.isRead)
            }
            if (message.isArchived != dto.isArchived) {
                messageRepository.setArchived(message.id, dto.isArchived)
            }
            if (message.isTrashed != dto.isTrashed) {
                messageRepository.setTrashed(message.id, dto.isTrashed)
            }
            val tagIds: List<Long> = dto.tagNames.mapNotNull { tagIdByName[it] }
            if (tagIds.isNotEmpty()) {
                messageRepository.replaceTagsForMessage(message.id, tagIds)
            }
            if (dto.balanceAfter != null) {
                val transaction: Transaction? = transactionRepository.getTransactionForMessage(message.id)
                if (transaction != null && transaction.balanceAfter != dto.balanceAfter) {
                    transactionRepository.updateBalanceAfter(transaction.id, dto.balanceAfter)
                }
                if (transaction != null) {
                    balancesRestored++
                }
            }
            restored++
        }
        return MessageStateResult(
            restored = restored,
            unmatched = unmatched,
            balancesRestored = balancesRestored
        )
    }

    private suspend fun importSettings(bundle: BackupBundle) {
        val settings = bundle.settings ?: return
        backupSettings.setThemeMode(settings.themeMode)
        backupSettings.setAppLockEnabled(settings.appLockEnabled)
    }

    private fun uniqueName(base: String, existing: Set<String>): String {
        var counter = 1
        var candidate = "$base ($counter)"
        while (existing.contains(candidate)) {
            counter++
            candidate = "$base ($counter)"
        }
        return candidate
    }
}

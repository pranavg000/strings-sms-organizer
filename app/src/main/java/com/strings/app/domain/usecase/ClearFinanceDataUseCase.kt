package com.strings.app.domain.usecase

import com.strings.app.domain.model.Tag
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.transaction.TransactionCategorizer

/**
 * Wipes derived Finance data so that recategorization starts fresh:
 * 1. Deletes all transactions (user-configured accounts are kept -- they are settings,
 *    not derived data).
 * 2. Removes the Finance tag and all its child tags from every message.
 * 3. Deletes the child tags (per-account) and the Finance parent tag itself.
 */
class ClearFinanceDataUseCase(
    private val tagRepository: TagRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend fun execute(): Int {
        transactionRepository.deleteAllTransactions()
        val financeTag: Tag = tagRepository.getTagByName(TransactionCategorizer.FINANCE_TAG_NAME)
            ?: return 0
        val descendantIds: List<Long> = tagRepository.getDescendantTagIds(financeTag.id)
        var removed = 0
        for (tagId in descendantIds) {
            tagRepository.deleteTag(tagId)
            removed++
        }
        return removed
    }
}

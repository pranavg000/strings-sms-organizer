package com.strings.app.domain.transaction

import com.strings.app.domain.model.Account

/**
 * Resolves parent-child account families from [Account.parentAccountId] (e.g. add-on
 * credit cards sharing the primary card's statement). Only one level of nesting exists:
 * a child points at its root. A dangling parent id (parent deleted) degrades gracefully
 * to the account being its own root.
 */
object AccountFamilies {
    fun rootId(account: Account, accountsById: Map<Long, Account>): Long {
        val parentId: Long = account.parentAccountId ?: return account.id
        return if (accountsById.containsKey(parentId)) parentId else account.id
    }

    fun familyAccountIds(account: Account, accounts: List<Account>): List<Long> {
        val byId: Map<Long, Account> = accounts.associateBy { it.id }
        val root: Long = rootId(account, byId)
        val ids: List<Long> = accounts.filter { rootId(it, byId) == root }.map { it.id }
        return ids.ifEmpty { listOf(account.id) }
    }
}

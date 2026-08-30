package com.strings.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.AccountSuggestion
import com.strings.app.domain.model.AccountType
import com.strings.app.domain.model.Tag
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.transaction.BankCatalog
import com.strings.app.domain.transaction.CatalogBank
import com.strings.app.domain.usecase.RecategorizeTransactionsUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Form state for creating or editing one account. [bankCode] is empty until the user picks a
 * bank (legacy rows migrated from before accounts were user-configured start this way and
 * show as "needs setup"). [validationError] is set on a failed save attempt and cleared on
 * the next field change.
 */
data class AccountEditState(
    val isNew: Boolean = true,
    val accountId: Long = 0L,
    val bankCode: String = "",
    val accountType: AccountType = AccountType.SAVINGS,
    val accountTail: String = "",
    val name: String = "",
    val parentAccountId: Long? = null,
    val colorIndex: Int = 0,
    val isEnabled: Boolean = true,
    val validationError: String? = null,
    val showDeleteConfirm: Boolean = false,
    val isSaving: Boolean = false
)

/**
 * Backs both the Manage accounts list and the account edit form. Saving or deleting an
 * account re-runs transaction categorization (idempotent delete-then-recreate) so history
 * rebuilds against the new configuration; the re-run is non-cancellable so it completes
 * even when the user navigates away immediately.
 */
class ManageAccountsViewModel(
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val filterRepository: FilterRepository,
    private val recategorizeTransactionsUseCase: RecategorizeTransactionsUseCase
) : ViewModel() {
    val accounts: StateFlow<List<Account>> = transactionRepository.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suggestions: StateFlow<List<AccountSuggestion>> =
        transactionRepository.getPendingAccountSuggestions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editState: MutableStateFlow<AccountEditState> = MutableStateFlow(AccountEditState())
    val editState: StateFlow<AccountEditState> = _editState.asStateFlow()

    fun loadAccountForEdit(accountId: Long, prefillBankCode: String, prefillTail: String) {
        viewModelScope.launch {
            if (accountId > 0L) {
                val account: Account = transactionRepository.getAccountById(accountId) ?: return@launch
                _editState.value = AccountEditState(
                    isNew = false,
                    accountId = account.id,
                    bankCode = account.bankCode,
                    accountType = account.accountType,
                    accountTail = account.accountTail,
                    name = account.displayName,
                    parentAccountId = account.parentAccountId,
                    colorIndex = if (account.colorIndex >= 0) account.colorIndex else 0,
                    isEnabled = account.isEnabled
                )
            } else {
                val existing: List<Account> = transactionRepository.getAllAccountsOnce()
                val bank: CatalogBank? = BankCatalog.byCode(prefillBankCode)
                _editState.value = AccountEditState(
                    isNew = true,
                    bankCode = prefillBankCode,
                    accountType = bank?.supportedTypes?.firstOrNull() ?: AccountType.SAVINGS,
                    accountTail = prefillTail,
                    name = bank?.defaultAccountName.orEmpty(),
                    colorIndex = nextAccountColorIndex(existing)
                )
            }
        }
    }

    fun updateBank(bankCode: String) {
        val bank: CatalogBank = BankCatalog.byCode(bankCode) ?: return
        _editState.value = _editState.value.copy(
            bankCode = bankCode,
            accountType = if (_editState.value.accountType in bank.supportedTypes) {
                _editState.value.accountType
            } else {
                bank.supportedTypes.first()
            },
            accountTail = if (bank.requiresTail) _editState.value.accountTail else "",
            name = _editState.value.name.ifBlank { bank.defaultAccountName.orEmpty() },
            parentAccountId = null,
            validationError = null
        )
    }

    fun updateAccountType(type: AccountType) {
        _editState.value = _editState.value.copy(
            accountType = type,
            parentAccountId = if (type == AccountType.CREDIT_CARD) _editState.value.parentAccountId else null,
            validationError = null
        )
    }

    fun updateAccountTail(tail: String) {
        _editState.value = _editState.value.copy(
            accountTail = tail.filter { it.isDigit() }.take(MAX_TAIL_DIGITS),
            validationError = null
        )
    }

    fun updateName(name: String) {
        _editState.value = _editState.value.copy(name = name, validationError = null)
    }

    fun updateParentAccount(parentAccountId: Long?) {
        _editState.value = _editState.value.copy(parentAccountId = parentAccountId, validationError = null)
    }

    fun updateColorIndex(colorIndex: Int) {
        _editState.value = _editState.value.copy(colorIndex = colorIndex)
    }

    fun updateIsEnabled(isEnabled: Boolean) {
        _editState.value = _editState.value.copy(isEnabled = isEnabled)
    }

    /** Same-bank, top-level credit cards this account could link to as an add-on card. */
    fun parentOptions(): List<Account> {
        val state: AccountEditState = _editState.value
        return accounts.value.filter {
            it.id != state.accountId &&
                it.bankCode == state.bankCode &&
                it.accountType == AccountType.CREDIT_CARD &&
                it.parentAccountId == null
        }
    }

    fun saveAccount(onSaved: () -> Unit) {
        val state: AccountEditState = _editState.value
        if (state.isSaving) return
        viewModelScope.launch {
            val error: String? = validate(state)
            if (error != null) {
                _editState.value = state.copy(validationError = error)
                return@launch
            }
            _editState.value = state.copy(isSaving = true)
            val account = Account(
                id = state.accountId,
                bankName = state.name.trim(),
                accountTail = state.accountTail,
                accountType = state.accountType,
                displayName = state.name.trim(),
                bankCode = state.bankCode,
                colorIndex = state.colorIndex,
                parentAccountId = state.parentAccountId,
                isEnabled = state.isEnabled
            )
            if (state.isNew) {
                transactionRepository.insertAccount(account)
            } else {
                transactionRepository.updateAccount(account)
            }
            transactionRepository.removeAccountSuggestion(state.bankCode, state.accountTail)
            onSaved()
            recategorize()
        }
    }

    private suspend fun validate(state: AccountEditState): String? {
        val bank: CatalogBank = BankCatalog.byCode(state.bankCode) ?: return "Pick a bank."
        if (state.name.isBlank()) return "Give the account a name."
        if (bank.requiresTail && state.accountTail.length !in MIN_TAIL_DIGITS..MAX_TAIL_DIGITS) {
            return "Enter the last $MIN_TAIL_DIGITS\u2013$MAX_TAIL_DIGITS digits of the account or card number."
        }
        val duplicate: Account? = transactionRepository
            .findAccountByCodeAndTail(state.bankCode, state.accountTail)
        if (duplicate != null && duplicate.id != state.accountId) {
            return "An account with these digits already exists for this bank."
        }
        return null
    }

    fun requestDeleteAccount() {
        _editState.value = _editState.value.copy(showDeleteConfirm = true)
    }

    fun dismissDeleteConfirm() {
        _editState.value = _editState.value.copy(showDeleteConfirm = false)
    }

    fun confirmDeleteAccount(onDeleted: () -> Unit) {
        val state: AccountEditState = _editState.value
        viewModelScope.launch {
            val account: Account? = transactionRepository.getAccountById(state.accountId)
            transactionRepository.deleteAccount(state.accountId)
            if (account != null) deleteOrphanedFinanceTag(account)
            _editState.value = state.copy(showDeleteConfirm = false)
            onDeleted()
        }
    }

    /**
     * Removes the account's finance tag when no other account still uses that name. Tags
     * referenced by filters are kept so filter actions never point at a deleted tag.
     */
    private suspend fun deleteOrphanedFinanceTag(account: Account) {
        val stillUsed: Boolean = transactionRepository.getAllAccountsOnce()
            .any { it.bankName == account.bankName }
        if (stillUsed) return
        val tag: Tag = tagRepository.getTagByName(account.bankName) ?: return
        if (filterRepository.getFilterNamesUsingTag(tag.id).isNotEmpty()) return
        tagRepository.deleteTag(tag.id)
    }

    fun dismissSuggestion(suggestionId: Long) {
        viewModelScope.launch {
            transactionRepository.dismissAccountSuggestion(suggestionId)
        }
    }

    /**
     * Tail-less wallets are enabled with a toggle instead of the form: find-or-create the
     * wallet account, flip [Account.isEnabled], and rebuild history when turning on.
     */
    fun setWalletEnabled(bank: CatalogBank, enabled: Boolean) {
        viewModelScope.launch {
            val existing: Account? = transactionRepository.findAccountByCodeAndTail(bank.code.name, "")
            if (existing != null) {
                transactionRepository.updateAccount(existing.copy(isEnabled = enabled))
            } else if (enabled) {
                val name: String = bank.defaultAccountName ?: bank.displayName
                transactionRepository.insertAccount(
                    Account(
                        bankName = name,
                        accountTail = "",
                        accountType = AccountType.WALLET,
                        displayName = name,
                        bankCode = bank.code.name,
                        colorIndex = nextAccountColorIndex(transactionRepository.getAllAccountsOnce())
                    )
                )
            }
            if (enabled) {
                transactionRepository.removeAccountSuggestion(bank.code.name, "")
                recategorize()
            }
        }
    }

    /**
     * Rebuilds transaction history against the current account configuration. Runs
     * non-cancellable so navigating away right after a save doesn't abort it mid-way.
     */
    private suspend fun recategorize() {
        withContext(NonCancellable) {
            recategorizeTransactionsUseCase.execute(System.currentTimeMillis() - RECATEGORIZE_WINDOW_MS)
        }
    }

    private companion object {
        const val MIN_TAIL_DIGITS: Int = 3
        const val MAX_TAIL_DIGITS: Int = 6
        const val RECATEGORIZE_WINDOW_MS: Long = 365L * 24 * 60 * 60 * 1000
    }
}

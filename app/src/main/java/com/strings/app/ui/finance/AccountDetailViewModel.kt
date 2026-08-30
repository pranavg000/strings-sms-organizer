package com.strings.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionType
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.domain.transaction.AccountFamilies
import com.strings.app.domain.transaction.BalanceDiscrepancy
import com.strings.app.domain.usecase.CheckBalanceDiscrepancyUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

class AccountDetailViewModel(
    private val transactionRepository: TransactionRepository,
    private val checkBalanceDiscrepancy: CheckBalanceDiscrepancyUseCase,
    private val accountId: Long
) : ViewModel() {

    private val _account: MutableStateFlow<Account?> = MutableStateFlow(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _balanceDiscrepancies = MutableSharedFlow<BalanceDiscrepancy>(extraBufferCapacity = 1)
    val balanceDiscrepancies: SharedFlow<BalanceDiscrepancy> = _balanceDiscrepancies.asSharedFlow()

    private val _currentMonth: MutableStateFlow<YearMonth> = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val monthRange: StateFlow<Pair<Long, Long>> = _currentMonth.map { month ->
        computeMonthRange(month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), computeMonthRange(YearMonth.now()))

    private val allAccounts: StateFlow<List<Account>> = transactionRepository.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val familyAccountIds: StateFlow<List<Long>> = combine(_account, allAccounts) { acct, accts ->
        if (acct == null) return@combine listOf(accountId)
        AccountFamilies.familyAccountIds(acct, accts).ifEmpty { listOf(accountId) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(accountId))

    val isFamily: StateFlow<Boolean> = familyAccountIds.map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val familyAccountsById: StateFlow<Map<Long, Account>> = combine(_account, allAccounts) { acct, accts ->
        if (acct == null) return@combine emptyMap()
        val familyIds: Set<Long> = AccountFamilies.familyAccountIds(acct, accts).toSet()
        accts.filter { it.id in familyIds }.associateBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Starts as null (= loading) so the UI doesn't flash the empty state on first load.
    val transactions: StateFlow<List<Transaction>?> = combine(familyAccountIds, monthRange) { ids, (from, to) ->
        ids to (from to to)
    }.flatMapLatest { (ids, range) ->
        transactionRepository.getTransactionsByAccountsInRange(ids, range.first, range.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val allFamilyTransactions: StateFlow<List<Transaction>> = familyAccountIds
        .flatMapLatest { ids -> transactionRepository.getTransactionsByAccounts(ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val estimatedBalance: StateFlow<Double?> = allFamilyTransactions.map { txns ->
        computeEstimatedBalance(txns)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val monthlySummary: StateFlow<MonthSummary> = transactions.map { txns ->
        MonthSummary(
            totalCredits = txns.orEmpty().filter { it.type == TransactionType.CREDIT }.sumOf { it.amount },
            totalDebits = txns.orEmpty().filter { it.type == TransactionType.DEBIT }.sumOf { it.amount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSummary())

    init {
        viewModelScope.launch {
            _account.value = transactionRepository.getAccountById(accountId)
        }
    }

    fun setTransactionBalance(transactionId: Long, balance: Double?) {
        viewModelScope.launch {
            transactionRepository.updateBalanceAfter(transactionId, balance)
            if (balance != null) {
                checkBalanceDiscrepancy.forTransaction(transactionId)?.let { discrepancy ->
                    _balanceDiscrepancies.tryEmit(discrepancy)
                }
            }
        }
    }

    fun dismissSentinel(transactionId: Long) {
        viewModelScope.launch {
            transactionRepository.deleteTransactionById(transactionId)
        }
    }

    fun previousMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next: YearMonth = _currentMonth.value.plusMonths(1)
        if (next <= YearMonth.now()) {
            _currentMonth.value = next
        }
    }
}

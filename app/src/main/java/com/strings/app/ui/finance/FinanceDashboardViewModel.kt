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

data class MonthSummary(
    val totalCredits: Double = 0.0,
    val totalDebits: Double = 0.0
)

data class AccountBalance(
    val account: Account,
    val estimatedBalance: Double?,
    val monthlyCredits: Double,
    val monthlyDebits: Double,
    val familyRootId: Long,
    val parentDisplayName: String? = null
)

private data class FamilyStats(
    val balance: Double?,
    val monthlyCredits: Double,
    val monthlyDebits: Double
)

/**
 * Sorts accounts so that family members are grouped together (child immediately after
 * parent), with families ordered by total family activity descending. Family stats are
 * shared across members, so comparing per-account activity keeps families contiguous.
 */
private fun familySortComparator(): Comparator<AccountBalance> {
    return compareByDescending<AccountBalance> { it.monthlyCredits + it.monthlyDebits }
        .thenBy { it.familyRootId }
        .thenBy { it.parentDisplayName != null }
}

class FinanceDashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val checkBalanceDiscrepancy: CheckBalanceDiscrepancyUseCase
) : ViewModel() {

    private val _currentMonth: MutableStateFlow<YearMonth> = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _balanceDiscrepancies = MutableSharedFlow<BalanceDiscrepancy>(extraBufferCapacity = 1)
    val balanceDiscrepancies: SharedFlow<BalanceDiscrepancy> = _balanceDiscrepancies.asSharedFlow()

    private val monthRange: StateFlow<Pair<Long, Long>> = _currentMonth.map { month ->
        computeMonthRange(month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), computeMonthRange(YearMonth.now()))

    // Nullable list flows start as null (= loading) so the UI can distinguish
    // "still loading" from "genuinely empty" and avoid flashing the empty state.
    private val monthlyTransactions: StateFlow<List<Transaction>?> = monthRange
        .flatMapLatest { (from, to) -> transactionRepository.getTransactionsInRange(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val allTransactions: StateFlow<List<Transaction>?> = transactionRepository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val accounts: StateFlow<List<Account>?> = transactionRepository.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val summary: StateFlow<MonthSummary> = monthlyTransactions.map { txns ->
        MonthSummary(
            totalCredits = txns.orEmpty().filter { it.type == TransactionType.CREDIT }.sumOf { it.amount },
            totalDebits = txns.orEmpty().filter { it.type == TransactionType.DEBIT }.sumOf { it.amount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSummary())

    val accountBalances: StateFlow<List<AccountBalance>?> = combine(
        allTransactions, accounts, monthlyTransactions
    ) { allTxns, accts, monthTxns ->
        if (allTxns == null || accts == null || monthTxns == null) return@combine null
        val byAccount: Map<Long, List<Transaction>> = allTxns.groupBy { it.accountId }
        val monthByAccount: Map<Long, List<Transaction>> = monthTxns.groupBy { it.accountId }
        val accountsById: Map<Long, Account> = accts.associateBy { it.id }
        val rootByAccountId: Map<Long, Long> = accts.associate {
            it.id to AccountFamilies.rootId(it, accountsById)
        }
        val familyCache: MutableMap<Long, FamilyStats> = mutableMapOf()
        fun familyStats(rootId: Long): FamilyStats {
            return familyCache.getOrPut(rootId) {
                val familyIds: List<Long> = accts.filter { rootByAccountId[it.id] == rootId }.map { it.id }
                val familyAllTxns: List<Transaction> = familyIds.flatMap { byAccount[it] ?: emptyList() }
                val familyMonthTxns: List<Transaction> = familyIds.flatMap { monthByAccount[it] ?: emptyList() }
                FamilyStats(
                    balance = computeEstimatedBalance(familyAllTxns),
                    monthlyCredits = familyMonthTxns.filter { it.type == TransactionType.CREDIT }.sumOf { it.amount },
                    monthlyDebits = familyMonthTxns.filter { it.type == TransactionType.DEBIT }.sumOf { it.amount }
                )
            }
        }
        val balances: List<AccountBalance> = accts.map { account ->
            val rootId: Long = rootByAccountId[account.id] ?: account.id
            val stats: FamilyStats = familyStats(rootId)
            AccountBalance(
                account = account,
                estimatedBalance = stats.balance,
                monthlyCredits = stats.monthlyCredits,
                monthlyDebits = stats.monthlyDebits,
                familyRootId = rootId,
                parentDisplayName = if (rootId != account.id) accountsById[rootId]?.displayName else null
            )
        }
        balances.sortedWith(familySortComparator())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val totalBalance: StateFlow<Double?> = accountBalances.map { balances ->
        val seen: MutableSet<Long> = mutableSetOf()
        val unique: MutableList<Double> = mutableListOf()
        for (ab in balances.orEmpty()) {
            if (seen.add(ab.familyRootId) && ab.estimatedBalance != null) {
                unique.add(ab.estimatedBalance)
            }
        }
        if (unique.isEmpty()) null else unique.sum()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val overviewTransactions: StateFlow<List<Transaction>?> = monthlyTransactions

    /**
     * Provides account lookup by ID for displaying account names in overview transaction rows.
     */
    val accountsById: StateFlow<Map<Long, Account>> = accounts.map { accts ->
        accts.orEmpty().associateBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

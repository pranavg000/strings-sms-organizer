package com.strings.app.ui.finance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.strings.app.domain.model.Account
import com.strings.app.domain.model.Transaction
import com.strings.app.domain.model.TransactionType
import com.strings.app.domain.transaction.BalanceDiscrepancy
import com.strings.app.ui.theme.AppPalette
import com.strings.app.ui.theme.CardColors
import com.strings.app.ui.theme.LocalAppDarkTheme
import com.strings.app.ui.theme.TransactionColors
import com.strings.app.ui.theme.resolveCardColors
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
private val CURRENCY_FORMAT: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun formatMonth(month: YearMonth): String = month.format(MONTH_FORMATTER)

fun formatTransactionDate(timestampMs: Long, transactionTime: String? = null): String {
    val zdt = Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault())
    val day: String = zdt.dayOfMonth.toString()
    val month: String = zdt.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val weekday: String = zdt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val time: String = if (transactionTime != null) formatTransactionTime(transactionTime) else zdt.format(TIME_FORMATTER)
    return "$day $month, $weekday \u2022 $time"
}

fun formatTransactionTime(time24: String): String {
    return try {
        val parsed = java.time.LocalTime.parse(time24, DateTimeFormatter.ofPattern("HH:mm"))
        parsed.format(TIME_FORMATTER)
    } catch (_: Exception) {
        time24
    }
}

fun formatCurrency(amount: Double): String {
    return CURRENCY_FORMAT.format(amount)
}

fun formatDiscrepancyMessage(discrepancy: BalanceDiscrepancy): String {
    return "Doesn't tally: expected ${formatCurrency(discrepancy.expectedBalance)}, " +
        "off by ${formatCurrency(kotlin.math.abs(discrepancy.difference))}"
}

fun formatSignedAmount(amount: Double, type: TransactionType): String {
    val prefix: String = if (type == TransactionType.CREDIT) "+" else "-"
    return "$prefix ${CURRENCY_FORMAT.format(amount)}"
}

@Composable
fun creditColor(): Color {
    return if (LocalAppDarkTheme.current) TransactionColors.creditDark else TransactionColors.creditLight
}

@Composable
fun debitColor(): Color {
    return if (LocalAppDarkTheme.current) TransactionColors.debitDark else TransactionColors.debitLight
}

@Composable
fun amountColor(type: TransactionType): Color {
    return if (type == TransactionType.CREDIT) creditColor() else debitColor()
}

@Composable
fun rememberAccountCardColors(account: Account): CardColors {
    val colorIndex: Int = if (account.colorIndex >= 0) {
        account.colorIndex
    } else {
        Math.floorMod(account.bankName.hashCode(), AppPalette.size)
    }
    return resolveCardColors(colorIndex)
}

/**
 * Picks the least-used palette slot among existing accounts so new account cards stay
 * visually distinct (ties resolve to the lowest index).
 */
fun nextAccountColorIndex(accounts: List<Account>): Int {
    val usage: IntArray = IntArray(AppPalette.size)
    for (account in accounts) {
        if (account.colorIndex in usage.indices) usage[account.colorIndex]++
    }
    var best = 0
    for (index in usage.indices) {
        if (usage[index] < usage[best]) best = index
    }
    return best
}

/**
 * Computes the month time range as epoch millis (inclusive start, inclusive end).
 */
fun computeMonthRange(month: YearMonth): Pair<Long, Long> {
    val zone: ZoneId = ZoneId.systemDefault()
    val from: Long = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val to: Long = month.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return from to to
}

/**
 * Estimates the current balance for an account by finding the latest transaction with a
 * non-null balanceAfter (anchor), then applying all subsequent credit/debit amounts forward.
 * Returns null if no anchor exists.
 */
fun computeEstimatedBalance(transactions: List<Transaction>): Double? {
    if (transactions.isEmpty()) return null
    val sorted: List<Transaction> = transactions.sortedBy { it.timestamp }
    val anchorIndex: Int = sorted.indexOfLast { it.balanceAfter != null }
    if (anchorIndex == -1) return null
    val anchor: Transaction = sorted[anchorIndex]
    var balance: Double = anchor.balanceAfter!!
    for (i in (anchorIndex + 1) until sorted.size) {
        val txn: Transaction = sorted[i]
        when (txn.type) {
            TransactionType.CREDIT -> balance += txn.amount
            TransactionType.DEBIT -> balance -= txn.amount
        }
    }
    return balance
}

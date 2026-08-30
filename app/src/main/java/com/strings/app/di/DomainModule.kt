package com.strings.app.di

import com.strings.app.domain.filter.FilterEngine
import com.strings.app.domain.filter.FilterSuggester
import com.strings.app.domain.otp.OtpDetector
import com.strings.app.domain.transaction.BalanceReconciler
import com.strings.app.domain.transaction.TransactionCategorizer
import com.strings.app.domain.transaction.TransactionParser
import com.strings.app.domain.transaction.defaultBankParsers
import com.strings.app.domain.usecase.AdoptLegacyWalletAccountsUseCase
import com.strings.app.domain.usecase.ApplyFilterToExistingUseCase
import com.strings.app.domain.usecase.ApplyFiltersUseCase
import com.strings.app.domain.usecase.CheckBalanceDiscrepancyUseCase
import com.strings.app.domain.usecase.ExportCategorizationUseCase
import com.strings.app.domain.usecase.ExportDataUseCase
import com.strings.app.domain.usecase.GetFilteredMessagesUseCase
import com.strings.app.domain.usecase.ImportDataUseCase
import com.strings.app.domain.usecase.GetMessagesForTagUseCase
import com.strings.app.domain.usecase.ClearFinanceDataUseCase
import com.strings.app.domain.usecase.RecategorizeTransactionsUseCase
import com.strings.app.domain.usecase.SearchMessagesUseCase
import com.strings.app.domain.usecase.SyncSmsUseCase
import org.koin.dsl.module

val domainModule = module {
    single { FilterEngine() }
    single { FilterSuggester() }
    single { OtpDetector() }
    single { TransactionParser(defaultBankParsers()) }
    single { TransactionCategorizer(get(), get(), get(), get()) }
    single { BalanceReconciler() }
    factory { CheckBalanceDiscrepancyUseCase(get(), get()) }
    factory { GetMessagesForTagUseCase(get(), get()) }
    factory { GetFilteredMessagesUseCase(get()) }
    factory { SearchMessagesUseCase(get()) }
    factory { ApplyFiltersUseCase(get(), get(), get(), get()) }
    factory { ApplyFilterToExistingUseCase(get(), get(), get(), get(), get()) }
    factory { SyncSmsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ExportDataUseCase(get(), get(), get(), get(), get()) }
    factory { ImportDataUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { RecategorizeTransactionsUseCase(get(), get(), get()) }
    factory { AdoptLegacyWalletAccountsUseCase(get(), get()) }
    factory { ExportCategorizationUseCase(get(), get(), get()) }
    factory { ClearFinanceDataUseCase(get(), get()) }
}

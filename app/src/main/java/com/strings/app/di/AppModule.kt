package com.strings.app.di

import com.strings.app.ui.backup.BackupViewModel
import com.strings.app.ui.detail.MessageDetailViewModel
import com.strings.app.ui.finance.AccountDetailViewModel
import com.strings.app.ui.finance.FinanceDashboardViewModel
import com.strings.app.ui.finance.ManageAccountsViewModel
import com.strings.app.ui.filters.FilterMessagesViewModel
import com.strings.app.ui.filters.FilterViewModel
import com.strings.app.ui.inbox.AllMessagesViewModel
import com.strings.app.ui.inbox.ArchivedMessagesViewModel
import com.strings.app.ui.inbox.InboxViewModel
import com.strings.app.ui.inbox.TagMessagesViewModel
import com.strings.app.ui.inbox.TrashedMessagesViewModel
import com.strings.app.ui.search.SearchViewModel
import com.strings.app.ui.settings.SettingsViewModel
import com.strings.app.ui.tags.TagViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { InboxViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get(), get(), get()) }
    viewModel { FilterViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { FilterMessagesViewModel(get(), get(), get(), get(), get()) }
    viewModel { TagViewModel(get(), get(), get()) }
    viewModel { MessageDetailViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { TagMessagesViewModel(get(), get(), get(), get()) }
    viewModel { AllMessagesViewModel(get(), get(), get()) }
    viewModel { ArchivedMessagesViewModel(get(), get(), get()) }
    viewModel { TrashedMessagesViewModel(get(), get(), get()) }
    viewModel { BackupViewModel(get(), get(), get()) }
    viewModel { FinanceDashboardViewModel(get(), get()) }
    viewModel { ManageAccountsViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { (accountId: Long) -> AccountDetailViewModel(get(), get(), accountId) }
}


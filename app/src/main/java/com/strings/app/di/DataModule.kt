package com.strings.app.di

import androidx.room.Room
import androidx.work.WorkManager
import com.strings.app.data.local.db.MIGRATION_1_2
import com.strings.app.data.local.db.MIGRATION_2_3
import com.strings.app.data.local.db.MIGRATION_3_4
import com.strings.app.data.local.db.MIGRATION_4_5
import com.strings.app.data.local.db.MIGRATION_5_6
import com.strings.app.data.local.db.MIGRATION_6_7
import com.strings.app.data.local.db.StringsDatabase
import com.strings.app.data.prefs.DataStoreBackupSettings
import com.strings.app.data.prefs.SettingsDataStore
import com.strings.app.data.repository.FilterRepositoryImpl
import com.strings.app.data.repository.MessageRepositoryImpl
import com.strings.app.data.contacts.ContactNameResolver
import com.strings.app.data.repository.TagRepositoryImpl
import com.strings.app.data.repository.TransactionRepositoryImpl
import com.strings.app.data.sms.SmsContentReader
import com.strings.app.domain.backup.BackupSettingsStore
import com.strings.app.domain.filter.FilterDraftHolder
import com.strings.app.domain.repository.FilterRepository
import com.strings.app.domain.repository.MessageRepository
import com.strings.app.domain.repository.TagRepository
import com.strings.app.domain.repository.TransactionRepository
import com.strings.app.notification.SmsNotifier
import com.strings.app.util.DatabaseSeeder
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single<Json> {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    single<StringsDatabase> {
        Room.databaseBuilder(
            androidContext(),
            StringsDatabase::class.java,
            "strings_database"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
    }
    single { get<StringsDatabase>().messageDao() }
    single { get<StringsDatabase>().tagDao() }
    single { get<StringsDatabase>().filterDao() }
    single { get<StringsDatabase>().tabConfigDao() }
    single { get<StringsDatabase>().accountDao() }
    single { get<StringsDatabase>().accountSuggestionDao() }
    single { get<StringsDatabase>().transactionDao() }
    single<MessageRepository> { MessageRepositoryImpl(get(), get(), get()) }
    single<TagRepository> { TagRepositoryImpl(get(), get()) }
    single<FilterRepository> { FilterRepositoryImpl(get(), get()) }
    single<TransactionRepository> { TransactionRepositoryImpl(get(), get(), get()) }
    single { SettingsDataStore(androidContext()) }
    single<BackupSettingsStore> { DataStoreBackupSettings(get()) }
    single { SmsContentReader(androidContext()) }
    single { ContactNameResolver(androidContext()) }
    single { SmsNotifier(androidContext()) }
    single { DatabaseSeeder(get(), get(), get(), get(), get(), get()) }
    single { FilterDraftHolder() }
    single { WorkManager.getInstance(androidContext()) }
}

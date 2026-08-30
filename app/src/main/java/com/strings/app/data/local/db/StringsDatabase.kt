package com.strings.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.strings.app.data.local.db.dao.AccountDao
import com.strings.app.data.local.db.dao.AccountSuggestionDao
import com.strings.app.data.local.db.dao.FilterDao
import com.strings.app.data.local.db.dao.MessageDao
import com.strings.app.data.local.db.dao.TabConfigDao
import com.strings.app.data.local.db.dao.TagDao
import com.strings.app.data.local.db.dao.TransactionDao
import com.strings.app.data.local.db.entity.AccountEntity
import com.strings.app.data.local.db.entity.AccountSuggestionEntity
import com.strings.app.data.local.db.entity.FilterActionEntity
import com.strings.app.data.local.db.entity.FilterEntity
import com.strings.app.data.local.db.entity.MessageEntity
import com.strings.app.data.local.db.entity.MessageTagEntity
import com.strings.app.data.local.db.entity.TabConfigEntity
import com.strings.app.data.local.db.entity.TagEntity
import com.strings.app.data.local.db.entity.TransactionEntity

@Database(
    entities = [
        MessageEntity::class,
        TagEntity::class,
        MessageTagEntity::class,
        TabConfigEntity::class,
        FilterEntity::class,
        FilterActionEntity::class,
        AccountEntity::class,
        AccountSuggestionEntity::class,
        TransactionEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class StringsDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun tagDao(): TagDao
    abstract fun filterDao(): FilterDao
    abstract fun tabConfigDao(): TabConfigDao
    abstract fun accountDao(): AccountDao
    abstract fun accountSuggestionDao(): AccountSuggestionDao
    abstract fun transactionDao(): TransactionDao
}

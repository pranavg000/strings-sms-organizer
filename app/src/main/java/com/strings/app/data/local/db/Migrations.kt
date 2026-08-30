package com.strings.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tags ADD COLUMN icon TEXT NOT NULL DEFAULT 'label'")
    }
}

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE filters ADD COLUMN conditionTree TEXT NOT NULL " +
                "DEFAULT '{\"type\":\"group\",\"logic\":\"AND\",\"children\":[]}'"
        )
        db.execSQL("DROP TABLE IF EXISTS filter_conditions")
    }
}

// The accounts/transactions entities were registered at version 3 but no migration
// ever created their tables, so any device upgrading from v2 would crash on schema
// validation. IF NOT EXISTS makes this idempotent for fresh v3 installs that already
// have them, and the statements mirror Room's generated schema exactly.
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`bankName` TEXT NOT NULL, " +
                "`accountTail` TEXT NOT NULL, " +
                "`accountType` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`messageId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`balanceAfter` REAL, " +
                "`merchant` TEXT, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`rawMatch` TEXT NOT NULL, " +
                "FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_messageId` " +
                "ON `transactions` (`messageId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` " +
                "ON `transactions` (`accountId`)"
        )
    }
}

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN transactionTime TEXT DEFAULT NULL")
    }
}

// Accounts become user-configured: bankCode links to the public BankCatalog, colorIndex
// replaces the registry-assigned palette slot, parentAccountId replaces the hardcoded
// family links, isEnabled gates parsing. Existing rows keep their tail/type/name and show
// as "needs setup" (empty bankCode) in the Manage accounts screen.
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN bankCode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE accounts ADD COLUMN colorIndex INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE accounts ADD COLUMN parentAccountId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE accounts ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `account_suggestions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`bankCode` TEXT NOT NULL, " +
                "`accountTail` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_account_suggestions_bankCode_accountTail` " +
                "ON `account_suggestions` (`bankCode`, `accountTail`)"
        )
    }
}

val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN isSentinel INTEGER NOT NULL DEFAULT 0")
    }
}

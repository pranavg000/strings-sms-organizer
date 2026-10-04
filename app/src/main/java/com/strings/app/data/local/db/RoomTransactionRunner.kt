package com.strings.app.data.local.db

import androidx.room.withTransaction
import com.strings.app.domain.repository.TransactionRunner

class RoomTransactionRunner(private val database: StringsDatabase) : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return database.withTransaction { block() }
    }
}

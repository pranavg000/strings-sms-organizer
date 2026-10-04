package com.strings.app.domain.repository

/**
 * Runs [block] atomically against the local store: every write inside either commits
 * together or is rolled back if the block throws or is cancelled. Lets use cases stay
 * free of Room while still guaranteeing all-or-nothing multi-step mutations (e.g. a
 * backup import that must never leave the database half-restored).
 */
interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

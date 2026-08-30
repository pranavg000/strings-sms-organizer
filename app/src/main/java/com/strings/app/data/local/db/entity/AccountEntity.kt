package com.strings.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bankName: String,
    val accountTail: String,
    val accountType: String,
    val displayName: String,
    val bankCode: String = "",
    val colorIndex: Int = -1,
    val parentAccountId: Long? = null,
    val isEnabled: Boolean = true
)

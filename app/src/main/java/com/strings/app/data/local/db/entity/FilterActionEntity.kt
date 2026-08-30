package com.strings.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "filter_actions",
    foreignKeys = [
        ForeignKey(
            entity = FilterEntity::class,
            parentColumns = ["id"],
            childColumns = ["filterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("filterId")]
)
data class FilterActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val filterId: Long,
    val actionType: String,
    val targetTagId: Long? = null
)

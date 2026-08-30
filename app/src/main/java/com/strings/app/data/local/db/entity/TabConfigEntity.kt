package com.strings.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tab_configs",
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class TabConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val tagId: Long,
    val position: Int,
    val isVisible: Boolean = true
)

package com.strings.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTagId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("parentTagId")]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val color: String,
    val icon: String = "label",
    val parentTagId: Long? = null,
    val sortOrder: Int = 0,
    val isSystemTag: Boolean = false
)

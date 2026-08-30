package com.strings.app.domain.model

data class Tag(
    val id: Long = 0L,
    val name: String,
    val color: String,
    val icon: String = "label",
    val parentTagId: Long? = null,
    val sortOrder: Int = 0,
    val isSystemTag: Boolean = false
)

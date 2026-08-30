package com.strings.app.domain.model

data class TabConfig(
    val id: Long = 0L,
    val tagId: Long,
    val position: Int,
    val isVisible: Boolean = true
)

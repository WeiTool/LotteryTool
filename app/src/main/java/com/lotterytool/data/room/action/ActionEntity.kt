package com.lotterytool.data.room.action

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_info")
data class ActionEntity(
    @PrimaryKey
    val dynamicId: Long,
    val repostResult: String? = null,
    val likeResult: String? = null,
    val replyResult: String? = null,
    val followResult: String? = null,
)
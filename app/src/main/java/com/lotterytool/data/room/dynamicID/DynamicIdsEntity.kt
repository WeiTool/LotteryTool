package com.lotterytool.data.room.dynamicID

import androidx.room.Entity

@Entity(
    tableName = "dynamic_ids",
    primaryKeys = ["articleId", "dynamicId"],
)
data class DynamicIdEntity(
    val articleId: Long,
    val dynamicId: Long,
    val isSpecial: Boolean
)
package com.lotterytool.data.room.dynamicID

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "dynamic_ids",
    primaryKeys = ["articleId", "dynamicId"],
    indices = [Index(value = ["dynamicId"])]
)
data class DynamicIdEntity(
    val articleId: Long,
    val dynamicId: Long,
    val isSpecial: Boolean
)
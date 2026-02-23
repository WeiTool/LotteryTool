package com.lotterytool.data.room.dynamicID

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dynamic_ids")
data class DynamicIdsEntity(
    @PrimaryKey val articleId: Long,          // 关联的文章ID
    val normalDynamicIds: List<Long>,         // 匹配到的普通动态ID列表
    val specialDynamicIds: List<Long>,        // 匹配到的特殊动态ID列表
)
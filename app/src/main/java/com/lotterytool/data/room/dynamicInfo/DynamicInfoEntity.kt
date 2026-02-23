package com.lotterytool.data.room.dynamicInfo

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dynamic_info",
    indices = [Index(value = ["articleId"])] // 建立索引，保证查询效率
)
data class DynamicInfoEntity(
    @PrimaryKey val dynamicId: Long, // 动态自身的唯一标识
    val articleId: Long,             // 所属的文章ID
    val content: String,
    var description: String ,
    val timestamp: Long,
    var uid: Long,
    var rid: Long,
    var type: Int,
    val errorMessage: String? = null

)
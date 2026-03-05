package com.lotterytool.data.room.dynamicInfo

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dynamic_info",
    indices = [Index(value = ["articleId"])]
)
data class DynamicInfoEntity(
    @PrimaryKey val dynamicId: Long,
    val articleId: Long,
    val content: String,
    var description: String ,
    val timestamp: Long,
    var uid: Long,
    var rid: Long,
    var type: Int,
    val errorMessage: String? = null

)
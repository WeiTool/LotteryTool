package com.lotterytool.data.room.userDynamic

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_dynamic")
data class UserDynamicEntity(
    @PrimaryKey val serviceId: Long,
    val dynamicId: Long,
    val mid: Long,
    val type: String,
    val offset: String,
    val lastUpdated: Long = System.currentTimeMillis() / 1000
)

package com.lotterytool.data.room.user

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    // UID
    @PrimaryKey val mid: Long,
    // 头像Url
    val face: String,
    // 用户名
    val uname: String,
    // 是否登陆
    val isLogin: Boolean,
    // Wbi签名ID
    val imgUrlId: String,
    val subUrlId: String,
    // Cookie
    val SESSDATA: String,
    val CSRF: String,
    // 系统存入时间
    val lastUpdated: Long = System.currentTimeMillis()
)
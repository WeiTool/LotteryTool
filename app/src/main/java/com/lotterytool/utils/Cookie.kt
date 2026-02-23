package com.lotterytool.utils

import androidx.core.net.toUri

data class BiliAuth(
    val sessData: String,
    val csrf: String
){
    // 直接输出带前缀的 Cookie 格式
    val sessDataCookie: String get() = "SESSDATA=$sessData"
    val csrfCookie: String get() = "bili_jct=$csrf"
}

/**
 * 解析 Bilibili 登录回调 URL 的工具扩展
 */
fun String.parseBiliAuth(): BiliAuth? {
    return try {
        val uri = this.toUri()
        val sessData = uri.getQueryParameter("SESSDATA")
        val csrf = uri.getQueryParameter("bili_jct")

        if (sessData != null && csrf != null) {
            BiliAuth(sessData, csrf)
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
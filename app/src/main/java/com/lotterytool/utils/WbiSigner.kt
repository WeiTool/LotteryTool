package com.lotterytool.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.TreeMap

object WbiSigner {

    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    private fun getMixinKey(imgKey: String, subKey: String): String {
        val s = imgKey + subKey
        val key = StringBuilder()
        mixinKeyEncTab.take(32).forEach { index ->
            key.append(s[index])
        }
        return key.toString()
    }

    private fun encodeURIComponent(s: String): String {
        return try {
            URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20")
        } catch (e: Exception) {
            ""
        }
    }

    private fun String.md5(): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun sign(
        params: Map<String, String>,
        imgKey: String,
        subKey: String
    ): Map<String, String> {
        val mixinKey = getMixinKey(imgKey, subKey)
        val newParams = params.toMutableMap()
        newParams["wts"] = (System.currentTimeMillis() / 1000).toString()

        val sortedParams = TreeMap(newParams)

        val paramString = sortedParams.entries.joinToString("&") { (key, value) ->
            "${encodeURIComponent(key)}=${encodeURIComponent(value)}"
        }

        val wbiSign = (paramString + mixinKey).md5()

        val finalParams = sortedParams.toMutableMap()
        finalParams["w_rid"] = wbiSign
        return finalParams
    }
}

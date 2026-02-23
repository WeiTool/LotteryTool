package com.lotterytool.data.room.dynamicID

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromList(value: List<Long>?): String {
        // 处理传入列表可能为空的情况，返回空字符串而不是抛错
        return value?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toList(value: String?): List<Long> {
        // 增加对 null 或空字符串的健壮性判断
        if (value.isNullOrBlank()) return emptyList()

        return value.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull {
                // 使用 toLongOrNull() 避免字符串包含非数字字符时导致进程崩溃
                it.trim().toLongOrNull()
            }
    }
}
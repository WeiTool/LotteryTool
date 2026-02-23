package com.lotterytool.data.room.log

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crash_logs")
data class CrashLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,          // 崩溃时间
    val crashDetail: String,      // 堆栈信息
    val deviceModel: String,      // 设备型号
    val androidVersion: String    // 系统版本
)
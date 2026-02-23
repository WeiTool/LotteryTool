package com.lotterytool.data.room.log

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CrashLogDao {

    // 同步版本专供 CrashHandler 调用，避免在崩溃现场启动协程作用域
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCrashSync(entity: CrashLogEntity)

    @Query("SELECT * FROM crash_logs ORDER BY id DESC LIMIT 1")
    suspend fun getLatestCrash(): CrashLogEntity?

    @Query("DELETE FROM crash_logs")
    suspend fun clearAll()
}
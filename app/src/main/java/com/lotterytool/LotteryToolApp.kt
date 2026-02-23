package com.lotterytool

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lotterytool.data.log.CrashHandler
import com.lotterytool.data.room.AppDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LotteryToolApp : Application(), Configuration.Provider {

    @Inject lateinit var database: AppDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // 初始化崩溃处理器
        // 建议：如果以后 CrashHandler 需要更多参数，可以考虑也用 Hilt 注入 CrashHandler
        initCrashHandler()
    }

    private fun initCrashHandler() {
        // 传入 Hilt 注入的数据库实例
        CrashHandler(database)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
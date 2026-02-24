package com.lotterytool

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lotterytool.data.log.CrashHandler
import com.lotterytool.data.room.AppDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用程序入口
 * 负责全局组件初始化：Hilt, WorkManager, 以及自定义崩溃拦截器
 */
@HiltAndroidApp
class LotteryToolApp : Application(), Configuration.Provider {

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // 初始化崩溃拦截处理器
        // 这将激活 CrashHandler.kt 中的 Thread.setDefaultUncaughtExceptionHandler 逻辑
        initCrashHandler()
    }

    private fun initCrashHandler() {
        // 直接使用你定义的 CrashHandler 类
        // 传入注入的数据库实例，确保崩溃时能同步保存日志
        CrashHandler(database)
    }

    /**
     * 自定义 WorkManager 配置，以支持 Hilt 注入 Worker
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
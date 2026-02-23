package com.lotterytool

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lotterytool.data.room.AppDatabase
import com.lotterytool.data.room.log.CrashLogEntity
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class LotteryToolApp : Application(), Configuration.Provider {

    @Inject lateinit var database: AppDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        initCrashHandler()
    }

    private fun initCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val crashDetail = android.util.Log.getStackTraceString(throwable)

            // 将崩溃信息同步写入数据库，供下次启动时由 CrashActivity 读取展示
            try {
                val entity = CrashLogEntity(
                    timestamp = System.currentTimeMillis(),
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidVersion = Build.VERSION.RELEASE,
                    crashDetail = crashDetail
                )
                // 崩溃处理器不在协程上下文中，使用后台线程同步写入后再继续
                val thread = Thread { database.crashLogDao().insertCrashSync(entity) }
                thread.start()
                thread.join(2_000) // 最多等待 2 秒，避免因 DB 异常卡死
            } catch (_: Exception) {
                // 数据库写入失败时静默忽略，不影响后续崩溃处理流程
            }

            // 启动 CrashActivity（下次启动时会读取 DB，此处无需传 Extra）
            // 注意：此处不再启动 CrashActivity，让应用正常退出；
            // 下次用户主动打开 APP 时 CrashActivity 会自动检测到日志并展示弹窗。
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
package com.lotterytool.data.log

import android.os.Build
import com.lotterytool.data.room.AppDatabase
import com.lotterytool.data.room.log.CrashLogEntity
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 崩溃拦截处理器
 * 核心逻辑：在进程彻底退出前，尝试将堆栈信息存入本地数据库
 */
class CrashHandler(private val db: AppDatabase) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        try {
            // 1. 提取异常详细信息
            val writer = StringWriter()
            ex.printStackTrace(PrintWriter(writer))

            val entity = CrashLogEntity(
                timestamp = System.currentTimeMillis(),
                crashDetail = writer.toString(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = Build.VERSION.RELEASE
            )

            // 2. 开启临时线程执行同步写入
            val writeThread = Thread {
                try {
                    // 注意：此处调用的是同步方法，不依赖协程
                    db.crashLogDao().insertCrashSync(entity)
                } catch (dbEx: Exception) {
                    // 数据库写入失败（如磁盘空间不足、数据库锁定）时不做处理，防止二次崩溃
                    dbEx.printStackTrace()
                }
            }

            writeThread.start()

            // 3. 等待写入完成（最多 500ms），避免阻塞时间过长导致系统判定 ANR
            writeThread.join(500)

        } catch (e: Exception) {
            // 兜底逻辑：记录逻辑本身抛出异常时，确保不干扰后续系统处理
            e.printStackTrace()
        } finally {
            // 4. 必须交还给系统处理器，完成后续的进程杀掉、弹窗提示等操作
            defaultHandler?.uncaughtException(thread, ex)
        }
    }
}
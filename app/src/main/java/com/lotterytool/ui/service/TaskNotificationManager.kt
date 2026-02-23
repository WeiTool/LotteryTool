package com.lotterytool.ui.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.lotterytool.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "task_channel"
        const val NOTIFICATION_ID = 1001
    }

    // ── WakeLock：确保 CPU 在熄屏时不进入深度睡眠 ──────────────────────────────
    // 在前台服务存活期间持有，任务结束或异常时释放。
    // WorkManager 内部已有 PartialWakeLock，这里额外持有是为了应对部分国产 ROM
    // 绕过 WorkManager 直接杀死进程的极端情况。
    private val wakeLock: PowerManager.WakeLock by lazy {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LotteryTool::TaskWakeLock"
            ).apply {
                // 设置超时上限（2 小时），防止因任务异常未释放导致持续耗电
                setReferenceCounted(false)
            }
    }

    /**
     * 获取初始的 ForegroundInfo，同时持有 WakeLock。
     * 在 [ExtractDynamicWorker.doWork] 开头调用。
     */
    fun getForegroundInfo(articleId: Long): ForegroundInfo {
        acquireWakeLock()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                buildNotification(articleId, 0, 0),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, buildNotification(articleId, 0, 0))
        }
    }

    /**
     * 动态更新进度通知。
     */
    fun updateProgress(articleId: Long, current: Int, total: Int) {
        val notification = buildNotification(articleId, current, total)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 显示错误通知，并释放 WakeLock（任务已终止，不再需要保持 CPU 唤醒）。
     */
    fun updateError(articleId: Long, errorMessage: String) {
        val notification = buildNotification(articleId, isError = true, errorMsg = errorMessage)
        notificationManager.notify(NOTIFICATION_ID, notification)
        releaseWakeLock()
    }

    /**
     * 任务正常完成时调用，释放 WakeLock。
     * 在 [ExtractDynamicWorker.doWork] 返回 [Result.success()] 前调用。
     */
    fun onTaskComplete() {
        releaseWakeLock()
    }

    // ── WakeLock 管理 ─────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) {
            // 超时 2 小时，防止永久持锁
            wakeLock.acquire(2 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    // ── 通知构建 ──────────────────────────────────────────────────────────────

    private fun buildNotification(
        articleId: Long,
        current: Int = 0,
        total: Int = 0,
        isError: Boolean = false,
        errorMsg: String? = null
    ): Notification {
        createNotificationChannel()

        val title = if (isError) "文章 $articleId 处理出错" else "正在处理文章: $articleId"
        val contentText = when {
            isError -> errorMsg ?: "未知错误"
            total > 0 -> "进度: $current / $total"
            else -> "准备中..."
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(
                if (isError) android.R.drawable.stat_notify_error
                else R.mipmap.ic_launcher
            )
            // ongoing=true 时系统会尽力维持前台服务不被杀死
            .setOngoing(!isError)
            .setAutoCancel(isError)
            .setProgress(
                if (isError) 0 else total,
                if (isError) 0 else current,
                !isError && total == 0   // indeterminate（不定进度条）
            )
            // 出错用 HIGH，正常进度用 DEFAULT（不要用 LOW，国产 ROM 会降低前台服务优先级）
            .setPriority(
                if (isError) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setSilent(!isError)
            // 防止国产 ROM 将通知折叠/压缩进"后台应用"组而失去 ongoing 效果
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "后台任务状态",
                    // 必须用 IMPORTANCE_DEFAULT 或以上，国产 ROM 才不会把前台服务折叠压制
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "用于显示抽奖助手后台任务的运行进度"
                    // 普通进度条通知不需要震动，只有出错时才响
                    enableVibration(false)
                    setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
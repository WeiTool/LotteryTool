package com.lotterytool.ui.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
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

    private val wakeLock: PowerManager.WakeLock by lazy {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LotteryTool::TaskWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
    }

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

    fun updateProgress(articleId: Long, current: Int, total: Int) {
        val notification = buildNotification(articleId, current, total)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 进入同步阶段时调用：隐藏进度条，显示"正在同步个人动态..."文字。
     */
    fun updateSyncing(articleId: Long) {
        val notification = buildNotification(articleId, isSyncing = true)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun updateError(articleId: Long, errorMessage: String) {
        val notification = buildNotification(articleId, isError = true, errorMsg = errorMessage)
        notificationManager.notify(NOTIFICATION_ID, notification)
        releaseWakeLock()
    }

    fun onTaskComplete() {
        releaseWakeLock()
    }

    // ── WakeLock 管理 ─────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) {
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
        errorMsg: String? = null,
        isSyncing: Boolean = false
    ): Notification {
        createNotificationChannel()

        val title = when {
            isError    -> "文章 $articleId 处理出错"
            isSyncing  -> "文章 $articleId 正在同步"
            else       -> "正在处理文章: $articleId"
        }

        val contentText = when {
            isError   -> errorMsg ?: "未知错误"
            isSyncing -> "正在同步个人动态..."
            total > 0 -> "进度: $current / $total"
            else      -> "准备中..."
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(
                if (isError) android.R.drawable.stat_notify_error
                else R.mipmap.ic_launcher
            )
            .setOngoing(!isError)
            .setAutoCancel(isError)
            .setProgress(
                when {
                    isError   -> 0
                    isSyncing -> 0      // 同步阶段：max=0 且 indeterminate=false → 不显示进度条
                    else      -> total
                },
                when {
                    isError   -> 0
                    isSyncing -> 0
                    else      -> current
                },
                // indeterminate：只在"准备中"（total==0 且非同步阶段）时显示转圈
                !isError && !isSyncing && total == 0
            )
            .setPriority(
                if (isError) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setSilent(!isError)
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
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "用于显示抽奖助手后台任务的运行进度"
                    enableVibration(false)
                    setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
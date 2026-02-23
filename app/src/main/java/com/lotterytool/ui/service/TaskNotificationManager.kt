package com.lotterytool.ui.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "task_channel"
        const val NOTIFICATION_ID = 1001
    }

    /**
     * 获取初始的 ForegroundInfo
     */
    fun getForegroundInfo(articleId: Long): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, buildNotification(articleId, 0, 0))
    }

    /**
     * 动态更新进度通知
     * @param articleId 当前处理的文章ID
     * @param current 当前完成数
     * @param total 总数
     */
    fun updateProgress(articleId: Long, current: Int, total: Int) {
        val notification = buildNotification(articleId, current, total)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun updateError(articleId: Long, errorMessage: String) {
        val notification = buildNotification(articleId, isError = true, errorMsg = errorMessage)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

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
            .setSmallIcon(if (isError) android.R.drawable.stat_notify_error else R.mipmap.ic_launcher)
            .setOngoing(!isError) // 报错后允许用户划掉通知
            .setAutoCancel(isError) // 报错后点击通知可自动消失
            .setProgress(if (isError) 0 else total, if (isError) 0 else current, !isError && total == 0)
            .setPriority(if (isError) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setSilent(!isError) // 只有出错时才震动/响铃提醒
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "后台任务状态",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "用于显示抽奖助手后台任务的运行进度"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
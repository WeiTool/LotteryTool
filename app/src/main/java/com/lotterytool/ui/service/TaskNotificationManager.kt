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

    // ═══════════════════════════════════════════════════════════════════════════
    // 国产 ROM 省电白名单引导
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 检查当前应用是否已被系统省电优化豁免（即在白名单中）。
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 弹出系统对话框，引导用户将本应用加入"省电白名单"（即忽略电池优化）。
     * 需要在 AndroidManifest 中声明 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限。
     *
     * 调用时机：在任务启动前（例如用户点击"开始执行"按钮时）检查并引导，
     * 而不是在 Worker 内部调用（Worker 无法弹出 UI）。
     *
     * 国产 ROM 额外设置说明（代码无法自动完成，需引导用户手动操作）：
     *
     * ── 小米 / Redmi / MIUI ──────────────────────────────────────────────────
     *   设置 → 应用 → 找到本应用 → 省电策略 → 选择"无限制"
     *   设置 → 应用 → 找到本应用 → 自启动 → 开启
     *   （MIUI 13+ 还需关闭：设置 → 省电与电池 → 省电模式 下"限制后台应用"）
     *
     * ── 华为 / 荣耀 / HarmonyOS / EMUI ──────────────────────────────────────
     *   设置 → 应用 → 应用管理 → 找到本应用 → 省电 → 手动管理 → 后台活动 开启
     *   设置 → 电池 → 启动管理 → 找到本应用 → 手动管理 → 自动运行 / 后台活动 全部勾选
     *
     * ── VIVO / iQOO / OriginOS / FuntouchOS ─────────────────────────────────
     *   设置 → 电池 → 后台高耗电 → 找到本应用 → 开启
     *   设置 → 应用与服务 → 找到本应用 → 权限 → 后台弹出界面 → 开启
     *   i管家 → 软件管理 → 自启动管理 → 找到本应用 → 开启
     *
     * ── OPPO / 一加 / ColorOS ────────────────────────────────────────────────
     *   设置 → 电池 → 省电设置 → 长时间待机时 → 关闭"限制后台活动"
     *   设置 → 应用管理 → 找到本应用 → 省电 → 允许后台运行
     *   手机管家 → 权限隐私 → 自启动管理 → 找到本应用 → 开启
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (isIgnoringBatteryOptimizations()) return // 已在白名单，无需再弹
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
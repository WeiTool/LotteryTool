package com.lotterytool

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lotterytool.data.room.AppDatabase
import com.lotterytool.data.room.log.CrashLogEntity
import com.lotterytool.ui.article.ArticleScreen
import com.lotterytool.ui.dynamicInfo.DynamicInfoScreen
import com.lotterytool.ui.dynamicList.DynamicListScreen
import com.lotterytool.ui.theme.LotteryToolTheme
import com.lotterytool.ui.user.UserScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: AppDatabase
    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val PREF_NOTIF_ASKED = "notification_permission_asked"
        private const val PREF_BATTERY_ASKED = "battery_optimization_asked"
    }

    // ── 步骤 1：省电白名单申请回调 ────────────────────────────────────────────
    // 系统弹出"忽略电池优化"对话框后，无论用户选择允许还是拒绝都会回调此处，
    // 回调完成后立即进入步骤 2（通知权限申请）。
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 不管用户是否同意，都标记已询问，并继续申请通知权限
        markBatteryAsked()
        checkAndRequestNotificationPermission()
    }

    // ── 步骤 2：通知权限申请回调（Android 13+）────────────────────────────────
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        markNotifAsked()
        if (!isGranted) {
            Toast.makeText(this, "通知权限已禁用，进度将仅在应用内显示", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkAndRequestBatteryOptimization()

        setContent {
            LotteryToolTheme(dynamicColor = false) {
                var crashLog by remember { mutableStateOf<CrashLogEntity?>(null) }

                LaunchedEffect(Unit) {
                    crashLog = withContext(Dispatchers.IO) {
                        database.crashLogDao().getLatestCrash()
                    }
                }

                crashLog?.let { log ->
                    CrashReportDialog(
                        log = log,
                        onDismiss = { crashLog = null },
                        onConfirm = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                database.crashLogDao().clearAll()
                            }
                            crashLog = null
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "user_screen") {
                        composable("user_screen") {
                            UserScreen(
                                onCardClick = { mid -> navController.navigate("article_screen/$mid") },
                                viewModel = hiltViewModel()
                            )
                        }
                        composable(
                            route = "article_screen/{mid}",
                            arguments = listOf(navArgument("mid") { type = NavType.LongType })
                        ) {
                            val mid = it.arguments?.getLong("mid") ?: 0L
                            ArticleScreen(onCardClick = { id ->
                                navController.navigate("dynamic_list_screen/$id/$mid")
                            })
                        }
                        composable(
                            route = "dynamic_list_screen/{articleId}/{userMid}",
                            arguments = listOf(
                                navArgument("articleId") { type = NavType.LongType },
                                navArgument("userMid") { type = NavType.LongType }
                            )
                        ) {
                            val mid = it.arguments?.getLong("userMid") ?: 0L
                            DynamicListScreen(onNavigateToDetail = { id, type ->
                                navController.navigate("dynamic_info_screen/$id/$type/$mid")
                            })
                        }
                        composable(
                            route = "dynamic_info_screen/{articleId}/{type}/{userMid}",
                            arguments = listOf(
                                navArgument("articleId") { type = NavType.LongType },
                                navArgument("type") { type = NavType.IntType },
                                navArgument("userMid") { type = NavType.LongType }
                            )
                        ) {
                            DynamicInfoScreen()
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 步骤 1：省电白名单（忽略电池优化）
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 检查是否需要申请省电白名单。
     * - 已在白名单中：跳过，直接进入步骤 2
     * - 未在白名单且未问过：弹系统对话框
     * - 未在白名单但已问过：不再重复打扰，直接进入步骤 2
     *
     * minSdk = 24，而 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 在 API 23 引入，
     * 因此无需版本判断，直接使用。
     */
    private fun checkAndRequestBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)

        when {
            isIgnoring -> {
                // 已在白名单，跳过电池弹窗，直接申请通知
                checkAndRequestNotificationPermission()
            }
            !hasBatteryAsked() -> {
                // 首次询问，触发系统弹窗（ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 会直接
                // 弹出"允许/拒绝"对话框，无需跳转设置页，用户体验更好）
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri()
                }
                try {
                    batteryOptimizationLauncher.launch(intent)
                } catch (e: Exception) {
                    // 极少数 ROM 不支持此 Action，静默跳过并继续
                    markBatteryAsked()
                    checkAndRequestNotificationPermission()
                }
            }
            else -> {
                // 已问过但用户拒绝，不再重复，直接进入步骤 2
                checkAndRequestNotificationPermission()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 步骤 2：通知权限
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 通知权限处理，兼容 API 24 ~ latest：
     *
     * - API 33+（Android 13+）：运行时权限，直接弹系统授权对话框。
     * - API 26~32（Android 8~12）：无运行时通知权限概念，但用户可在设置里关闭，
     *                              若已关闭则引导用户去设置页手动开启。
     * - API 24~25（Android 7）：同上，设置跳转方式略有不同，已兼容处理。
     */
    private fun checkAndRequestNotificationPermission() {
        val notifEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()

        when {
            notifEnabled -> return // 通知已开启，无需操作

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+：运行时权限
                val permission = Manifest.permission.POST_NOTIFICATIONS
                val granted = ContextCompat.checkSelfPermission(this, permission) ==
                        PackageManager.PERMISSION_GRANTED
                if (!granted && (!hasNotifAsked() || shouldShowRequestPermissionRationale(permission))) {
                    requestNotificationPermissionLauncher.launch(permission)
                }
            }

            !hasNotifAsked() -> {
                // Android 7~12：通知权限无需运行时申请，但若被关闭则引导去设置页
                showNotificationGuideDialog()
            }
        }
    }

    /**
     * 引导用户跳转到通知设置页（适用于 API 24~32）。
     */
    private fun showNotificationGuideDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("开启通知权限")
            .setMessage("为了确保你能收到抽奖进度提醒，请开启应用通知权限。")
            .setPositiveButton("去设置") { _, _ ->
                markNotifAsked()
                val intent = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        }
                    }
                    else -> {
                        // API 24~25 兼容写法
                        Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                            putExtra("app_package", packageName)
                            putExtra("app_uid", applicationInfo.uid)
                        }
                    }
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // 兜底：跳转应用详情页
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:$packageName".toUri()
                        }
                    )
                }
            }
            .setNegativeButton("取消") { _, _ -> markNotifAsked() }
            .setCancelable(false)
            .show()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SharedPreferences 辅助
    // ═══════════════════════════════════════════════════════════════════════════

    private fun hasBatteryAsked(): Boolean =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_BATTERY_ASKED, false)

    private fun markBatteryAsked() =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean(PREF_BATTERY_ASKED, true) }

    private fun hasNotifAsked(): Boolean =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_NOTIF_ASKED, false)

    private fun markNotifAsked() =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean(PREF_NOTIF_ASKED, true) }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 崩溃报告弹窗
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CrashReportDialog(
    log: CrashLogEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = remember(log.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("了解并清除") }
        },
        dismissButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val fullLog = """
                    Time: $dateStr
                    Model: ${log.deviceModel}
                    Android: ${log.androidVersion}
                    Detail: 
                    ${log.crashDetail}
                """.trimIndent()
                val clip = ClipData.newPlainText("Crash Log", fullLog)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }) {
                Text("复制日志")
            }
        },
        title = { Text("程序异常退出报告") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("时间: $dateStr", fontSize = 12.sp)
                Text("机型: ${log.deviceModel} (Android ${log.androidVersion})", fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = log.crashDetail,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        }
    )
}
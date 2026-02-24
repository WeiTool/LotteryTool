package com.lotterytool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lotterytool.ui.article.ArticleScreen
import com.lotterytool.ui.dynamicInfo.DynamicInfoScreen
import com.lotterytool.ui.dynamicList.DynamicListScreen
import com.lotterytool.ui.theme.LotteryToolTheme
import com.lotterytool.ui.user.UserScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val PREF_NOTIF_ASKED = "notification_permission_asked"
    }

    // ── 通知权限申请回调（Android 13+）────────────────────────────────────────
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

        setContent {
            LotteryToolTheme(dynamicColor = false) {
                // 1. 定义控制弹窗显示的状态
                var showNotifDialog by remember { mutableStateOf(false) }

                // 2. 进入页面时触发权限检查逻辑
                LaunchedEffect(Unit) {
                    checkAndRequestNotificationPermission(
                        onShowGuide = { showNotifDialog = true }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    // 3. 渲染权限引导弹窗
                    if (showNotifDialog) {
                        NotificationGuideDialog(
                            onDismiss = {
                                markNotifAsked()
                                showNotifDialog = false
                            },
                            onConfirm = {
                                showNotifDialog = false
                                openNotificationSettings()
                            }
                        )
                    }

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
    // 通知权限逻辑层
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 通知权限处理逻辑：
     * @param onShowGuide 当检测到需要显示自定义弹窗引导时执行的回调
     */
    private fun checkAndRequestNotificationPermission(onShowGuide: () -> Unit) {
        val notifEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()

        when {
            notifEnabled -> return

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val permission = Manifest.permission.POST_NOTIFICATIONS
                val granted = ContextCompat.checkSelfPermission(this, permission) ==
                        PackageManager.PERMISSION_GRANTED
                if (!granted && (!hasNotifAsked() || shouldShowRequestPermissionRationale(permission))) {
                    requestNotificationPermissionLauncher.launch(permission)
                }
            }

            // 对于 API 33 以下且权限关闭的情况，触发弹窗引导回调
            !hasNotifAsked() -> onShowGuide()
        }
    }

    /** 核心逻辑：跳转到通知设置页 */
    private fun openNotificationSettings() {
        markNotifAsked()
        val intent = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            }
            else -> {
                Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                    putExtra("app_package", packageName)
                    putExtra("app_uid", applicationInfo.uid)
                }
            }
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:$packageName".toUri()
                }
            )
        }
    }

    private fun hasNotifAsked(): Boolean =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_NOTIF_ASKED, false)

    private fun markNotifAsked() =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean(PREF_NOTIF_ASKED, true) }
}

// ═══════════════════════════════════════════════════════════════════════════
// Compose UI 组件
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 权限引导弹窗 - Compose 风格
 */
@Composable
fun NotificationGuideDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "开启通知权限") },
        text = { Text(text = "为了确保你能收到抽奖进度提醒，请开启应用通知权限。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("去设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

        // 检查并请求通知权限
        checkAndRequestNotificationPermission()

        setContent {
            LotteryToolTheme(dynamicColor = false) {
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
    // 通知权限
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 通知权限处理，兼容 API 24 ~ latest：
     * - API 33+（Android 13+）：运行时权限，直接弹系统授权对话框。
     * - API 26~32（Android 8~12）：无运行时通知权限，若已关闭则引导去设置页手动开启。
     * - API 24~25（Android 7）：同上，设置跳转方式略有不同，已兼容处理。
     */
    private fun checkAndRequestNotificationPermission() {
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

            !hasNotifAsked() -> showNotificationGuideDialog()
        }
    }

    /** 引导用户跳转到通知设置页（适用于 API 24~32）。 */
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
                        Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                            putExtra("app_package", packageName)
                            putExtra("app_uid", applicationInfo.uid)
                        }
                    }
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
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

    private fun hasNotifAsked(): Boolean =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_NOTIF_ASKED, false)

    private fun markNotifAsked() =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean(PREF_NOTIF_ASKED, true) }
}
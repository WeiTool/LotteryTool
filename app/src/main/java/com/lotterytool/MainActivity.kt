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
import androidx.compose.ui.platform.LocalUriHandler
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
import com.lotterytool.data.repository.UpdateResult
import com.lotterytool.ui.CheckVersionViewModel
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

    // 抽取为属性，避免重复调用 getSharedPreferences
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

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
                val versionViewModel: CheckVersionViewModel = hiltViewModel()
                val uriHandler = LocalUriHandler.current
                var showNotifDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    versionViewModel.checkForUpdates()
                    checkAndRequestNotificationPermission(
                        onShowGuide = { showNotifDialog = true }
                    )
                }

                val updateResult = versionViewModel.updateState
                if (updateResult is UpdateResult.HasUpdate) {
                    AppAlertDialog(
                        title = "发现新版本: ${updateResult.latestVersion}",
                        text = updateResult.releaseNotes.ifBlank { "修复了一些已知问题" },
                        confirmText = "下载",
                        dismissText = "忽略",
                        onDismiss = { versionViewModel.dismissDialog() },
                        onConfirm = {
                            updateResult.downloadUrl?.let { uriHandler.openUri(it) }
                            versionViewModel.dismissDialog()
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    if (showNotifDialog) {
                        AppAlertDialog(
                            title = "开启通知权限",
                            text = "为了确保你能收到抽奖进度提醒，请开启应用通知权限。",
                            confirmText = "去设置",
                            dismissText = "取消",
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

            !hasNotifAsked() -> onShowGuide()
        }
    }

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

    private fun hasNotifAsked(): Boolean = prefs.getBoolean(PREF_NOTIF_ASKED, false)

    private fun markNotifAsked() = prefs.edit { putBoolean(PREF_NOTIF_ASKED, true) }
}

// ═══════════════════════════════════════════════════════════════════════════
// Compose UI 组件
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 通用弹窗组件，替代原来重复的 NotificationGuideDialog 和 UpdateDialog
 */
@Composable
fun AppAlertDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        }
    )
}
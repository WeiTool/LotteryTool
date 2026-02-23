package com.lotterytool

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
import com.lotterytool.ui.user.UserViewModel
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
    private val PREFS_NAME = "app_settings"
    private val PREF_NOTIF_ASKED = "notification_permission_asked"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        markPermissionAsAsked()
        if (!isGranted) {
            Toast.makeText(this, "通知权限已禁用，进度将仅在应用内显示", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkAndRequestNotificationPermission()

        setContent {
            LotteryToolTheme(dynamicColor = false) {
                // 崩溃日志显示逻辑
                var crashLog by remember { mutableStateOf<CrashLogEntity?>(null) }

                LaunchedEffect(Unit) {
                    // 在后台线程检查数据库
                    val latest = withContext(Dispatchers.IO) {
                        database.crashLogDao().getLatestCrash()
                    }
                    crashLog = latest
                }

                // 崩溃弹窗 UI
                crashLog?.let { log ->
                    AlertDialog(
                        onDismissRequest = { crashLog = null },
                        confirmButton = {
                            TextButton(onClick = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    database.crashLogDao().clearAll()
                                }
                                crashLog = null
                            }) { Text("了解并清除") }
                        },
                        title = { Text("程序异常退出报告") },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                val dateStr = SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss",
                                    Locale.getDefault()
                                ).format(Date(log.timestamp))
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "user_screen"
                    ) {

                        // ── 用户列表页 ─────────────────────────────────────────
                        composable("user_screen") {
                            val userViewModel: UserViewModel = hiltViewModel()
                            UserScreen(
                                onCardClick = { mid ->
                                    navController.navigate("article_screen/$mid")
                                },
                                viewModel = userViewModel
                            )
                        }

                        // ── 专栏列表页（携带 mid，供 ArticleViewModel 读取） ──
                        composable(
                            route = "article_screen/{mid}",
                            arguments = listOf(
                                navArgument("mid") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            // 从当前路由取出 userMid，用于拼接下一层路由
                            val userMid = backStackEntry.arguments?.getLong("mid") ?: 0L

                            // ArticleViewModel 通过 SavedStateHandle 自动接收 "mid"
                            ArticleScreen(
                                onCardClick = { articleId ->
                                    // 将 userMid 一并传入动态列表路由，避免后续丢失
                                    navController.navigate("dynamic_list_screen/$articleId/$userMid")
                                }
                            )
                        }

                        // ── 动态汇总页（同时携带 articleId 和 userMid） ────────
                        composable(
                            route = "dynamic_list_screen/{articleId}/{userMid}",
                            arguments = listOf(
                                navArgument("articleId") { type = NavType.LongType },
                                navArgument("userMid")   { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            // DynamicListScreen 的 onNavigateToDetail 只暴露 (articleId, type)，
                            // 所以在这里从路由中捕获 userMid，并在 lambda 内闭包传递给下一层。
                            val userMid = backStackEntry.arguments?.getLong("userMid") ?: 0L

                            DynamicListScreen(
                                onNavigateToDetail = { articleId, type ->
                                    navController.navigate("dynamic_info_screen/$articleId/$type/$userMid")
                                }
                            )
                        }

                        // ── 动态详情页（携带 articleId、type、userMid） ─────────
                        composable(
                            route = "dynamic_info_screen/{articleId}/{type}/{userMid}",
                            arguments = listOf(
                                navArgument("articleId") { type = NavType.LongType },
                                navArgument("type")      { type = NavType.IntType  },
                                navArgument("userMid")   { type = NavType.LongType }
                            )
                        ) {
                            // DynamicInfoViewModel 通过 SavedStateHandle 自动接收
                            // "articleId"、"type"、"userMid" 三个参数
                            DynamicInfoScreen()
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            when {
                // 已授权
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    // 已有权限，无需操作
                }
                // 曾经拒绝过，系统建议显示解释（这里可以根据需要弹出自定义 Dialog）
                shouldShowRequestPermissionRationale(permission) -> {
                    requestPermissionLauncher.launch(permission)
                }
                // 第一次请求或未询问过
                else -> {
                    // 建议稍微延迟执行，确保 Activity 窗口已获得焦点
                    window.decorView.postDelayed({
                        if (!hasAskedPermissionBefore()) {
                            requestPermissionLauncher.launch(permission)
                        }
                    }, 500)
                }
            }
        }
    }

    private fun hasAskedPermissionBefore(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(PREF_NOTIF_ASKED, false)
    }

    private fun markPermissionAsAsked() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit { putBoolean(PREF_NOTIF_ASKED, true) }
    }
}
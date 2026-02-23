package com.lotterytool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotterytool.data.room.AppDatabase
import com.lotterytool.data.room.log.CrashLogEntity
import com.lotterytool.ui.theme.LotteryToolTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 启动页 Activity（在 AndroidManifest.xml 中配置为 launcher）。
 *
 * 生命周期说明：
 * - 无崩溃日志 → 立即跳转 MainActivity 并调用 finish()，不展示任何 UI，不占用任何资源。
 * - 有崩溃日志 → 展示崩溃报告弹窗；用户操作完毕后同样跳转 MainActivity 并 finish()。
 */
@AndroidEntryPoint
class CrashActivity : ComponentActivity() {

    @Inject
    lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LotteryToolTheme(dynamicColor = false) {
                val scope = rememberCoroutineScope()

                // null  = 正在加载；CrashLogEntity? = 加载完毕（可能无日志）
                var crashLog by remember { mutableStateOf<CrashLogEntity?>(null) }
                var loaded by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val log = withContext(Dispatchers.IO) {
                        database.crashLogDao().getLatestCrash()
                    }
                    crashLog = log
                    loaded = true

                    // 无崩溃日志：直接跳转，不渲染任何弹窗
                    if (log == null) {
                        navigateToMain()
                    }
                }

                // 有崩溃日志时才渲染弹窗
                if (loaded && crashLog != null) {
                    CrashReportDialog(
                        log = crashLog!!,
                        onDismiss = {
                            // 点击弹窗外部等同于"了解并清除"
                            scope.launch {
                                withContext(Dispatchers.IO) { database.crashLogDao().clearAll() }
                                navigateToMain()
                            }
                        },
                        onConfirm = {
                            scope.launch {
                                withContext(Dispatchers.IO) { database.crashLogDao().clearAll() }
                                navigateToMain()
                            }
                        }
                    )
                }
            }
        }
    }

    /** 跳转 MainActivity 并释放自身，确保不在返回栈中残留。 */
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 崩溃报告弹窗（从 MainActivity 迁移至此）
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CrashReportDialog(
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
                clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", fullLog))
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
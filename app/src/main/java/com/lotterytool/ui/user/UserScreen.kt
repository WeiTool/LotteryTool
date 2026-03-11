package com.lotterytool.ui.user

import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.lotterytool.data.room.user.UserEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(onCardClick: (Long) -> Unit, viewModel: UserViewModel) {
    // 数据委托
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val userList by viewModel.users.collectAsState(initial = emptyList())
    val userErrors by viewModel.userErrors.collectAsState()
    val qrState by viewModel.qrState.collectAsState()

    // 弹出 LoginDialog 就重置状态
    if (qrState !is QRState.Idle) {
        LoginDialog(qrState = qrState, onDismiss = { viewModel.resetQRState() })
    }

    // 使用 Box 作为根布局，以便在右下角放置浮动按钮
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 全局错误信息：显示扫码失败 (0L) 或 全局刷新异常 (-1L)
                val globalError = userErrors[0L] ?: userErrors[-1L]
                globalError?.let { msg ->
                    item {
                        ErrorUserCard(message = msg)
                    }
                }

                // 正常用户列表
                items(
                    items = userList,
                    key = { it.mid }
                ) { user ->
                    // 检查当前 mid 是否有错误信息
                    val specificError = userErrors[user.mid]

                    UserCard(
                        user = user,
                        errorMessage = specificError,
                        onClick = { onCardClick(user.mid) }
                    )
                }

                // 列表底部的添加按钮（保留原样）
                item {
                    AddUserCard(onClick = { viewModel.startLoginProcess() })
                }

                // 为悬浮按钮留出底部空间，防止遮挡列表内容
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // --- 右下角正方形圆角按钮 ---
        WebAndChuckerFab(
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}
@Composable
fun UserCard(
    user: UserEntity,
    errorMessage: String? = null,
    onClick: () -> Unit,
) {
    val isError = errorMessage != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            // 如果报错，背景切换为错误容器颜色
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 用户头像
                AsyncImage(
                    model = user.face,
                    contentDescription = "用户头像",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.uname,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "UID: ${user.mid}",
                        fontSize = 12.sp,
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 如果没报错，显示正常的状态标签
                    if (!isError) {
                        val statusColor = if (user.isLogin) Color(0xFF4CAF50) else Color(0xFFF44336)
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = statusColor,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = if (user.isLogin) "已登录" else "未登录",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                if (isError) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "错误标识",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 如果有报错，在下方展示错误详情
            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ErrorUserCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "错误",
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "操作失败",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun AddUserCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strokeWidth = 2.dp
    val dashLength = 10.dp
    val gapLength = 6.dp
    val cornerRadius = 12.dp
    val color = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = color,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(dashLength.toPx(), gapLength.toPx()),
                        phase = 0f
                    )
                ),
                cornerRadius = CornerRadius(cornerRadius.toPx())
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加用户",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加账号或重新登陆", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun LoginDialog(qrState: QRState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (qrState) {
                    is QRState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在生成二维码...", style = MaterialTheme.typography.bodyMedium)
                    }
                    is QRState.Success -> {
                        Text("请使用 哔哩哔哩 App 扫码", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "有效时间 3 分钟",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Image(
                            bitmap = qrState.bitmap.asImageBitmap(),
                            contentDescription = "二维码",
                            modifier = Modifier.size(200.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("可以截图和使用其他设备", style = MaterialTheme.typography.bodyMedium)
                    }
                    is QRState.Verifying -> {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("扫码成功，正在拉取数据...", style = MaterialTheme.typography.bodyMedium)
                    }
                    is QRState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("出错啦", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                        Text(text = qrState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("关闭")
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebAndChuckerFab(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    val url = "https://gitee.com/weitool/lottery-tool/releases"
    val view = LocalView.current

    // 使用 Surface 代替原生的 FloatingActionButton
    Surface(
        modifier = modifier
            .padding(24.dp)
            .size(56.dp), // 标准 FAB 尺寸
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 6.dp, // 设置海拔高度（阴影）
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {
                        // 短按逻辑
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = "点击打开网页，长按调试",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
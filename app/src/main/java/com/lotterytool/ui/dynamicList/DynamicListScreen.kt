package com.lotterytool.ui.dynamicList

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RunningWithErrors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lotterytool.data.room.task.TaskState

@Composable
fun DynamicListScreen(
    viewModel: DynamicListViewModel = hiltViewModel(),
    onNavigateToDetail: (Long, Int) -> Unit
) {
    val isProcessing by viewModel.isProcessing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val counts by viewModel.dynamicCounts.collectAsState()
    val erroredDynamics by viewModel.erroredDynamics.collectAsState()
    val hasExpired by viewModel.hasExpiredOfficial.collectAsState()
    val taskState by viewModel.taskState.collectAsState()
    val actionErrorByType by viewModel.actionErrorByType.collectAsState()
    val hasMissing by viewModel.hasMissingOfficial.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // 1. 顶部标题
        Text(
            text = "文章：${viewModel.articleId}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 2. 状态与进度卡片
        StatusCard(
            isProcessing = isProcessing,
            articleId = viewModel.articleId,
            errorMessage = errorMessage,
            progress = progress,
            taskState = taskState,
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text("数据统计", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // 3. 统计卡片组（新增 hasActionError 参数）
        SummaryCard(
            label = "官方动态",
            count = counts.official,
            color = Color(0xFF2196F3),
            onClick = { onNavigateToDetail(viewModel.articleId, 0) },
            hasExpired = hasExpired,
            hasError = erroredDynamics.any { it.type == 0 },
            hasActionError = actionErrorByType[0] ?: false,
            hasMissingInfo = hasMissing
        )

        SummaryCard(
            label = "普通动态",
            count = counts.normal,
            color = Color(0xFF4CAF50),
            onClick = { onNavigateToDetail(viewModel.articleId, 1) },
            hasError = erroredDynamics.any { it.type == 1 },
            hasActionError = actionErrorByType[1] ?: false
        )

        SummaryCard(
            label = "加码动态",
            count = counts.special,
            color = Color(0xFFFF9800),
            onClick = { onNavigateToDetail(viewModel.articleId, 2) },
            hasError = erroredDynamics.any { it.type == 2 },
            hasActionError = actionErrorByType[2] ?: false
        )
    }
}

@Composable
fun StatusCard(
    isProcessing: Boolean,
    articleId: Long,
    taskState: TaskState?,
    errorMessage: String?,
    progress: DynamicListViewModel.ProcessProgress
) {
    // 容器颜色：错误 > 处理中 > 闲置
    val containerColor by animateColorAsState(
        targetValue = when {
            errorMessage != null -> MaterialTheme.colorScheme.errorContainer
            isProcessing -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "cardColor"
    )

    // 指示灯颜色
    val indicatorColor = when {
        errorMessage != null -> MaterialTheme.colorScheme.error
        isProcessing -> Color(0xFF4CAF50) // 运行中为绿色
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. 顶部状态标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = indicatorColor,
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when {
                        errorMessage != null -> "处理失败"
                        taskState == TaskState.RUNNING -> "正在解析详情..."
                        taskState == TaskState.ACTION_PHASE -> "正在抽奖操作..."
                        taskState == TaskState.SUCCESS -> "任务已完成"
                        !isProcessing -> "系统空闲"
                        else -> "准备中..."
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // 2. 进度条区域
            if (isProcessing && progress.total > 0) {
                Column(modifier = Modifier.padding(top = 12.dp, start = 22.dp)) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress.current.toFloat() / progress.total.toFloat(),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "smoothProgress"
                    )

                    val animatedCurrent by animateIntAsState(
                        targetValue = progress.current,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "smoothCurrent"
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 只保留进度数值显示
                        Text(
                            text = "进度: $animatedCurrent / ${progress.total}",
                            style = MaterialTheme.typography.labelSmall
                        )

                        // 移除错误计数统计，改为显示百分比
                        Text(
                            text = "${(progress.current * 100 / progress.total)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isProcessing) {
                // 如果正在处理但总数尚未获取，显示不确定进度条
                LinearProgressIndicator(
                    modifier = Modifier
                        .padding(top = 16.dp, start = 22.dp)
                        .fillMaxWidth()
                        .height(4.dp),
                    strokeCap = StrokeCap.Round
                )
            }

            // 3. 错误信息与 ID
            if (errorMessage != null) {
                Text(
                    text = "错误: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, start = 22.dp)
                )
            }

            Text(
                text = "文章 ID: $articleId",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, start = 22.dp)
            )
        }
    }
}


@Composable
fun SummaryCard(
    label: String,
    count: Int?,
    color: Color,
    onClick: () -> Unit,
    hasExpired: Boolean = false,
    hasError: Boolean = false,
    hasActionError: Boolean = false,
    hasMissingInfo: Boolean = false
) {
    val animatedCount by animateIntAsState(
        targetValue = count ?: 0,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "summaryCount"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (hasError) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "存在解析错误的动态",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                    )
                }
                if (hasExpired) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "存在已过期的抽奖",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                    )
                }
                if (hasActionError) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "存在操作执行失败的动态",
                        tint = Color(0xFFE91E63),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                    )
                }
                if (hasMissingInfo) {
                    Icon(
                        imageVector = Icons.Filled.RunningWithErrors,
                        contentDescription = "信息缺失",
                        tint = Color(0xFF7E57C2),
                        modifier = Modifier.padding(start = 8.dp).size(20.dp)
                    )
                }
            }
            if (count == null) {
                Box(
                    modifier = Modifier
                        .size(48.dp, 24.dp)
                        .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                )
            } else {
                Text("${animatedCount}个", style = MaterialTheme.typography.titleLarge, color = color)
            }
        }
    }
}
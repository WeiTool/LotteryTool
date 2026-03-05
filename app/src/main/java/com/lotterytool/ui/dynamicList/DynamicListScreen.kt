package com.lotterytool.ui.dynamicList

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    statusViewModel: StatusViewModel = hiltViewModel(),
    iconViewModel: IconViewModel = hiltViewModel(),
    onNavigateToDetail: (Long, Int) -> Unit
) {
    val isProcessing by statusViewModel.isProcessing.collectAsState()
    val errorMessage by statusViewModel.errorMessage.collectAsState()
    val progress by statusViewModel.progress.collectAsState()
    val taskState by statusViewModel.taskState.collectAsState()
    val iconState by iconViewModel.iconState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // 1. 顶部标题
        Text(
            text = "文章：${statusViewModel.articleId}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 2. 状态与进度卡片
        StatusCard(
            isProcessing = isProcessing,
            articleId = statusViewModel.articleId,
            errorMessage = errorMessage,
            progress = progress,
            taskState = taskState,
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text("数据统计", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // 3. 统计卡片组
        SummaryCard(
            label = "官方动态",
            count = iconState.countType0,
            color = Color(0xFF2196F3),
            onClick = { onNavigateToDetail(statusViewModel.articleId, 0) },
            hasExpired = iconState.hasExpired,
            hasError = iconState.hasParseErrorType0,
            hasActionError = iconState.hasActionErrorType0,
            hasMissingInfo = iconState.hasMissingOfficial
        )

        SummaryCard(
            label = "普通动态",
            count = iconState.countType1,
            color = Color(0xFF4CAF50),
            onClick = { onNavigateToDetail(statusViewModel.articleId, 1) },
            hasError = iconState.hasParseErrorType1,
            hasActionError = iconState.hasActionErrorType1
        )

        SummaryCard(
            label = "加码动态",
            count = iconState.countType2,
            color = Color(0xFFFF9800),
            onClick = { onNavigateToDetail(statusViewModel.articleId, 2) },
            hasError = iconState.hasParseErrorType2,
            hasActionError = iconState.hasActionErrorType2
        )
    }
}

@Composable
fun StatusCard(
    isProcessing: Boolean,
    articleId: Long,
    taskState: TaskState?,
    errorMessage: String?,
    progress: StatusViewModel.ProcessProgress
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            errorMessage != null -> MaterialTheme.colorScheme.errorContainer
            isProcessing -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "cardColor"
    )

    val indicatorColor = when {
        errorMessage != null -> MaterialTheme.colorScheme.error
        isProcessing -> Color(0xFF4CAF50)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 顶部状态标题
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

            // 进度条区域
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
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "进度: $animatedCurrent / ${progress.total}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "${(progress.current * 100 / progress.total)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .padding(top = 16.dp, start = 22.dp)
                        .fillMaxWidth()
                        .height(4.dp),
                    strokeCap = StrokeCap.Round
                )
            }

            // 错误信息与 ID
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
    count: Int,
    color: Color,
    onClick: () -> Unit,
    hasExpired: Boolean = false,
    hasError: Boolean = false,
    hasActionError: Boolean = false,
    hasMissingInfo: Boolean = false
) {
    val animatedCount by animateIntAsState(
        targetValue = count,
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
                        modifier = Modifier.padding(start = 8.dp).size(20.dp)
                    )
                }
                if (hasExpired) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "存在已过期的抽奖",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.padding(start = 8.dp).size(20.dp)
                    )
                }
                if (hasActionError) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "存在操作执行失败的动态",
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.padding(start = 8.dp).size(20.dp)
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
            Text("${animatedCount}个", style = MaterialTheme.typography.titleLarge, color = color)
        }
    }
}
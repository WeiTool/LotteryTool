package com.lotterytool.ui.article

import android.widget.Toast
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RunningWithErrors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lotterytool.data.room.article.ArticleEntity
import com.lotterytool.utils.formatPublishTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ArticleScreen(
    viewModel: ArticleViewModel = hiltViewModel(),
    buttonViewModel: ButtonViewModel = hiltViewModel(),
    iconViewModel: IconViewModel = hiltViewModel(),
    onCardClick: (Long) -> Unit
) {
    // 从业务 ViewModel 获取数据和核心状态
    val articles by viewModel.articles.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()
    val userMid = viewModel.userMid

    // 从按钮状态 ViewModel 获取按钮相关状态
    val isAutoProcessing by buttonViewModel.isAutoProcessing.collectAsState()
    val isProcessing by buttonViewModel.isProcessing.collectAsState()
    val workStates by buttonViewModel.articleWorkStates.collectAsState()

    // 从图标状态 ViewModel 获取图标相关状态
    val errorStates by iconViewModel.articleErrorStates.collectAsState()
    val emptyCountStates by iconViewModel.articleEmptyCountStates.collectAsState()
    val expiredStates by iconViewModel.articleExpiredStates.collectAsState()
    val actionErrorStates by iconViewModel.articleActionErrorStates.collectAsState()
    val officialMissingStates by iconViewModel.articleOfficialMissingStates.collectAsState()

    // 在 UI 层组合出全局繁忙状态
    val isBusy = isRefreshing || isProcessing

    // 状态控制：当前正在尝试删除的文章 ID（null 代表对话框关闭）
    var deleteTargetId by remember { mutableStateOf<Long?>(null) }
    var deleteTargetRunning by remember { mutableStateOf(false) }

    var pageSizeInput by remember { mutableStateOf("1") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    DeleteConfirmDialog(
        show = deleteTargetId != null,
        isDeleting = isDeleting,
        errorMessage = deleteError, // 传递错误信息
        onDismiss = {
            if (!isDeleting) {
                deleteTargetId = null
                viewModel.clearDeleteError() // 关闭时重置错误状态
            }
        },
        onConfirm = {
            deleteTargetId?.let { id ->
                viewModel.deleteArticleFull(
                    articleId = id,
                    isRunning = deleteTargetRunning,
                    isGlobalBusy = isBusy,
                    onComplete = { deleteTargetId = null }
                )
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // 1. 搜索工具栏
        ArticleSearchBar(
            value = pageSizeInput,
            onValueChange = { input ->
                if (input.isEmpty() || input.all { it.isDigit() }) {
                    pageSizeInput = input
                } else {
                    Toast.makeText(context, "只能输入数字", Toast.LENGTH_SHORT).show()
                }
            },
            onSearchClick = { viewModel.loadArticles(pageSizeInput, userMid) },
            isRefreshing = isRefreshing
        )

        // 2. 自动处理头部 (按钮 + 图标解释)
        if (articles.isNotEmpty()) {
            AutoProcessHeader(
                articleCount = articles.size,
                isAutoProcessing = isAutoProcessing,
                isBusy = isBusy, // 传递组合后的状态
                onStartClick = { viewModel.startAutoProcessAll(isBusy) } // 传递组合后的状态
            )
        }

        // 3. 列表内容区域
        when {
            isRefreshing && articles.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            errorMsg != null && articles.isEmpty() -> {
                Text("获取失败: $errorMsg", modifier = Modifier.padding(top = 16.dp))
            }

            articles.isEmpty() -> {
                Text("暂无数据", modifier = Modifier.padding(top = 16.dp))
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        count = articles.size,
                        key = { index -> articles[index].articleId }
                    ) { index ->
                        val article = articles[index]
                        val isRunning = workStates[article.articleId] ?: false
                        val hasError = errorStates[article.articleId] ?: false
                        val hasEmptyCount = emptyCountStates[article.articleId] ?: false
                        val hasExpired = expiredStates[article.articleId] ?: false
                        val hasActionError = actionErrorStates[article.articleId] ?: false
                        val hasOfficialMissing = officialMissingStates[article.articleId] ?: false

                        ArticleItem(
                            article = article,
                            onClick = { id -> onCardClick(id) },
                            onExtractClick = {
                                viewModel.startExtractionTask(
                                    article.articleId,
                                    userMid,
                                    isBusy
                                )
                            },
                            onDeleteClick = {
                                deleteTargetId = article.articleId
                                deleteTargetRunning = isRunning
                            },
                            isGlobalBusy = isBusy,
                            isRunning = isRunning,
                            hasError = hasError,
                            hasEmptyCount = hasEmptyCount,
                            hasExpired = hasExpired,
                            hasActionError = hasActionError,
                            hasOfficialMissing = hasOfficialMissing
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text("获取数量") },
            placeholder = { Text("请输入数字") },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "清除")
                    }
                }
            },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = {
                if (value.isNotEmpty()) {
                    onSearchClick()
                    keyboardController?.hide()
                }
            }),
            singleLine = true
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onSearchClick,
            enabled = !isRefreshing && value.isNotEmpty(),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("获取专栏")
            }
        }
    }
}

@Composable
fun AutoProcessHeader(
    articleCount: Int,
    isAutoProcessing: Boolean,
    isBusy: Boolean,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 左侧按钮
        Button(
            onClick = onStartClick,
            modifier = Modifier.weight(0.4f),
            enabled = !isBusy,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) {
            if (isAutoProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "处理全部($articleCount)",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    maxLines = 1
                )
            }
        }

        // 右侧区域：Column 布局
        Column(
            modifier = Modifier.weight(0.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 第一行：两个图标（每项占 50%，文字不会被截断）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusLegendItem(
                    Icons.Filled.Error,
                    MaterialTheme.colorScheme.error,
                    "有出错",
                    Modifier.weight(1f)
                )
                StatusLegendItem(
                    Icons.Filled.Info,
                    Color(0xFF2196F3),
                    "有空项",
                    Modifier.weight(1f)
                )
            }
            // 第二行：另外两个图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusLegendItem(
                    Icons.Filled.Warning,
                    Color(0xFFFFC107),
                    "有开奖",
                    Modifier.weight(1f)
                )
                StatusLegendItem(
                    Icons.Filled.CloudOff,
                    Color(0xFFE91E63),
                    "抽奖动作问题",
                    Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusLegendItem(
                    Icons.Filled.RunningWithErrors,
                    Color(0xFF7E57C2),
                    "官方信息缺失",
                    Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.weight(1f))
            }

            // 提示文字
            Text(
                text = "滑动卡片即可出现删除按钮",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth() // 1. 让 Text 占满宽度，方便内部文字对齐
                    .align(Alignment.Start) // 2. 强制该子组件在 Column 中靠左对齐
                    .padding(start = 2.dp),
                textAlign = TextAlign.Start, // 3. 确保文字内容在 Text 容器内也是靠左的
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatusLegendItem(
    icon: ImageVector,
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

enum class DragValue {
    Closed, Open
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleItem(
    article: ArticleEntity,
    onClick: (Long) -> Unit,
    onExtractClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isGlobalBusy: Boolean,
    isRunning: Boolean,
    hasError: Boolean,
    hasEmptyCount: Boolean,
    hasExpired: Boolean,
    hasActionError: Boolean,
    hasOfficialMissing: Boolean
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val deleteButtonWidth = 80.dp
    val deleteButtonWidthPx = with(density) { deleteButtonWidth.toPx() }

    val state = remember {
        AnchoredDraggableState(
            initialValue = DragValue.Closed
        )
    }

    LaunchedEffect(deleteButtonWidthPx) {
        state.updateAnchors(
            DraggableAnchors {
                DragValue.Closed at 0f
                DragValue.Open at -deleteButtonWidthPx
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(IntrinsicSize.Min)
    ) {
        // --- 底层：侧滑露出的红色删除按钮 ---
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(deleteButtonWidth)
                .fillMaxHeight()
                .padding(start = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {
                    onDeleteClick()
                    scope.launch { state.animateTo(DragValue.Closed) }
                },
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .fillMaxSize()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // --- 上层：卡片内容层 ---
        Card(
            onClick = {
                if (state.targetValue == DragValue.Closed) onClick(article.articleId)
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    val offset = if (state.anchors.size > 0) {
                        state.requireOffset()
                    } else {
                        0f
                    }
                    IntOffset(x = offset.roundToInt(), y = 0)
                }
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                        state = state,
                        positionalThreshold = { distance: Float -> distance * 0.5f },
                        animationSpec = spring(),
                    )
                )
        ) {
            // 使用 Box 容器以便在左下角放置图标
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // 主内容区
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：文章信息区
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 16.dp) // 为底部图标留出空间
                    ) {
                        Text(
                            text = "专栏 ID: ${article.articleId}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatPublishTime(article.publishTime),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // 右侧：处理按钮
                    Button(
                        onClick = onExtractClick,
                        enabled = !isGlobalBusy && !isRunning,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .width(90.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("处理", fontSize = 12.sp)
                        }
                    }
                }

                // 左下角：状态图标组 (横向排列)
                Row(
                    modifier = Modifier.align(Alignment.BottomStart),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasError) StatusIcon(Icons.Filled.Error, MaterialTheme.colorScheme.error)
                    if (hasEmptyCount) StatusIcon(Icons.Filled.Info, Color(0xFF2196F3))
                    if (hasExpired) StatusIcon(Icons.Filled.Warning, Color(0xFFFFC107))
                    if (hasActionError) StatusIcon(Icons.Filled.CloudOff, Color(0xFFE91E63))
                    if (hasOfficialMissing) StatusIcon(
                        Icons.Filled.RunningWithErrors,
                        Color(0xFF7E57C2)
                    )
                }
            }
        }
    }
}

/**
 * 提取图标组件，保持代码整洁
 */
@Composable
private fun StatusIcon(imageVector: ImageVector, color: Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(14.dp)
    )
}

@Composable
fun DeleteConfirmDialog(
    show: Boolean,
    isDeleting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) onDismiss() },
            title = { Text("确认删除") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("此操作将同步删除 Bilibili 服务器上的动态及本地所有数据，确定继续吗？")

                    // 如果有错误信息，显示红色警告文本
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (isDeleting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = onConfirm
                ) {
                    // 如果出错了，按钮文字可以改为“重试”
                    Text(if (errorMessage != null) "重试" else "确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = onDismiss
                ) {
                    Text("取消")
                }
            }
        )
    }
}
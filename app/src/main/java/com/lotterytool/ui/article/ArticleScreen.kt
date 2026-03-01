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
    val syncDialogError by viewModel.syncDialogError.collectAsState()
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
    val processedStates by iconViewModel.articleProcessedStates.collectAsState()

    // 在 UI 层组合出全局繁忙状态
    val isBusy = isRefreshing || isProcessing

    // 删除确认 Dialog 状态
    var deleteTargetId by remember { mutableStateOf<Long?>(null) }
    var deleteTargetRunning by remember { mutableStateOf(false) }

    // 同步数据：操作前询问确认 Dialog 状态
    var showSyncConfirmDialog by remember { mutableStateOf(false) }

    // 同步数据：执行中 / 结果 Dialog 状态
    var showSyncDialog by remember { mutableStateOf(false) }

    var pageSizeInput by remember { mutableStateOf("1") }
    val context = LocalContext.current

    // 收集 Toast 消息（deleteArticleFull 成功/失败 + loadServiceId 繁忙提示均走此通道）
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 同步成功时自动关闭执行 Dialog：isRefreshing 归 false 且无错误 → 认为成功
    LaunchedEffect(isRefreshing) {
        if (showSyncDialog && !isRefreshing && syncDialogError == null) {
            showSyncDialog = false
        }
    }

    // ── 统一 Dialog 管理器 ──
    ArticleDialogManager(
        // 删除 Dialog
        showDeleteDialog = deleteTargetId != null,
        isDeleting = isDeleting,
        onDeleteDismiss = {
            if (!isDeleting) deleteTargetId = null
        },
        onDeleteConfirm = {
            deleteTargetId?.let { id ->
                viewModel.deleteArticleFull(
                    articleId = id,
                    isRunning = deleteTargetRunning,
                    isGlobalBusy = isBusy,
                    onComplete = { deleteTargetId = null }
                )
            }
        },
        // 同步确认 Dialog（操作前询问）
        showSyncConfirmDialog = showSyncConfirmDialog,
        onSyncConfirmed = {
            showSyncConfirmDialog = false
            showSyncDialog = true
            viewModel.loadServiceId(
                isRunning = isProcessing,
                isGlobalBusy = isBusy
            )
        },
        onSyncConfirmDismiss = {
            showSyncConfirmDialog = false
        },
        // 同步执行中 / 失败 Dialog
        showSyncDialog = showSyncDialog,
        isSyncing = isRefreshing && showSyncDialog,
        syncDialogError = syncDialogError,
        onSyncRetry = {
            viewModel.loadServiceId(
                isRunning = isProcessing,
                isGlobalBusy = isBusy
            )
        },
        onSyncDismiss = {
            showSyncDialog = false
            viewModel.clearSyncDialogError()
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
                isBusy = isBusy,
                isSyncing = isRefreshing && showSyncDialog,
                onStartClick = { viewModel.startAutoProcessAll(isBusy) },
                // 点击「同步数据」先弹确认框，而不是直接执行
                onSyncClick = { showSyncConfirmDialog = true }
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
                        val isProcessed = processedStates[article.articleId] ?: false

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
                            hasOfficialMissing = hasOfficialMissing,
                            isProcessed = isProcessed
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 统一 Dialog 管理器
// 承载页面内所有 AlertDialog 的展示逻辑，保持 ArticleScreen 主体整洁。
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 统一管理页面内所有 AlertDialog 的 Composable。
 *
 * @param showDeleteDialog       是否显示删除确认 Dialog
 * @param isDeleting             删除是否正在进行
 * @param onDeleteDismiss        删除 Dialog 取消回调
 * @param onDeleteConfirm        删除 Dialog 确认回调
 *
 * @param showSyncConfirmDialog  是否显示同步操作前的功能说明 / 询问 Dialog
 * @param onSyncConfirmed        用户在询问 Dialog 中点击「确认同步」的回调
 * @param onSyncConfirmDismiss   用户在询问 Dialog 中点击「取消」的回调
 *
 * @param showSyncDialog         是否显示同步执行中 / 失败 Dialog
 * @param isSyncing              同步是否正在进行（用于显示进度环）
 * @param syncDialogError        同步失败的错误信息（null 代表无错误）
 * @param onSyncRetry            同步失败后点击「重试」的回调
 * @param onSyncDismiss          同步执行 Dialog 取消/关闭回调
 */
@Composable
fun ArticleDialogManager(
    // 删除 Dialog
    showDeleteDialog: Boolean,
    isDeleting: Boolean,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    // 同步确认 Dialog（操作前询问）
    showSyncConfirmDialog: Boolean,
    onSyncConfirmed: () -> Unit,
    onSyncConfirmDismiss: () -> Unit,
    // 同步执行中 / 失败 Dialog
    showSyncDialog: Boolean,
    isSyncing: Boolean,
    syncDialogError: String?,
    onSyncRetry: () -> Unit,
    onSyncDismiss: () -> Unit
) {
    // 删除确认 Dialog
    DeleteConfirmDialog(
        show = showDeleteDialog,
        isDeleting = isDeleting,
        onDismiss = onDeleteDismiss,
        onConfirm = onDeleteConfirm
    )

    // 同步操作前：功能说明 + 询问 Dialog
    SyncConfirmDialog(
        show = showSyncConfirmDialog,
        onConfirm = onSyncConfirmed,
        onDismiss = onSyncConfirmDismiss
    )

    // 同步执行中 / 失败 Dialog
    SyncDataDialog(
        show = showSyncDialog,
        isSyncing = isSyncing,
        errorMessage = syncDialogError,
        onRetry = onSyncRetry,
        onDismiss = onSyncDismiss
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 具体 Dialog 组件
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 删除确认 Dialog。
 * 执行结果（成功 / 失败）均通过 Toast 反馈，此 Dialog 不展示任何错误文本。
 */
@Composable
fun DeleteConfirmDialog(
    show: Boolean,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("确认删除") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("此操作将同步删除 Bilibili 服务器上的动态及本地所有数据，确定继续吗？")
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
                Text("确定", color = MaterialTheme.colorScheme.error)
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

/**
 * 同步数据操作前的功能说明 / 询问 Dialog。
 *
 * 向用户介绍「同步数据」按钮的作用，并由用户决定是否继续执行，
 * 避免误触发耗时的网络请求。
 */
@Composable
fun SyncConfirmDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同步数据") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "该操作将执行以下步骤，请确认后继续：",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                SyncInfoRow(
                    content = "该操作核心是：获取用户全量动态 ID，与转发的抽奖动态 ID 做对照，确保删除操作能同步删除远端数据；"
                )
                SyncInfoRow(
                    content = "因动态数量多会导致耗时较长，仅在有的 ID 未同步时执行，日常处理动态时会自动完成该 ID 同步逻辑。"
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "确认后将立即开始同步，是否继续？",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认同步")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 同步确认 Dialog 内的单行说明条目。
 */
@Composable
private fun SyncInfoRow(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 同步数据执行中 / 失败 Dialog。
 * - 正在同步：显示进度环，按钮禁用。
 * - 同步失败：显示红色错误信息 + 「重试」「取消」按钮。
 * - 同步成功：由外层 LaunchedEffect 检测到后自动将 show 设为 false，Dialog 自动关闭。
 */
@Composable
fun SyncDataDialog(
    show: Boolean,
    isSyncing: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = { if (!isSyncing) onDismiss() },
        title = { Text("同步数据") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    isSyncing -> {
                        Text("正在同步动态服务 ID，请稍候…")
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }

                    errorMessage != null -> {
                        Text("同步失败，请检查后重试。")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> {
                        Text("准备同步动态服务 ID…")
                    }
                }
            }
        },
        confirmButton = {
            if (errorMessage != null) {
                TextButton(
                    enabled = !isSyncing,
                    onClick = onRetry
                ) {
                    Text("重试")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSyncing,
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 搜索栏
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// 自动处理头部（含「处理全部」和「同步数据」双按钮）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param isSyncing    同步数据是否正在进行（用于「同步数据」按钮显示进度环）
 * @param onSyncClick  点击「同步数据」时的回调（弹出确认 Dialog，不直接执行）
 */
@Composable
fun AutoProcessHeader(
    articleCount: Int,
    isAutoProcessing: Boolean,
    isBusy: Boolean,
    isSyncing: Boolean,
    onStartClick: () -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 左侧：纵向排列的两个按钮 ──
        Column(
            modifier = Modifier.weight(0.4f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 按钮 1：处理全部
            Button(
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth(),
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

            // 按钮 2：同步数据
            // 禁用条件：isBusy（Worker 运行中或 isRefreshing）时置灰。
            // 若同步自身正在进行（isSyncing）同样禁止重复触发。
            Button(
                onClick = onSyncClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "同步数据",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                        maxLines = 1
                    )
                }
            }
        }

        // ── 右侧：图标说明区域（与原始实现一致）──
        Column(
            modifier = Modifier.weight(0.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 第一行
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
            // 第二行
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
            // 第三行
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
                    .fillMaxWidth()
                    .align(Alignment.Start)
                    .padding(start = 2.dp),
                textAlign = TextAlign.Start,
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

// ─────────────────────────────────────────────────────────────────────────────
// 拖拽删除相关
// ─────────────────────────────────────────────────────────────────────────────

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
    hasOfficialMissing: Boolean,
    isProcessed: Boolean
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
                            .padding(bottom = 16.dp)
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

                    // 右侧：处理按钮 + 已执行标注
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                        if (isProcessed) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "✓ 已执行",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFF4CAF50)
                            )
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
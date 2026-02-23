package com.lotterytool.ui.dynamicInfo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RunningWithErrors
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDetail
import com.lotterytool.data.room.officialInfo.OfficialInfoEntity
import com.lotterytool.utils.formatPublishTime

@Composable
fun DynamicInfoScreen(
    viewModel: DynamicInfoViewModel = hiltViewModel(),
    problemsViewModel: DynamicProblemsViewModel = hiltViewModel()
) {
    // 动态列表 & 官方详情
    val items by viewModel.dynamicList.collectAsStateWithLifecycle()
    val officialDetail by viewModel.officialDetail.collectAsStateWithLifecycle()

    // 由于 DynamicProblemsViewModel 已按 type 隔离，各列表仅包含当前 type 的动态。
    // 官方信息缺失 / 已过开奖时间在 type != 0 时自然为空，不需要 Screen 层额外判断。
    val parseErrors by problemsViewModel.parseErrors.collectAsStateWithLifecycle()
    val missingOfficialItems by problemsViewModel.missingOfficialInfoItems.collectAsStateWithLifecycle()
    val actionErrorItems by problemsViewModel.actionErrorItems.collectAsStateWithLifecycle()
    val expiredItems by problemsViewModel.expiredItems.collectAsStateWithLifecycle()
    val problemDynamicIds by problemsViewModel.problemDynamicIds.collectAsStateWithLifecycle()

    // 主列表仅显示不在任何问题分组中的动态（问题动态已归入对应的展开卡片）
    val filteredItems = remember(items, problemDynamicIds) {
        items.filter { it.dynamicId !in problemDynamicIds }
    }

    Box {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {

            val hasAnyProblems = parseErrors.isNotEmpty()
                    || missingOfficialItems.isNotEmpty()
                    || actionErrorItems.isNotEmpty()
                    || expiredItems.isNotEmpty()

            if (hasAnyProblems) {
                item(key = "problem_panels") {
                    ProblemsPanel(
                        parseErrors = parseErrors,
                        missingOfficialItems = missingOfficialItems,
                        actionErrorItems = actionErrorItems,
                        expiredItems = expiredItems,
                        onRetryExtraction = { viewModel.retryExtraction(it) },
                        onRetryOfficial = { viewModel.showOfficialDetail(it.dynamicId) },
                        onRetryAction = { viewModel.retryAction(it) },
                        onDelete = { viewModel.deleteDynamic(it) }
                    )
                }
            }

            // ── 正常动态列表（已过滤掉所有问题动态，仅显示无任何异常的条目）────
            items(filteredItems, key = { it.dynamicId }) { info ->
                DynamicInfoItem(
                    info = info,
                    onRetry = { viewModel.retryExtraction(info) },
                    onShowOfficial = { viewModel.showOfficialDetail(info.dynamicId) },
                    onDeleteDynamic = { viewModel.deleteDynamic(info) },
                    onRetryAction = { viewModel.retryAction(info) }
                )
            }
        }

        // 官方抽奖详情对话框
        if (viewModel.selectedOfficialId != null) {
            OfficialDetailDialog(
                detail = officialDetail,
                onDismiss = { viewModel.dismissDialog() },
                onRetry = { viewModel.retryOfficial(it) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Problems Panel — 四个可展开问题汇总卡片
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 问题汇总面板，包含四类可展开的问题分组。
 *
 * 由于 [DynamicProblemsViewModel] 已按导航参数 type 隔离数据源，
 * 调用方无需在此处做额外的 type 过滤：
 * - type != 0 时，[missingOfficialItems] 和 [expiredItems] 自然为空，对应卡片不显示。
 * - 每类卡片只展示属于当前页面 type 的动态条目。
 */
@Composable
private fun ProblemsPanel(
    parseErrors: List<DynamicInfoDetail>,
    missingOfficialItems: List<DynamicInfoDetail>,
    actionErrorItems: List<DynamicInfoDetail>,
    expiredItems: List<DynamicInfoDetail>,
    onRetryExtraction: (DynamicInfoDetail) -> Unit,
    onRetryOfficial: (DynamicInfoDetail) -> Unit,
    onRetryAction: (DynamicInfoDetail) -> Unit,
    onDelete: (DynamicInfoDetail) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "⚠\uFE0F 问题汇总",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // ── 1. 解析错误（所有 type 均可能出现）─────────────────────────────────
        if (parseErrors.isNotEmpty()) {
            ExpandableProblemSection(
                title = "解析错误",
                count = parseErrors.size,
                headerColor = MaterialTheme.colorScheme.errorContainer,
                headerContentColor = MaterialTheme.colorScheme.onErrorContainer,
                headerIcon = Icons.Default.Error
            ) {
                parseErrors.forEach { info ->
                    ErrorDynamicCard(
                        dynamicId = info.dynamicId,
                        errorMessage = info.errorMessage ?: "未知错误",
                        onRetry = { onRetryExtraction(info) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // ── 2. 官方信息缺失（仅 type == 0 时由 VM 提供数据，否则列表为空不显示）─
        if (missingOfficialItems.isNotEmpty()) {
            ExpandableProblemSection(
                title = "官方信息缺失",
                count = missingOfficialItems.size,
                headerColor = Color(0xFFFFF3E0),
                headerContentColor = Color(0xFFE65100),
                headerIcon = Icons.Default.RunningWithErrors
            ) {
                missingOfficialItems.forEach { info ->
                    ProblemBadgeWrapper(
                        badgeIcon = Icons.Default.ErrorOutline,
                        badgeColor = Color(0xFFE65100),
                        badgeDescription = "官方信息缺失"
                    ) {
                        NormalDynamicCard(
                            info = info,
                            onClick = { onRetryOfficial(info) },
                            onDelete = { onDelete(info) },
                            onRetryAction = { onRetryAction(info) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // ── 3. 任务执行异常（所有 type 均可能出现）─────────────────────────────
        if (actionErrorItems.isNotEmpty()) {
            ExpandableProblemSection(
                title = "任务执行异常",
                count = actionErrorItems.size,
                headerColor = Color(0xFFFCE4EC),
                headerContentColor = Color(0xFFB71C1C),
                headerIcon = Icons.Default.CloudOff
            ) {
                actionErrorItems.forEach { info ->
                    ProblemBadgeWrapper(
                        badgeIcon = Icons.Default.CloudOff,
                        badgeColor = Color(0xFFB71C1C),
                        badgeDescription = "任务执行异常"
                    ) {
                        NormalDynamicCard(
                            info = info,
                            onClick = { onRetryOfficial(info) },
                            onDelete = { onDelete(info) },
                            onRetryAction = { onRetryAction(info) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // ── 4. 已过开奖时间（仅 type == 0 时由 VM 提供数据，否则列表为空不显示）─
        if (expiredItems.isNotEmpty()) {
            ExpandableProblemSection(
                title = "已过开奖时间",
                count = expiredItems.size,
                headerColor = Color(0xFFE8F5E9),
                headerContentColor = Color(0xFF2E7D32),
                headerIcon = Icons.Default.Schedule
            ) {
                expiredItems.forEach { info ->
                    ProblemBadgeWrapper(
                        badgeIcon = Icons.Default.Schedule,
                        badgeColor = Color(0xFF2E7D32),
                        badgeDescription = "已过开奖时间"
                    ) {
                        NormalDynamicCard(
                            info = info,
                            onClick = { onRetryOfficial(info) },
                            onDelete = { onDelete(info) },
                            onRetryAction = { onRetryAction(info) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExpandableProblemSection — 可展开的分类卡片
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandableProblemSection(
    title: String,
    count: Int,
    headerColor: Color,
    headerContentColor: Color,
    headerIcon: ImageVector,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val arrowAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow_rotation"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // ── 标题行（可点击）────────────────────────────────────────────────
            Surface(
                onClick = { expanded = !expanded },
                color = headerColor,
                shape = if (expanded) RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp
                ) else RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = headerContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = headerContentColor
                        )
                        // 数量徽章
                        Surface(
                            shape = CircleShape,
                            color = headerContentColor
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = headerColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = headerContentColor,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(arrowAngle)
                    )
                }
            }

            // ── 展开内容 ────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProblemBadgeWrapper — 在卡片右上角叠加一个问题标识图标
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProblemBadgeWrapper(
    badgeIcon: ImageVector,
    badgeColor: Color,
    badgeDescription: String,
    content: @Composable () -> Unit
) {
    Box {
        content()

        // 右上角悬浮徽章
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 2.dp)
                .zIndex(1f),
            shape = CircleShape,
            color = badgeColor,
            shadowElevation = 2.dp
        ) {
            Icon(
                imageVector = badgeIcon,
                contentDescription = badgeDescription,
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .padding(4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DynamicInfoItem — 逻辑分发组件
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 主列表中的动态条目。
 * 因主列表已通过 [problemDynamicIds] 过滤掉所有问题动态，
 * 正常情况下 [info.errorMessage] 为 null，[ErrorDynamicCard] 分支作为安全兜底保留。
 */
@Composable
fun DynamicInfoItem(
    info: DynamicInfoDetail,
    onRetry: () -> Unit,
    onShowOfficial: () -> Unit,
    onDeleteDynamic: () -> Unit,
    onRetryAction: () -> Unit
) {
    if (info.errorMessage != null) {
        ErrorDynamicCard(
            dynamicId = info.dynamicId,
            errorMessage = info.errorMessage,
            onRetry = onRetry
        )
    } else {
        NormalDynamicCard(
            info = info,
            onClick = onShowOfficial,
            onDelete = onDeleteDynamic,
            onRetryAction = onRetryAction
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// NormalDynamicCard
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun NormalDynamicCard(
    info: DynamicInfoDetail,
    onClick: () -> Unit = {},
    onDelete: () -> Unit,
    onRetryAction: () -> Unit
) {
    val isOfficial = info.type == 0
    val currentSeconds = System.currentTimeMillis() / 1000L
    val isExpired = isOfficial && (info.officialTime ?: 0) != 0L && (info.officialTime ?: 0) < currentSeconds

    val hasActionError = listOf(
        info.repostResult,
        info.likeResult,
        info.replyResult,
        info.followResult
    ).any { it != null && it != "成功" && it != "已经关注用户，无法重复关注" }

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth(),
        onClick = { if (isOfficial) onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── 顶部：ID + 状态图标 + 类型标签 ──────────────────────────────
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "ID: ${info.dynamicId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    if (isExpired || hasActionError) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (isExpired) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "已过期",
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(end = 4.dp)
                                )
                            }
                            if (hasActionError) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "任务执行异常",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(end = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (isOfficial) {
                    Text(
                        "官方抽奖",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ── 正文内容 ─────────────────────────────────────────────────────
            Text(
                text = if (info.type == 2) info.content else info.description,
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ── 发布日期 + 删除按钮 ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "发布日期: ${formatPublishTime(info.timestamp)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ── 任务执行状态 ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "任务执行状态",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { onRetryAction() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重试任务",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusTasks = listOf(
                    "转发" to info.repostResult,
                    "点赞" to info.likeResult,
                    "评论" to info.replyResult,
                    "关注" to info.followResult
                )
                statusTasks.forEach { (label, result) ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        StatusItem(label, result)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// StatusItem
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatusItem(label: String, result: String?) {
    val isSuccess = result == "成功" || result == "已经关注用户，无法重复关注"
    val isNotExecuted = result == null

    val statusColor = when {
        isSuccess -> Color(0xFF4CAF50)
        isNotExecuted -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.error
    }

    val icon = when {
        isSuccess -> Icons.Default.CheckCircle
        isNotExecuted -> Icons.AutoMirrored.Filled.HelpOutline
        else -> Icons.Default.Cancel
    }

    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = result ?: "待执行",
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = TextUnit.Unspecified
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ErrorDynamicCard
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ErrorDynamicCard(
    dynamicId: Long,
    errorMessage: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "解析异常 (ID: $dynamicId)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
                FilledIconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重试",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// OfficialDetailDialog
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun OfficialDetailDialog(
    detail: OfficialInfoEntity?,
    onDismiss: () -> Unit,
    onRetry: (Long) -> Unit
) {
    if (detail == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (detail.isError) "解析失败" else "官方抽奖详情") },
        text = {
            Column {
                if (detail.isError) {
                    Text(
                        text = detail.errorMessage ?: "未知错误",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    DetailRow("一等奖", detail.firstPrize, detail.firstPrizeCmt)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    DetailRow("二等奖", detail.secondPrize, detail.secondPrizeCmt)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    DetailRow("三等奖", detail.thirdPrize, detail.thirdPrizeCmt)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "开奖时间: ${formatPublishTime(detail.time)}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            if (detail.isError) {
                TextButton(onClick = { onRetry(detail.dynamicId) }) { Text("重试") }
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// DetailRow
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DetailRow(label: String, people: Int, content: String) {
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "共抽取 $people 人",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, name = "任务全部成功状态")
@Composable
fun PreviewNormalDynamicCardSuccess() {
    MaterialTheme {
        val mockInfo = DynamicInfoDetail(
            dynamicId = 123456789L,
            type = 0,
            description = "恭喜大家！这是一个官方抽奖动态的描述内容，点击可以查看奖品详情。",
            content = "",
            timestamp = System.currentTimeMillis(),
            officialTime = System.currentTimeMillis() + 86400000,
            repostResult = "成功",
            likeResult = "成功",
            replyResult = "成功",
            followResult = "成功",
            articleId = 0,
            uid = 0,
            rid = 0,
            errorMessage = null,
            officialIsError = false
        )
        NormalDynamicCard(info = mockInfo, onDelete = {}, onRetryAction = {})
    }
}

@Preview(showBackground = true, name = "任务异常与待执行状态")
@Composable
fun PreviewNormalDynamicCardWithErrors() {
    MaterialTheme {
        val mockInfo = DynamicInfoDetail(
            dynamicId = 987654321L,
            type = 2,
            description = "",
            content = "这是一个普通的动态内容，其中部分任务执行失败了，需要检查网络或登录状态。",
            timestamp = System.currentTimeMillis() - 3600000,
            officialTime = null,
            repostResult = "成功",
            likeResult = "失败: 403",
            replyResult = null,
            followResult = "风险账号",
            articleId = 0,
            uid = 0,
            rid = 0,
            errorMessage = null,
            officialIsError = false
        )
        NormalDynamicCard(info = mockInfo, onDelete = {}, onRetryAction = {})
    }
}

@Preview(showBackground = true, name = "解析错误卡片预览")
@Composable
fun PreviewErrorDynamicCard() {
    MaterialTheme {
        Column(modifier = Modifier.padding(8.dp)) {
            ErrorDynamicCard(
                dynamicId = 123456789L,
                errorMessage = "无法解析该动态内容。原因：网络请求超时 (ConnectTimeoutException)，请检查网络连接后重试。",
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "问题面板预览 - 官方抽奖页 (type=0)")
@Composable
fun PreviewProblemsPanel_Official() {
    MaterialTheme {
        val mockNormal = DynamicInfoDetail(
            dynamicId = 111L, type = 0,
            description = "官方动态，缺少开奖信息", content = "",
            timestamp = System.currentTimeMillis(),
            officialTime = null, officialIsError = null,
            repostResult = "成功", likeResult = "成功",
            replyResult = "成功", followResult = "成功",
            articleId = 1L, uid = 0L, rid = 0L, errorMessage = null
        )
        val mockAction = DynamicInfoDetail(
            dynamicId = 222L, type = 0,
            description = "官方动态，任务失败", content = "内容",
            timestamp = System.currentTimeMillis(),
            officialTime = null, officialIsError = null,
            repostResult = "失败: 403", likeResult = "成功",
            replyResult = null, followResult = "风险账号",
            articleId = 1L, uid = 0L, rid = 0L, errorMessage = null
        )
        val mockError = DynamicInfoDetail(
            dynamicId = 333L, type = 0,
            description = "", content = "",
            timestamp = System.currentTimeMillis(),
            officialTime = null, officialIsError = null,
            repostResult = null, likeResult = null,
            replyResult = null, followResult = null,
            articleId = 1L, uid = 0L, rid = 0L,
            errorMessage = "ConnectTimeoutException: 网络超时"
        )
        val mockExpired = DynamicInfoDetail(
            dynamicId = 444L, type = 0,
            description = "已过开奖时间的官方抽奖动态", content = "",
            timestamp = System.currentTimeMillis() - 86400000 * 3,
            officialTime = System.currentTimeMillis() / 1000L - 3600L,
            officialIsError = false,
            repostResult = "成功", likeResult = "成功",
            replyResult = "成功", followResult = "成功",
            articleId = 1L, uid = 0L, rid = 0L, errorMessage = null
        )
        Column(modifier = Modifier.padding(8.dp)) {
            ProblemsPanel(
                parseErrors = listOf(mockError),
                missingOfficialItems = listOf(mockNormal),
                actionErrorItems = listOf(mockAction),
                expiredItems = listOf(mockExpired),
                onRetryExtraction = {},
                onRetryOfficial = {},
                onRetryAction = {},
                onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "问题面板预览 - 普通动态页 (type=1)")
@Composable
fun PreviewProblemsPanel_Normal() {
    MaterialTheme {
        // 普通动态页只会有解析错误 / 任务执行异常，不会出现官方信息缺失 / 已过开奖时间
        val mockAction = DynamicInfoDetail(
            dynamicId = 555L, type = 1,
            description = "普通动态，任务失败", content = "内容",
            timestamp = System.currentTimeMillis(),
            officialTime = null, officialIsError = null,
            repostResult = "失败: 403", likeResult = "成功",
            replyResult = null, followResult = "风险账号",
            articleId = 1L, uid = 0L, rid = 0L, errorMessage = null
        )
        val mockError = DynamicInfoDetail(
            dynamicId = 666L, type = 1,
            description = "", content = "",
            timestamp = System.currentTimeMillis(),
            officialTime = null, officialIsError = null,
            repostResult = null, likeResult = null,
            replyResult = null, followResult = null,
            articleId = 1L, uid = 0L, rid = 0L,
            errorMessage = "ConnectTimeoutException: 网络超时"
        )
        Column(modifier = Modifier.padding(8.dp)) {
            ProblemsPanel(
                parseErrors = listOf(mockError),
                missingOfficialItems = emptyList(),  // type=1 时 VM 不提供此数据
                actionErrorItems = listOf(mockAction),
                expiredItems = emptyList(),           // type=1 时 VM 不提供此数据
                onRetryExtraction = {},
                onRetryOfficial = {},
                onRetryAction = {},
                onDelete = {}
            )
        }
    }
}
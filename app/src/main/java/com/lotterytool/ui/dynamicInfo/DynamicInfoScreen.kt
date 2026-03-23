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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RunningWithErrors
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lotterytool.data.room.officialInfo.OfficialInfoEntity
import com.lotterytool.data.room.view.DynamicInfoDetail
import com.lotterytool.utils.formatPublishTime

@Composable
fun DynamicInfoScreen(
    viewModel: DynamicInfoViewModel = hiltViewModel(),
    problemsViewModel: ProblemsViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val items by viewModel.dynamicList.collectAsStateWithLifecycle()
    val officialDetail by viewModel.officialDetail.collectAsStateWithLifecycle()
    val rawGroups by problemsViewModel.problemGroups.collectAsStateWithLifecycle()
    val searchQuery by searchViewModel.searchQuery.collectAsStateWithLifecycle()

    // 修复：搜索改为匹配 dynamicId 或 description，原来用 articleId（页面内所有条目相同，等于无效）
    fun DynamicInfoDetail.matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        return dynamicId.toString().contains(query) ||
                description.contains(query, ignoreCase = true)
    }

    // 异常分组按搜索词过滤
    val filteredGroups = remember(rawGroups, searchQuery) {
        if (searchQuery.isBlank()) {
            rawGroups
        } else {
            rawGroups.copy(
                parseErrors         = rawGroups.parseErrors.filter         { it.matchesQuery(searchQuery) },
                missingOfficialItems= rawGroups.missingOfficialItems.filter { it.matchesQuery(searchQuery) },
                actionErrorItems    = rawGroups.actionErrorItems.filter    { it.matchesQuery(searchQuery) },
                expiredItems        = rawGroups.expiredItems.filter        { it.matchesQuery(searchQuery) }
            )
        }
    }

    // 主列表：过滤掉已在异常面板中显示的条目
    val filteredItems by remember(items, rawGroups.problemDynamicIds, searchQuery) {
        derivedStateOf {
            items.filter {
                it.dynamicId !in rawGroups.problemDynamicIds && it.matchesQuery(searchQuery)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        SearchTopBar(
            query = searchQuery,
            onQueryChange = { searchViewModel.onSearchQueryChange(it) },
            onClear = { searchViewModel.clearSearch() }
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // ── 异常问题面板 ───────────────────────────────────────────────
                if (filteredGroups.hasAnyProblems) {
                    item(key = "problem_panels") {
                        ProblemsPanel(
                            parseErrors          = filteredGroups.parseErrors,
                            missingOfficialItems = filteredGroups.missingOfficialItems,
                            actionErrorItems     = filteredGroups.actionErrorItems,
                            expiredItems         = filteredGroups.expiredItems,
                            onRetryExtraction    = { viewModel.showRetryExtraction(it) },
                            onRetryOfficial      = { viewModel.showOfficialDetail(it.dynamicId) },
                            onRetryAction        = { viewModel.showRetryAction(it) },
                            onDelete             = { viewModel.showDeleteDialog(it) }
                        )
                    }
                }

                // ── 正常动态列表 ───────────────────────────────────────────────
                items(items = filteredItems, key = { it.dynamicId }) { info ->
                    DynamicInfoItem(
                        info           = info,
                        onRetry        = { viewModel.showRetryExtraction(info) },
                        onShowOfficial = { viewModel.showOfficialDetail(info.dynamicId) },
                        onDeleteDynamic= { viewModel.showDeleteDialog(info) },
                        onRetryAction  = { viewModel.showRetryAction(info) }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            if (viewModel.selectedOfficialId != null) {
                OfficialDetailDialog(
                    detail    = officialDetail,
                    onDismiss = { viewModel.dismissOfficialDialog() },
                    onRetry   = { viewModel.retryOfficial(it) }
                )
            }

            if (viewModel.pendingAction != null) {
                UnifiedActionDialog(viewModel = viewModel)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SearchTopBar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            // 修复：placeholder 改为与实际搜索字段一致
            placeholder = { Text("搜索动态 ID 或内容...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon  = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "清除")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            // 修复：改为 Text 键盘，支持搜索中文内容
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ProblemsPanel
// ═══════════════════════════════════════════════════════════════════════════════

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

        if (parseErrors.isNotEmpty()) {
            ExpandableProblemSection(
                title            = "解析错误",
                count            = parseErrors.size,
                headerColor      = MaterialTheme.colorScheme.errorContainer,
                headerContentColor = MaterialTheme.colorScheme.onErrorContainer,
                headerIcon       = Icons.Default.Error,
                defaultExpanded  = true   // 解析错误默认展开，用户最需要关注
            ) {
                parseErrors.forEachIndexed { index, info ->
                    key("${info.dynamicId}_parse_$index") {
                        ErrorDynamicCard(
                            dynamicId    = info.dynamicId,
                            errorMessage = info.errorMessage ?: "未知错误",
                            onRetry      = { onRetryExtraction(info) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        if (missingOfficialItems.isNotEmpty()) {
            ExpandableProblemSection(
                title              = "官方信息缺失",
                count              = missingOfficialItems.size,
                headerColor        = Color(0xFFFFF3E0),
                headerContentColor = Color(0xFFE65100),
                headerIcon         = Icons.Default.RunningWithErrors,
                defaultExpanded    = true
            ) {
                missingOfficialItems.forEachIndexed { index, info ->
                    key("${info.dynamicId}_${info.serviceId ?: 0}_missing_$index") {
                        NormalDynamicCard(
                            info          = info,
                            onClick       = { onRetryOfficial(info) },
                            onDelete      = { onDelete(info) },
                            onRetryAction = { onRetryAction(info) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        if (actionErrorItems.isNotEmpty()) {
            ExpandableProblemSection(
                title              = "任务执行异常",
                count              = actionErrorItems.size,
                headerColor        = Color(0xFFFCE4EC),
                headerContentColor = Color(0xFFB71C1C),
                headerIcon         = Icons.Default.CloudOff,
                defaultExpanded    = true
            ) {
                actionErrorItems.forEachIndexed { index, info ->
                    key("${info.dynamicId}_${info.serviceId ?: 0}_action_$index") {
                        NormalDynamicCard(
                            info          = info,
                            onClick       = { onRetryOfficial(info) },
                            onDelete      = { onDelete(info) },
                            onRetryAction = { onRetryAction(info) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        if (expiredItems.isNotEmpty()) {
            ExpandableProblemSection(
                title              = "已过开奖时间",
                count              = expiredItems.size,
                headerColor        = Color(0xFFE8F5E9),
                headerContentColor = Color(0xFF2E7D32),
                headerIcon         = Icons.Default.Schedule,
                defaultExpanded    = false   // 过期条目通常较多，默认折叠减少噪音
            ) {
                expiredItems.forEachIndexed { index, info ->
                    key("${info.dynamicId}_${info.serviceId ?: 0}_expired_$index") {
                        NormalDynamicCard(
                            info          = info,
                            onClick       = { onRetryOfficial(info) },
                            onDelete      = { onDelete(info) },
                            onRetryAction = { onRetryAction(info) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ExpandableProblemSection
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 修复：新增 [defaultExpanded] 参数，由调用方决定初始展开状态。
 * 解析错误/任务异常/官方缺失默认展开，过期列表默认折叠。
 */
@Composable
private fun ExpandableProblemSection(
    title: String,
    count: Int,
    headerColor: Color,
    headerContentColor: Color,
    headerIcon: ImageVector,
    defaultExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(defaultExpanded) }
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val arrowAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow_rotation"
    )

    Card(
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Surface(
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime > 400L) {
                        lastClickTime = now
                        expanded = !expanded
                    }
                },
                color = headerColor,
                shape = if (expanded) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                else RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint     = headerContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text       = title,
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = headerContentColor
                        )
                        Surface(shape = CircleShape, color = headerContentColor) {
                            Text(
                                text       = count.toString(),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = headerColor,
                                modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Icon(
                        imageVector    = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint     = headerContentColor,
                        modifier = Modifier.size(24.dp).rotate(arrowAngle)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(),
                exit    = shrinkVertically()
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

// ═══════════════════════════════════════════════════════════════════════════════
// DynamicInfoItem
// ═══════════════════════════════════════════════════════════════════════════════

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
            dynamicId    = info.dynamicId,
            errorMessage = info.errorMessage,
            onRetry      = onRetry
        )
    } else {
        NormalDynamicCard(
            info          = info,
            onClick       = onShowOfficial,
            onDelete      = onDeleteDynamic,
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

    Card(
        modifier  = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
        onClick   = { if (isOfficial) onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text  = "ID: ${info.dynamicId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    if (info.serviceId != null) {
                        Text(
                            text  = "转发 ID: ${info.serviceId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }
                if (isOfficial) {
                    Text(
                        "官方抽奖",
                        color      = MaterialTheme.colorScheme.primary,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(
                modifier  = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Text(
                text  = if (info.type == 2) info.content else info.description,
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider(
                modifier  = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "发布日期: ${formatPublishTime(info.timestamp)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { onDelete() }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector    = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier  = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "任务执行状态",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { onRetryAction() }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector    = Icons.Default.Refresh,
                        contentDescription = "重试任务",
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                listOf(
                    "转发" to info.repostResult,
                    "点赞" to info.likeResult,
                    "评论" to info.replyResult,
                    "关注" to info.followResult
                ).forEach { (label, result) ->
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
    val isSuccess     = result == "成功" || result == "已经关注用户，无法重复关注"
    val isNotExecuted = result == null

    val statusColor = when {
        isSuccess     -> Color(0xFF4CAF50)
        isNotExecuted -> MaterialTheme.colorScheme.outline
        else          -> MaterialTheme.colorScheme.error
    }
    val icon = when {
        isSuccess     -> Icons.Default.CheckCircle
        isNotExecuted -> Icons.AutoMirrored.Filled.HelpOutline
        else          -> Icons.Default.Cancel
    }

    Column(
        modifier              = Modifier.padding(vertical = 4.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Icon(imageVector = icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(28.dp))
        Text(
            text     = result ?: "待执行",
            style    = MaterialTheme.typography.bodySmall,
            color    = statusColor,
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
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "解析异常 (ID: $dynamicId)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
                FilledIconButton(
                    onClick  = onRetry,
                    modifier = Modifier.size(32.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "重试", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text  = errorMessage,
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
        text  = {
            Column {
                if (detail.isError) {
                    Text(text = detail.errorMessage ?: "未知错误", color = MaterialTheme.colorScheme.error)
                } else {
                    DetailRow("一等奖", detail.firstPrize, detail.firstPrizeCmt)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailRow("二等奖", detail.secondPrize, detail.secondPrizeCmt)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailRow("三等奖", detail.thirdPrize, detail.thirdPrizeCmt)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(text = "开奖时间: ${formatPublishTime(detail.time)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
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
    Column(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(text = "共抽取 $people 人", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Text(text = content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UnifiedActionDialog
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun UnifiedActionDialog(viewModel: DynamicInfoViewModel) {
    val pending     = viewModel.pendingAction ?: return
    val isExecuting = viewModel.isExecuting
    val dialogError = viewModel.dialogError
    val info        = pending.info

    val title: String
    val confirmLabel: String
    val confirmColor: Color

    when (pending.type) {
        ActionType.RETRY_EXTRACTION -> {
            title        = if (isExecuting) "正在重新解析…" else "重新解析"
            confirmLabel = "开始解析"
            confirmColor = MaterialTheme.colorScheme.primary
        }
        ActionType.RETRY_ACTION -> {
            title        = if (isExecuting) "正在执行…" else "重新执行任务"
            confirmLabel = "开始执行"
            confirmColor = MaterialTheme.colorScheme.primary
        }
        ActionType.DELETE -> {
            title        = if (isExecuting) "正在删除…" else "确认删除"
            confirmLabel = "确认删除"
            confirmColor = MaterialTheme.colorScheme.error
        }
    }

    AlertDialog(
        onDismissRequest = { /* 禁止点击空白处关闭 */ },
        title = { Text(text = title) },
        text  = {
            Column(
                modifier              = Modifier.fillMaxWidth(),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                if (isExecuting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (pending.type) {
                            ActionType.RETRY_EXTRACTION -> "正在重新解析，请稍候…"
                            ActionType.RETRY_ACTION     -> "正在重新处理失败的步骤，请稍候…"
                            ActionType.DELETE           -> "正在删除，请稍候…"
                        },
                        style     = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    when (pending.type) {
                        ActionType.RETRY_EXTRACTION -> RetryExtractionContent(info)
                        ActionType.RETRY_ACTION     -> RetryActionContent(info)
                        ActionType.DELETE           -> DeleteContent(info)
                    }
                    if (dialogError != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.errorContainer)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Text(text = dialogError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isExecuting) {
                TextButton(onClick = { viewModel.executeCurrentAction() }) {
                    Text(text = if (dialogError != null) "重试" else confirmLabel, color = confirmColor)
                }
            }
        },
        dismissButton = {
            if (!isExecuting) {
                TextButton(onClick = { viewModel.dismissActionDialog() }) { Text("取消") }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 对话框内容区块
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RetryExtractionContent(info: DynamicInfoDetail) {
    Text(text = "ID: ${info.dynamicId}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Text(text = info.errorMessage ?: "未知错误", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun RetryActionContent(info: DynamicInfoDetail) {
    val failedSteps = buildList {
        if (info.repostResult != null && info.repostResult != "成功") add("转发 (${info.repostResult})")
        if (info.likeResult   != null && info.likeResult   != "成功") add("点赞 (${info.likeResult})")
        if (info.replyResult  != null && info.replyResult  != "成功") add("评论 (${info.replyResult})")
        if (info.followResult != null && info.followResult != "成功" && info.followResult != "已经关注用户，无法重复关注") add("关注 (${info.followResult})")
    }

    Text(text = "ID: ${info.dynamicId}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (failedSteps.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(text = "以下失败步骤将被重新执行：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        failedSteps.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp)) {
                Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                Text(text = step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(text = "已成功的步骤将被跳过。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth())
    } else {
        Text(text = "没有检测到失败的步骤，是否仍要重新执行？", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeleteContent(info: DynamicInfoDetail) {
    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
    Text(text = "确定要删除此动态吗？", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Text(text = "ID: ${info.dynamicId}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
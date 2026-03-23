package com.lotterytool.ui.dynamicInfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotterytool.data.room.view.DynamicInfoDetail
import com.lotterytool.data.room.view.viewDao.DynamicInfoDetailDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class ProblemsViewModel @Inject constructor(
    dynamicInfoDetailDao: DynamicInfoDetailDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val articleId: Long = savedStateHandle.get<Long>("articleId") ?: -1L

    // 当前页面的 type，由导航参数传入。用于在分区时跳过不适用于本 type 的条件，确保各 Tab 的问题卡片完全隔离。
    private val type: Int = savedStateHandle.get<Int>("type") ?: 0

    // 仅包含本 type 的动态，不跨 type 污染
    private val allDynamics = dynamicInfoDetailDao.getInfoByArticleAndType(articleId, type)

    // 每 60 秒 tick 一次，驱动过期检查的实时刷新。启动时立刻发射第一个值，确保 UI 不等待。
    private val refreshTicker = flow {
        while (currentCoroutineContext().isActive) {
            // 发射当前系统时间（秒），确保类型是 Long
            emit(System.currentTimeMillis() / 1000L)
            delay(60_000L)
        }
    }


    /**
     * 将所有动态划分为 4 类问题。
     * 逻辑修改：过期检测现在支持所有类型（官方、普通、加码）。
     */
    val problemGroups: StateFlow<ProblemGroups> =
        combine(allDynamics, refreshTicker) { details, now ->
            val parseErrors = mutableListOf<DynamicInfoDetail>()
            val missingOfficialItems = mutableListOf<DynamicInfoDetail>()
            val actionErrorItems = mutableListOf<DynamicInfoDetail>()
            val expiredItems = mutableListOf<DynamicInfoDetail>()

            details.forEach { detail ->
                // 1. 解析错误 (所有类型通用)
                if (detail.errorMessage != null || (detail.type == 0 && detail.officialIsError == true)) {
                    parseErrors.add(detail)
                }

                // 2. 官方信息缺失 (仅限官方动态 type 0)
                if (detail.type == 0) {
                    val isMissing =
                        (detail.officialTime != null && detail.officialIsError == null) ||
                                (detail.officialTime == null && detail.officialIsError != null) ||
                                detail.officialIsError == true
                    if (isMissing) {
                        missingOfficialItems.add(detail)
                    }
                }

                // 3. 操作执行失败 (所有类型通用)
                if (hasActionError(detail)) {
                    actionErrorItems.add(detail)
                }

                // 1. 获取对应的时间戳，并处理空值为 0
                val expireTimeValue: Long = when (detail.type) {
                    0 -> detail.officialTime ?: 0L
                    1 -> detail.normalTime ?: 0L
                    2 -> detail.specialTime ?: 0L
                    else -> 0L
                }

                if (expireTimeValue > 0L && expireTimeValue < now) {
                    expiredItems.add(detail)
                }
            }

            ProblemGroups(
                parseErrors = parseErrors,
                missingOfficialItems = missingOfficialItems,
                actionErrorItems = actionErrorItems,
                expiredItems = expiredItems,
                problemDynamicIds = (parseErrors + missingOfficialItems + actionErrorItems + expiredItems)
                    .map { it.dynamicId }
                    .toSet()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProblemGroups()
        )

    // ─────────────────────────────────────────────────────────────────────────────

    private fun hasActionError(detail: DynamicInfoDetail): Boolean {
        return listOf(
            detail.repostResult,
            detail.likeResult,
            detail.replyResult,
            detail.followResult
        ).any { result ->
            result != null
                    && result != "成功"
                    && result != "已经关注用户，无法重复关注"
        }
    }
}

/**
 * 单次分区的结果：4 类问题列表 + 合并 ID 集合，由 ProblemsViewModel 统一维护。
 *
 * 设计原则：
 *  - [missingOfficialItems] 和 [expiredItems] 仅在 type == 0 时有意义，
 *    ViewModel 内部通过 type 字段在分区时直接跳过，不会出现在其他 type 的结果中。
 *  - [problemDynamicIds] 是四个列表的并集，供主列表过滤使用。
 *  - 同一动态可同时属于多个问题分组（例如既已过期又有操作失败）。
 */
data class ProblemGroups(
    val parseErrors: List<DynamicInfoDetail> = emptyList(),
    val missingOfficialItems: List<DynamicInfoDetail> = emptyList(),
    val actionErrorItems: List<DynamicInfoDetail> = emptyList(),
    val expiredItems: List<DynamicInfoDetail> = emptyList(),
    val problemDynamicIds: Set<Long> = emptySet()
) {
    val hasAnyProblems: Boolean
        get() = parseErrors.isNotEmpty()
                || missingOfficialItems.isNotEmpty()
                || actionErrorItems.isNotEmpty()
                || expiredItems.isNotEmpty()
}
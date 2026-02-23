package com.lotterytool.ui.dynamicInfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DynamicProblemsViewModel @Inject constructor(
    dynamicInfoDao: DynamicInfoDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val articleId: Long = savedStateHandle.get<Long>("articleId") ?: -1L

    /**
     * 当前页面的 type（由导航参数传入，与 DynamicInfoViewModel 一致）。
     * 所有问题分组均只包含属于本 type 的动态，确保不同 Tab 之间的问题卡片完全隔离。
     */
    private val type: Int = savedStateHandle.get<Int>("type") ?: 0

    /**
     * 仅加载当前 type 的动态，避免在"普通动态"页展示官方抽奖相关的问题卡，
     * 也避免在"官方抽奖"页展示普通/特殊动态的问题卡。
     */
    private val allDynamics = dynamicInfoDao.getInfoByArticleAndType(articleId, type)

    /**
     * 每 60 秒触发一次的 ticker，用于驱动"已过开奖时间"分组的实时刷新。
     * 与 IconViewModel 中的 refreshTicker 策略完全一致。
     */
    private val refreshTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    // ── 1. 解析错误 ─────────────────────────────────────────────────────────────
    /**
     * 本 type 下解析失败（errorMessage != null）的动态。
     */
    val parseErrors: StateFlow<List<DynamicInfoDetail>> = allDynamics
        .map { list -> list.filter { it.errorMessage != null } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ── 2. 官方信息缺失 ─────────────────────────────────────────────────────────
    /**
     * 仅在 type == 0（官方抽奖页）时有意义。
     * 包含 officialIsError == true（抓取失败）或 officialIsError == null（从未抓取）的动态。
     * 由于 allDynamics 已按 type 过滤，当 type != 0 时本流自然为空列表。
     */
    val missingOfficialInfoItems: StateFlow<List<DynamicInfoDetail>> = allDynamics
        .map { list ->
            list.filter { detail ->
                detail.type == 0
                        && (detail.officialIsError == true || detail.officialIsError == null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ── 3. 任务执行异常 ─────────────────────────────────────────────────────────
    /**
     * 本 type 下任意一项 action 结果为非成功且非 null（null 表示任务尚未执行，不算错误）。
     * "已经关注用户，无法重复关注"视为正常（幂等性关注），不计入异常。
     */
    val actionErrorItems: StateFlow<List<DynamicInfoDetail>> = allDynamics
        .map { list -> list.filter { hasActionError(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ── 4. 已过开奖时间 ─────────────────────────────────────────────────────────
    /**
     * 仅在 type == 0（官方抽奖页）时有意义。
     * 条件（全部满足）：
     *   - type == 0
     *   - officialIsError == false（官方信息存在且解析正常）
     *   - officialTime != null
     *   - officialTime < 当前 Unix 秒
     *
     * 通过与 [refreshTicker] combine，每 60 秒重新计算一次，确保开奖时间到达后
     * 动态能自动归入本分组，不需要用户手动刷新。
     * 由于 allDynamics 已按 type 过滤，当 type != 0 时本流自然为空列表。
     */
    val expiredItems: StateFlow<List<DynamicInfoDetail>> =
        combine(allDynamics, refreshTicker) { list, _ ->
            val nowSeconds = System.currentTimeMillis() / 1000L
            list.filter { detail ->
                detail.type == 0
                        && detail.officialIsError == false
                        && detail.officialTime != null
                        && detail.officialTime < nowSeconds
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // ── 所有"问题"动态 ID 的集合，供 Screen 过滤主列表使用 ───────────────────
    /**
     * 合并本 type 四类问题动态的 dynamicId，主列表只显示不在本集合中的动态，
     * 确保每个问题动态仅在对应的 Expandable Section 中出现一次。
     */
    val problemDynamicIds: StateFlow<Set<Long>> =
        combine(
            parseErrors,
            missingOfficialInfoItems,
            actionErrorItems,
            expiredItems
        ) { parse, missing, action, expired ->
            (parse + missing + action + expired).map { it.dynamicId }.toSet()
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptySet()
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
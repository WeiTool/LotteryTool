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

    /** 专栏下所有动态（通过数据库视图一次性拉取，后续在内存中按类别过滤） */
    private val allDynamics = dynamicInfoDao.getAllInfoByArticle(articleId)

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
    val parseErrors: StateFlow<List<DynamicInfoDetail>> = allDynamics
        .map { list -> list.filter { it.errorMessage != null } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ── 2. 官方信息缺失 ─────────────────────────────────────────────────────────
    /**
     * 仅包含 type == 0（官方抽奖）的动态，且满足以下任意一个条件：
     * - officialIsError == true：抓取过但解析失败
     * - officialIsError == null：LEFT JOIN 无结果，即从未成功抓取过
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
     * 任意一项 action 结果为非成功且非 null（null 表示任务尚未执行，不算错误）。
     * "已经关注用户，无法重复关注" 视为正常（幂等性关注），不计入异常。
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
     * 条件（全部满足）：
     *   - type == 0（官方抽奖动态）
     *   - officialIsError == false（官方信息存在且解析正常，无缺失）
     *   - officialTime != null
     *   - officialTime（单位：秒，与 DB 中 strftime('%s','now') 一致）< 当前 Unix 秒
     *
     * 通过与 [refreshTicker] combine，每 60 秒重新计算一次，确保开奖时间到达后
     * 动态能自动归入本分组，不需要用户手动刷新。
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
     * 合并四类问题动态的 dynamicId，主列表只显示不在本集合中的动态，
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
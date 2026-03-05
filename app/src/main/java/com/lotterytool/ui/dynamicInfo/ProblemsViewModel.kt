package com.lotterytool.ui.dynamicInfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.view.DynamicInfoDetail
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
    dynamicInfoDao: DynamicInfoDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val articleId: Long = savedStateHandle.get<Long>("articleId") ?: -1L

    /**
     * 当前页面的 type，由导航参数传入。
     * 用于在分区时跳过不适用于本 type 的条件，确保各 Tab 的问题卡片完全隔离。
     */
    private val type: Int = savedStateHandle.get<Int>("type") ?: 0

    /** 仅包含本 type 的动态，不跨 type 污染 */
    private val allDynamics = dynamicInfoDao.getInfoByArticleAndType(articleId, type)

    /**
     * 每 60 秒 tick 一次，驱动过期检查的实时刷新。
     * 启动时立刻发射第一个值，确保 UI 不等待。
     */
    private val refreshTicker = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(60_000L)
        }
    }

    /**
     * 所有问题分组，单次 Flow 订阅、单次列表遍历、单个 StateFlow。
     *
     * 每当 [allDynamics] 数据库推送更新，或 [refreshTicker] 每分钟到期，
     * 对整个列表做一次分区，同时计算四类问题和合并 ID 集合，替代原先 5 个独立的
     * StateFlow + 5 次独立的 `.map {}` / `combine()` 遍历。
     *
     * type 隔离规则（在遍历内部以 O(1) 分支判断，不产生额外集合分配）：
     *  - type == 0：全部四类问题均参与分区
     *  - type == 1 / 2：仅参与 parseErrors 和 actionErrorItems 分区；
     *    missingOfficialItems / expiredItems 始终为空列表
     */
    val problemGroups: StateFlow<ProblemGroups> =
        combine(allDynamics, refreshTicker) { list, _ ->
            val nowSeconds = System.currentTimeMillis() / 1000L

            val parseErrors        = mutableListOf<DynamicInfoDetail>()
            val missingOfficials   = mutableListOf<DynamicInfoDetail>()
            val actionErrors       = mutableListOf<DynamicInfoDetail>()
            val expiredItems       = mutableListOf<DynamicInfoDetail>()

            for (detail in list) {
                // ── 解析错误（所有 type） ─────────────────────────────────────
                if (detail.errorMessage != null) {
                    parseErrors.add(detail)
                }

                // ── type == 0 专属分组 ────────────────────────────────────────
                if (type == 0) {
                    // 官方信息缺失：抓取失败 或 从未抓取
                    if (detail.officialIsError == true || detail.officialIsError == null) {
                        missingOfficials.add(detail)
                    }
                    // 已过开奖时间：官方信息正常 + 时间已过
                    if (detail.officialIsError == false
                        && detail.officialTime != null
                        && detail.officialTime < nowSeconds
                    ) {
                        expiredItems.add(detail)
                    }
                }

                // ── 操作执行异常（所有 type） ─────────────────────────────────
                if (hasActionError(detail)) {
                    actionErrors.add(detail)
                }
            }

            // 合并 ID 集合：用 flatMap + toHashSet 一次性完成，避免多次 +运算产生中间集合
            val ids = (parseErrors.size + missingOfficials.size + actionErrors.size + expiredItems.size)
                .let { capacity -> HashSet<Long>(capacity * 2) }
                .also { set ->
                    parseErrors.forEach     { set.add(it.dynamicId) }
                    missingOfficials.forEach{ set.add(it.dynamicId) }
                    actionErrors.forEach    { set.add(it.dynamicId) }
                    expiredItems.forEach    { set.add(it.dynamicId) }
                }

            ProblemGroups(
                parseErrors        = parseErrors,
                missingOfficialItems = missingOfficials,
                actionErrorItems   = actionErrors,
                expiredItems       = expiredItems,
                problemDynamicIds  = ids
            )
        }
            .stateIn(
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
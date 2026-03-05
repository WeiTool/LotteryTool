package com.lotterytool.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotterytool.data.room.view.viewDao.DynamicViewDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.collections.associate

@HiltViewModel
class IconViewModel @Inject constructor(
    private val dynamicViewDao: DynamicViewDao,
) : ViewModel() {

    /**
     * 每分钟 tick 一次，同时作为过期检查的时间戳源。
     * 启动时立刻发射第一个值，确保 UI 不等待。
     */
    private val refreshTicker = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis() / 1000)
            delay(60_000)
        }
    }

    /**
     * 所有文章的图标状态，单次查询、单个 StateFlow。
     *
     * 查询由 ticker 驱动（每分钟刷新一次，满足过期检查需求），
     * 数据库变更也会通过 Room 的响应式机制自动推送更新。
     *
     * Map<articleId, ArticleIconState> 供 UI O(1) 查询。
     */
    val articleStates: StateFlow<Map<Long, ArticleIconState>> = refreshTicker
        .flatMapLatest { now ->
            dynamicViewDao.getArticleStatusRows(now)
        }
        .map { rows ->
            rows.associate { row ->
                row.articleId to ArticleIconState(
                    hasError              = row.hasError,
                    isExpired             = row.isExpired,
                    hasActionError        = row.hasActionError,
                    hasMissingTypes       = row.hasMissingTypes,
                    hasMissingOfficialTime= row.hasMissingOfficialTime,
                    isProcessed           = row.isProcessed
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
}

data class ArticleIconState(
    val hasError: Boolean = false,
    val isExpired: Boolean = false,
    val hasActionError: Boolean = false,
    val hasMissingTypes: Boolean = false,
    val hasMissingOfficialTime: Boolean = false,
    val isProcessed: Boolean = false
)
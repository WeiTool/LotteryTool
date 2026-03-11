package com.lotterytool.ui.dynamicList

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotterytool.data.room.view.viewDao.DynamicInfoDetailDao
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

@HiltViewModel
class IconViewModel @Inject constructor(
    private val dynamicViewDao: DynamicInfoDetailDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val articleId: Long = savedStateHandle.get<Long>("articleId") ?: -1L

    /**
     * 每分钟 tick 一次，同时作为过期检查的时间戳源。
     * 启动时立刻发射第一个值，确保 UI 不等待。
     */
    private val refreshTicker = flow {
        while (currentCoroutineContext().isActive) {
            // 发射当前系统时间（秒），确保类型是 Long
            emit(System.currentTimeMillis() / 1000L)
            delay(60_000L)
        }
    }

    /**
     * 当前文章所有图标状态，单次查询、单个 StateFlow。
     * ticker 驱动以满足过期检查的实时性，数据库变更由 Room 自动推送。
     */
    val iconState: StateFlow<ListIconState> = refreshTicker
        .flatMapLatest { now ->
            dynamicViewDao.getIconStatusRow(articleId, now)
        }
        .map { row ->
            row?.let {
                ListIconState(
                    countType0          = it.countType0,
                    countType1          = it.countType1,
                    countType2          = it.countType2,
                    hasParseErrorType0  = it.hasParseErrorType0,
                    hasParseErrorType1  = it.hasParseErrorType1,
                    hasParseErrorType2  = it.hasParseErrorType2,
                    hasExpiredType0     = it.hasExpiredType0,
                    hasExpiredType1     = it.hasExpiredType1,
                    hasExpiredType2     = it.hasExpiredType2,
                    hasActionErrorType0 = it.hasActionErrorType0,
                    hasActionErrorType1 = it.hasActionErrorType1,
                    hasActionErrorType2 = it.hasActionErrorType2,
                    hasMissingOfficial  = it.hasMissingOfficial
                )
            } ?: ListIconState()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ListIconState()
        )
}

/**
 * 单篇文章动态列表页所有图标状态，由 IconViewModel 统一维护。
 * 默认值全为安全初始值，查询无结果时可直接使用。
 */
data class ListIconState(
    val countType0: Int = 0,
    val countType1: Int = 0,
    val countType2: Int = 0,

    val hasParseErrorType0: Boolean = false,
    val hasParseErrorType1: Boolean = false,
    val hasParseErrorType2: Boolean = false,

    val hasExpiredType0: Boolean = false,
    val hasExpiredType1: Boolean = false,
    val hasExpiredType2: Boolean = false,

    val hasActionErrorType0: Boolean = false,
    val hasActionErrorType1: Boolean = false,
    val hasActionErrorType2: Boolean = false,

    val hasMissingOfficial: Boolean = false
)
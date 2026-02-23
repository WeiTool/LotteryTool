package com.lotterytool.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotterytool.data.repository.ArticleRepository
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.task.TaskDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class IconViewModel @Inject constructor(
    articleRepository: ArticleRepository,
    officialInfoDao: OfficialInfoDao,
    taskDao: TaskDao,
    dynamicInfoDao: DynamicInfoDao,
    private val actionDao: ActionDao
) : ViewModel() {
    val articles = articleRepository.getAllArticlesFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(15000),
            emptyList()
        )

    // 合并两种错误来源（任务失败 + 动态解析错误）
    val articleErrorStates: StateFlow<Map<Long, Boolean>> = combine(
        taskDao.getFailedTaskIds(),
        dynamicInfoDao.getErroredArticleIds()
    ) { failedTasks, erroredDynamics ->
        // 将两个列表合并，取唯一值，并快速转换为 Map
        (failedTasks + erroredDynamics).distinct().associateWith { true }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap()
    )

    // 打开APP就监听一次，每隔一分钟刷新一次
    private val refreshTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    // 检查是否有开奖动态
    val articleExpiredStates = combine(
        officialInfoDao.getExpiredArticleIds(),
        refreshTicker
    ) { expiredIds, _ ->
        expiredIds.associateWith { true }
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    // 检查是否有 Action 执行失败
    val articleActionErrorStates: StateFlow<Map<Long, Boolean>> =
        articles.flatMapLatest { articleList ->
            if (articleList.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val perArticleFlows = articleList.map { article ->
                    // 数据库查询返回的是布尔类型
                    actionDao.hasActionErrorForArticle(article.articleId)
                        .map { hasError -> article.articleId to hasError }
                }
                combine(perArticleFlows) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    val articleEmptyCountStates: StateFlow<Map<Long, Boolean>> = combine(
        taskDao.getTasksInActionPhaseIds(), // 只看已经进入执行抽奖阶段的任务
        dynamicInfoDao.getArticlesWithMissingTypes() // 数据库里缺失类型的文章
    ) { actionPhaseIds, missingTypeIds ->
        // 只有同时满足以下两个条件，才在 UI 上显示“数据缺失”警告：
        // 条件 A：任务已经走到了 ACTION_PHASE (说明爬取阶段已结束)
        // 条件 B：但是 type 0, 1, 2 中依然有数据是 0 条
        val actionSet = actionPhaseIds.toSet()
        missingTypeIds
            .filter { it in actionSet }
            .associateWith { true }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap()
    )

    val articleOfficialMissingStates: StateFlow<Map<Long, Boolean>> = combine(
        officialInfoDao.getArticlesWithMissingOfficialInfo(),
        taskDao.getTasksInActionPhaseIds()
    ) { missingIds, actionPhaseIds ->
        val actionSet = actionPhaseIds.toSet()

        // 只有那些 1.数据库判定缺失 且 2.爬取阶段已结束 的文章才显示警告
        missingIds
            .filter { it in actionSet }
            .associateWith { true }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap()
    )
}

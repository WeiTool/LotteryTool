package com.lotterytool.ui.dynamicList

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoEntity
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.task.TaskDao
import com.lotterytool.data.room.task.TaskState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DynamicListViewModel @Inject constructor(
    taskDao: TaskDao,
    workManager: WorkManager,
    dynamicInfoDao: DynamicInfoDao,
    officialInfoDao: OfficialInfoDao,
    actionDao: ActionDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val articleId: Long = savedStateHandle.get<Long>("articleId") ?: -1L

    data class ProcessProgress(
        val current: Int = 0,
        val total: Int = 0,
    )

    data class DynamicCount(
        val official: Int? = null,
        val normal: Int? = null,
        val special: Int? = null,
    )

    // 获取 Task 流
    private val taskEntity = taskDao.getTaskFlow(articleId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    // 获取 TaskState 流
    val taskState: StateFlow<TaskState?> = taskEntity.map { it?.state }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TaskState.IDLE
        )

    // 获取 WorkInfo 流
    private val workInfoFlow = workManager
        .getWorkInfosByTagFlow("extract_$articleId")
        .map { it.firstOrNull() }

    // 判断是否正在处理
    val isProcessing: StateFlow<Boolean> = combine(taskEntity, workInfoFlow)
    { entity, workInfo ->
        val isWorkRunning = workInfo?.state == WorkInfo.State.RUNNING ||
                workInfo?.state == WorkInfo.State.ENQUEUED
        entity?.state == TaskState.RUNNING ||
                entity?.state == TaskState.ACTION_PHASE ||
                isWorkRunning
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    // 获取task里面的错误信息
    val errorMessage: StateFlow<String?> = taskEntity.map { entity ->
        if (entity?.state == TaskState.FAILED) {
            entity.errorMessage ?: "解析失败，请检查网络"
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 获取进度
    val progress: StateFlow<ProcessProgress> = taskEntity.map { entity ->
        if (entity != null) {
            ProcessProgress(
                current = entity.currentProgress,
                total = entity.totalProgress,
            )
        } else {
            ProcessProgress()
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ProcessProgress()
    )

    // 获取每个动态总数
    val dynamicCounts: StateFlow<DynamicCount> = dynamicInfoDao.getDynamicCountsFlow(articleId)
        .map { result ->
            // 将数据库结果映射到 UI 使用的 DynamicCount 模型
            DynamicCount(
                official = result?.officialCount ?: 0,
                normal = result?.normalCount ?: 0,
                special = result?.specialCount ?: 0
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DynamicCount(0, 0, 0)
        )

    // 获取所有解析错误的动态
    val erroredDynamics: StateFlow<List<DynamicInfoEntity>> =
        dynamicInfoDao.getErroredDynamics(articleId)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )


    // 检查该文章是否包含已过期的官方动态
    val hasExpiredOfficial: StateFlow<Boolean> = officialInfoDao.getExpiredArticleIds()
        .map { expiredIds ->
            articleId in expiredIds
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 检查是否有抽奖动作出错
    val actionErrorByType: StateFlow<Map<Int, Boolean>> = actionDao.getActionErrorStatusFlow(articleId)
        .map { result ->
            mapOf(
                0 to (result?.hasOfficialError ?: false),
                1 to (result?.hasNormalError ?: false),
                2 to (result?.hasSpecialError ?: false)
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    // 检查该文章是否包含“信息缺失”的官方动态
    val hasMissingOfficial: StateFlow<Boolean> = officialInfoDao.hasMissingOfficialInfo(articleId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false
        )
}
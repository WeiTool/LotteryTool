package com.lotterytool.ui.dynamicList

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
class StatusViewModel @Inject constructor(
    taskDao: TaskDao,
    workManager: WorkManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val articleId: Long = savedStateHandle.get<Long>("articleId") ?: -1L

    data class ProcessProgress(
        val current: Int = 0,
        val total: Int = 0,
    )

    // Task 实体流（taskState、progress、errorMessage 共同来源）
    private val taskEntity = taskDao.getTaskFlow(articleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // WorkInfo 流（用于判断 Worker 是否还在队列或运行中）
    private val workInfoFlow = workManager
        .getWorkInfosByTagFlow("extract_$articleId")
        .map { it.firstOrNull() }

    val taskState: StateFlow<TaskState?> = taskEntity
        .map { it?.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskState.IDLE)

    val isProcessing: StateFlow<Boolean> = combine(taskEntity, workInfoFlow) { entity, workInfo ->
        val isWorkActive = workInfo?.state == WorkInfo.State.RUNNING ||
                workInfo?.state == WorkInfo.State.ENQUEUED
        entity?.state == TaskState.RUNNING ||
                entity?.state == TaskState.ACTION_PHASE ||
                entity?.state == TaskState.SYNC_PHASE ||
                isWorkActive
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val errorMessage: StateFlow<String?> = taskEntity
        .map { entity ->
            if (entity?.state == TaskState.FAILED) entity.errorMessage ?: "解析失败，请检查网络"
            else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val progress: StateFlow<ProcessProgress> = taskEntity
        .map { entity ->
            if (entity != null) ProcessProgress(entity.currentProgress, entity.totalProgress)
            else ProcessProgress()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProcessProgress())
}
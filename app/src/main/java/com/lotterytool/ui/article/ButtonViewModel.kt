package com.lotterytool.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ButtonViewModel @Inject constructor(
    workManager: WorkManager
) : ViewModel() {

    // 判断自动处理任务链是否在运行
    val isAutoProcessing = workManager
        .getWorkInfosForUniqueWorkFlow("AUTO_PROCESS_CHAIN")
        .map { infoList ->
            infoList.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(15000),
            false
        )

    // 判断手动处理任务是否在运行
    val isManualProcessing = workManager
        .getWorkInfosByTagFlow("MANUAL_EXTRACT")
        .map { infoList ->
            infoList.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(15000),
            false
        )

    // 合并自动和手动处理状态，代表任何后台Worker是否正在工作
    val isProcessing = combine(isAutoProcessing, isManualProcessing) { auto, manual ->
        auto || manual
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(15000),
        false
    )

    // 查看具体哪个专栏正在处理
    val articleWorkStates = combine(
        workManager.getWorkInfosByTagFlow("MANUAL_EXTRACT"),
        workManager.getWorkInfosForUniqueWorkFlow("AUTO_PROCESS_CHAIN")
    ) { manualWorks, autoWorks ->
        val stateMap = mutableMapOf<Long, Boolean>()
        val allWorks = manualWorks + autoWorks
        allWorks.forEach { workInfo ->
            workInfo.tags.forEach { tag ->
                if (tag.startsWith("extract_")) {
                    val articleId = tag.removePrefix("extract_").toLongOrNull()
                    if (articleId != null) {
                        val isRunning = workInfo.state == WorkInfo.State.RUNNING ||
                                workInfo.state == WorkInfo.State.ENQUEUED
                        stateMap[articleId] = stateMap[articleId] ?: false || isRunning
                    }
                }
            }
        }
        stateMap.toMap()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(15000),
        emptyMap()
    )
}

package com.lotterytool.data.room.task

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["state"])]
)
data class TaskEntity(
    @PrimaryKey val articleId: Long,
    val state: TaskState = TaskState.IDLE,
    val currentProgress: Int = 0,
    val totalProgress: Int = 0,
    val errorMessage: String? = null,
    val lastUpdateTime: Long = System.currentTimeMillis()
)

enum class TaskState {
    IDLE, RUNNING, ACTION_PHASE, SUCCESS, FAILED
}
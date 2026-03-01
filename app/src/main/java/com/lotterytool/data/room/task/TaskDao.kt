package com.lotterytool.data.room.task

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // 获取 Task 流
    @Query("SELECT * FROM tasks WHERE articleId = :articleId")
    fun getTaskFlow(articleId: Long): Flow<TaskEntity?>

    // 获取所有状态为 ACTION_PHASE 的 articleId
    @Query("SELECT articleId FROM tasks WHERE state = 'ACTION_PHASE'")
    fun getTasksInActionPhaseIds(): Flow<List<Long>>

    // 获取所有状态为 state 的 articleId
    @Query("SELECT articleId FROM tasks WHERE state = :state")
    fun getTasksByState(state: TaskState): Flow<List<Long>>

    // 更新Task
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    // 更新状态
    @Query("UPDATE tasks SET state = :state, errorMessage = :error WHERE articleId = :articleId")
    suspend fun updateState(articleId: Long, state: TaskState, error: String? = null)

    // 更新处理状态
    @Query("UPDATE tasks SET currentProgress = :current, totalProgress = :total WHERE articleId = :articleId")
    suspend fun updateProgress(articleId: Long, current: Int, total: Int)

    // 删除
    @Query("DELETE FROM tasks WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: Long)

    // 用于监听专栏任务失败的 Flow
    @Query("SELECT articleId FROM tasks WHERE state = 'FAILED'")
    fun getFailedTaskIds(): Flow<List<Long>>
}
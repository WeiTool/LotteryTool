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

}
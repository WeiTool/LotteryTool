package com.lotterytool.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lotterytool.data.repository.DynamicIdRepository
import com.lotterytool.data.repository.DynamicInfoRepository
import com.lotterytool.data.repository.UserDynamicRepository
import com.lotterytool.data.room.task.TaskDao
import com.lotterytool.data.room.task.TaskEntity
import com.lotterytool.data.room.task.TaskState
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.ui.service.TaskNotificationManager
import com.lotterytool.utils.FetchResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@HiltWorker
class ExtractDynamicWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: DynamicIdRepository,
    private val infoRepository: DynamicInfoRepository,
    private val taskDao: TaskDao,
    private val dynamicAction: DynamicAction,
    private val userDao: UserDao,
    private val notificationManager: TaskNotificationManager,
    private val userDynamicRepository: UserDynamicRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val articleId = inputData.getLong("ARTICLE_ID", -1L)
        val userMid = inputData.getLong("USER_MID", -1L)

        if (articleId == -1L) return Result.failure()

        // 开启前台通知 + 同时持有 WakeLock（在 getForegroundInfo 内部完成）
        // WakeLock 保证 CPU 在熄屏 / 按 Home 后不进入深度睡眠，协程继续运行。
        setForeground(notificationManager.getForegroundInfo(articleId))

        return try {
            // 状态初始化
            taskDao.upsertTask(TaskEntity(articleId = articleId, state = TaskState.RUNNING))

            // 校验用户信息
            if (userMid == -1L) {
                val msg = "缺少用户信息 (USER_MID)"
                taskDao.updateState(articleId, TaskState.FAILED, msg)
                notificationManager.updateError(articleId, msg) // updateError 内部会释放 WakeLock
                return Result.failure()
            }

            val user = userDao.getUserById(userMid)
            if (user == null) {
                val msg = "找不到该账号，请重新登录"
                taskDao.updateState(articleId, TaskState.FAILED, msg)
                notificationManager.updateError(articleId, msg)
                return Result.failure()
            }

            val cookie = user.SESSDATA
            val csrf = user.CSRF

            // 阶段一：提取 ID（NonCancellable 确保即使 Worker 被取消也能写完数据库）
            val extractResult = withContext(NonCancellable) {
                repository.extractDynamic(cookie, articleId)
            }

            when (extractResult) {
                is FetchResult.Success -> {
                    // 阶段二：详情解析
                    infoRepository.processAndStoreAllDynamics(
                        cookie = cookie,
                        articleId = articleId,
                        onProgress = { current, total ->
                            taskDao.updateProgress(articleId, current, total)
                            notificationManager.updateProgress(articleId, current, total)
                        }
                    )

                    // 阶段三：Action 阶段
                    taskDao.updateProgress(articleId, 0, 0)
                    taskDao.updateState(articleId, TaskState.ACTION_PHASE)

                    dynamicAction.allAction(
                        articleId = articleId,
                        cookie = cookie,
                        csrf = csrf,
                        onProgress = { current, total ->
                            taskDao.updateProgress(articleId, current, total)
                            notificationManager.updateProgress(articleId, current, total)
                        }
                    )

                    // 阶段四: 立即同步个人动态，获取刚才转发生成的 serviceId
                    userDynamicRepository.fetchUserDynamic(cookie=cookie, mid = userMid.toString())

                    // 任务成功：更新状态并释放 WakeLock
                    taskDao.updateState(articleId, TaskState.SUCCESS)
                    notificationManager.onTaskComplete() // 释放 WakeLock
                    Result.success()
                }

                is FetchResult.Error -> {
                    val errorMsg = extractResult.message
                    taskDao.updateState(articleId, TaskState.FAILED, errorMsg)
                    notificationManager.updateError(articleId, errorMsg) // 释放 WakeLock
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "运行中发生未知异常"
            taskDao.updateState(articleId, TaskState.FAILED, errorMsg)
            notificationManager.updateError(articleId, errorMsg) // 释放 WakeLock
            Result.failure()
        }
    }
}
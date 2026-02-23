package com.lotterytool.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lotterytool.data.repository.DynamicIdRepository
import com.lotterytool.data.repository.DynamicInfoRepository
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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val articleId = inputData.getLong("ARTICLE_ID", -1L)
        val userMid = inputData.getLong("USER_MID", -1L)

        // 如果连 articleId 都没有，无法关联到卡片，直接失败
        if (articleId == -1L) return Result.failure()

        // 1. 立即开启前台通知，并传入 articleId 以显示在标题
        setForeground(notificationManager.getForegroundInfo(articleId))

        return try {
            // 2. 状态初始化
            taskDao.upsertTask(TaskEntity(articleId = articleId, state = TaskState.RUNNING))

            // 3. 校验用户信息
            if (userMid == -1L) {
                val msg = "缺少用户信息 (USER_MID)"
                taskDao.updateState(articleId, TaskState.FAILED, msg)
                notificationManager.updateError(articleId, msg) // 更新通知栏错误信息
                return Result.failure()
            }

            val user = userDao.getUserById(userMid)
            if (user == null) {
                val msg = "找不到该账号，请重新登录"
                taskDao.updateState(articleId, TaskState.FAILED, msg)
                notificationManager.updateError(articleId, msg) // 更新通知栏错误信息
                return Result.failure()
            }

            val cookie = user.SESSDATA
            val csrf = user.CSRF

            // 4. 阶段一：提取 ID
            val extractResult = withContext(NonCancellable) {
                repository.extractDynamic(cookie, articleId)
            }

            when (extractResult) {
                is FetchResult.Success -> {
                    // 5. 阶段二：详情解析
                    var detailErrorCount = 0
                    infoRepository.processAndStoreAllDynamics(
                        cookie = cookie,
                        articleId = articleId,
                        onProgress = { current, total, error ->
                            if (error != null) {
                                detailErrorCount++
                                taskDao.updateDetailErrorCount(articleId, detailErrorCount)
                            }
                            // 同时更新数据库和通知栏进度
                            taskDao.updateProgress(articleId, current, total)
                            notificationManager.updateProgress(articleId, current, total)
                        }
                    )

                    // 6. 阶段三：切换到 Action 阶段
                    taskDao.updateProgress(articleId, 0, 0)
                    taskDao.updateState(articleId, TaskState.ACTION_PHASE)

                    var actionErrorCount = 0
                    dynamicAction.allAction(
                        articleId = articleId,
                        cookie = cookie,
                        csrf = csrf,
                        onProgress = { current, total, error ->
                            if (error != null) {
                                actionErrorCount++
                                taskDao.updateActionErrorCount(articleId, actionErrorCount)
                            }
                            // 同时更新数据库和通知栏进度
                            taskDao.updateProgress(articleId, current, total)
                            notificationManager.updateProgress(articleId, current, total)
                        }
                    )

                    // 7. 任务圆满成功
                    taskDao.updateState(articleId, TaskState.SUCCESS)
                    Result.success()
                }

                is FetchResult.Error -> {
                    val errorMsg = extractResult.message
                    taskDao.updateState(articleId, TaskState.FAILED, errorMsg)
                    // 提取 ID 失败，直接在通知栏显示错误
                    notificationManager.updateError(articleId, errorMsg)
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "运行中发生未知异常"
            taskDao.updateState(articleId, TaskState.FAILED, errorMsg)
            // 捕获异常并在通知栏显示
            notificationManager.updateError(articleId, errorMsg)
            Result.failure()
        }
    }
}
package com.lotterytool.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
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

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val articleId = inputData.getLong("ARTICLE_ID", -1L)
        return notificationManager.getForegroundInfo(articleId)
    }

    override suspend fun doWork(): Result {
        val articleId = inputData.getLong("ARTICLE_ID", -1L)
        val userMid = inputData.getLong("USER_MID", -1L)
        val forceRefresh = inputData.getBoolean("FORCE_REFRESH", false)

        return try {
            setForeground(getForegroundInfo())

            taskDao.upsertTask(TaskEntity(articleId = articleId, state = TaskState.RUNNING))

            if (userMid == -1L) {
                val msg = "缺少用户信息 (USER_MID)"
                taskDao.updateState(articleId, TaskState.FAILED, msg)
                notificationManager.updateError(articleId, msg)
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

            val extractResult = withContext(NonCancellable) {
                repository.extractDynamic(cookie, articleId)
            }

            when (extractResult) {
                is FetchResult.Success -> {
                    // 阶段二：详情解析
                    infoRepository.processAndStoreAllDynamics(
                        cookie = cookie,
                        articleId = articleId,
                        forceRefresh = forceRefresh,
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
                        forceRefresh = forceRefresh,
                        onProgress = { current, total ->
                            taskDao.updateProgress(articleId, current, total)
                            notificationManager.updateProgress(articleId, current, total)
                        }
                    )

                    // 阶段四：同步个人动态
                    taskDao.updateState(articleId, TaskState.SYNC_PHASE)
                    notificationManager.updateSyncing(articleId)

                    userDynamicRepository.fetchUserDynamic(cookie = cookie, mid = userMid.toString())

                    taskDao.updateState(articleId, TaskState.SUCCESS)
                    notificationManager.onTaskComplete()
                    Result.success()
                }

                is FetchResult.Error -> {
                    val errorMsg = extractResult.message
                    taskDao.updateState(articleId, TaskState.FAILED, errorMsg)
                    notificationManager.updateError(articleId, errorMsg)
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "运行中发生未知异常"
            taskDao.updateState(articleId, TaskState.FAILED, errorMsg)
            notificationManager.updateError(articleId, errorMsg)
            Result.failure()
        }
    }
}
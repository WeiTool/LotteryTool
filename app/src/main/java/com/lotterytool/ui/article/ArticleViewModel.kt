@file:OptIn(FlowPreview::class)

package com.lotterytool.ui.article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lotterytool.data.repository.ArticleRepository
import com.lotterytool.data.repository.UserDynamicRepository
import com.lotterytool.data.repository.actionRepository.RemoveRepository
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.article.ArticleDao
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.task.TaskDao
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.data.room.userDynamic.UserDynamicDao
import com.lotterytool.data.room.view.viewDao.DynamicInfoDetailDao
import com.lotterytool.data.workers.ExtractDynamicWorker
import com.lotterytool.utils.FetchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArticleViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val workManager: WorkManager,
    private val taskDao: TaskDao,
    private val dynamicInfoDao: DynamicInfoDao,
    private val officialInfoDao: OfficialInfoDao,
    private val actionDao: ActionDao,
    private val dynamicIdsDao: DynamicIdsDao,
    private val articleDao: ArticleDao,
    private val userDao: UserDao,
    private val userDynamicRepository: UserDynamicRepository,
    private val dynamicInfoDetailDao: DynamicInfoDetailDao,
    private val dynamicViewDao: DynamicInfoDetailDao,
    private val removeRepository: RemoveRepository,
    private val userDynamicDao: UserDynamicDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val userMid: Long = savedStateHandle["mid"] ?: 0L

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _syncDialogError = MutableStateFlow<String?>(null)
    val syncDialogError = _syncDialogError.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    val articles = articleRepository.getAllArticlesFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(15000),
            emptyList()
        )

    fun loadArticles(ps: String, userMid: Long) {
        val pageSize = ps.toIntOrNull() ?: 1
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                _errorMessage.value = null

                val user = userDao.getUserById(userMid)
                if (user?.SESSDATA == null) {
                    _errorMessage.value = "未找到用户Cookie（mid=$userMid），请刷新登录"
                    return@launch
                }

                val result = articleRepository.fetchArticles(
                    cookie = user.SESSDATA,
                    mid = userMid,
                    ps = pageSize
                )

                if (result is FetchResult.Error) {
                    _errorMessage.value = result.message
                }
            } catch (e: Exception) {
                _errorMessage.value = "请求发生错误: ${e.localizedMessage}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadServiceId(isRunning: Boolean, isGlobalBusy: Boolean) {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                _syncDialogError.value = null

                if (isRunning || isGlobalBusy) {
                    _toastMessage.emit("全局/单独文章正在处理中，中途删除出错")
                    return@launch
                }

                val user = userDao.getUserById(userMid)
                if (user?.SESSDATA == null) {
                    _syncDialogError.value = "未找到用户Cookie，请刷新登录"
                    return@launch
                }

                val result = userDynamicRepository.fetchUserDynamicAll(
                    cookie = user.SESSDATA,
                    mid = userMid.toString()
                )

                if (result is FetchResult.Error) {
                    _syncDialogError.value = result.message
                }

            } catch (e: Exception) {
                _syncDialogError.value = "请求发生错误: ${e.localizedMessage}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearSyncDialogError() {
        _syncDialogError.value = null
    }

    fun startAutoProcessAll(isBusy: Boolean) {
        viewModelScope.launch {
            if (isBusy) return@launch

            try {
                val allArticles = articleDao.getAllArticles()
                if (allArticles.isEmpty()) {
                    _toastMessage.emit("没有任何专栏")
                    return@launch
                }

                val processedIds = dynamicViewDao.getProcessedArticleIds().toSet()
                val pendingArticles = allArticles.filter { it.articleId !in processedIds }

                if (pendingArticles.isEmpty()) {
                    _toastMessage.emit("没有需要处理的任务")
                    return@launch
                }

                // 自动处理全量专栏，均为首次处理，forceRefresh = false
                val workRequests = pendingArticles.map { article ->
                    OneTimeWorkRequestBuilder<ExtractDynamicWorker>()
                        .setInputData(
                            Data.Builder()
                                .putLong("ARTICLE_ID", article.articleId)
                                .putLong("USER_MID", article.mid)
                                .putBoolean("FORCE_REFRESH", false)
                                .build()
                        )
                        .addTag("extract_${article.articleId}")
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                }

                val uniqueName = "AUTO_PROCESS_CHAIN"
                var chain = workManager.beginUniqueWork(
                    uniqueName,
                    ExistingWorkPolicy.REPLACE,
                    workRequests.first()
                )
                for (request in workRequests.drop(1)) {
                    chain = chain.then(request)
                }
                chain.enqueue()

                workManager.getWorkInfosForUniqueWorkFlow(uniqueName).collect { workInfos ->
                    val allFinished = workInfos.all { it.state.isFinished }

                    if (allFinished && workInfos.isNotEmpty()) {
                        // 统计 OutputData 中包含 IS_CLEANED 为 true 的数量
                        val cleanedCount = workInfos.count {
                            it.outputData.getBoolean("IS_CLEANED", false)
                        }

                        if (cleanedCount > 0) {
                            _toastMessage.emit("批量处理完成，共自动清理 $cleanedCount 个空专栏")
                        } else {
                            _toastMessage.emit("批量处理任务已完成")
                        }
                        // 统计完后可以退出 collect，避免重复触发
                        return@collect
                    }
                }

            } catch (e: Exception) {
                _toastMessage.emit("启动自动处理失败: ${e.localizedMessage}")
            }
        }
    }

    /**
     * @param forceRefresh 已处理过的专栏点"重试"时传 true，首次点"处理"时传 false。
     *                     为 true 时 Worker 会跳过"已处理"检查，强制重新抓取所有动态。
     */
    fun startExtractionTask(articleId: Long, userMid: Long, isBusy: Boolean, forceRefresh: Boolean) {
        if (isBusy) return

        val inputData = Data.Builder()
            .putLong("ARTICLE_ID", articleId)
            .putLong("USER_MID", userMid)
            .putBoolean("FORCE_REFRESH", forceRefresh)
            .build()

        val request = OneTimeWorkRequestBuilder<ExtractDynamicWorker>()
            .setInputData(inputData)
            .addTag("extract_$articleId")
            .addTag("MANUAL_EXTRACT")
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            "unique_extract_$articleId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun deleteArticleFull(
        articleId: Long,
        isRunning: Boolean,
        isGlobalBusy: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (isRunning || isGlobalBusy) {
                _toastMessage.emit("全局/单独文章正在处理中，中途删除出错")
                return@launch
            }

            val user = userDao.getUserById(userMid)
            if (user?.SESSDATA.isNullOrEmpty() || user.CSRF.isEmpty()) {
                _toastMessage.emit("用户凭证不全，请重新登录")
                return@launch
            }

            try {
                _isDeleting.value = true

                val details = dynamicInfoDetailDao.getDetailsByArticleId(articleId)

                if (details.isEmpty()) {
                    dynamicIdsDao.deleteByArticleId(articleId)
                    taskDao.deleteByArticleId(articleId)
                    articleDao.deleteByArticleId(articleId)
                    onComplete()
                    return@launch
                }

                val successfulDynamicIds = mutableListOf<Long>()
                var errorCount = 0

                for (detail in details) {
                    val serviceId = detail.serviceId

                    if (serviceId == null) {
                        errorCount++
                        kotlinx.coroutines.delay(300)
                        continue
                    }

                    val result = removeRepository.executeRemove(
                        cookie = user.SESSDATA,
                        csrf = user.CSRF,
                        dynamicId = serviceId
                    )

                    if (result is FetchResult.Success) {
                        userDynamicDao.deleteByServiceId(serviceId)
                        successfulDynamicIds.add(detail.dynamicId)
                    } else {
                        errorCount++
                    }

                    kotlinx.coroutines.delay(300)
                }

                if (successfulDynamicIds.isNotEmpty()) {
                    actionDao.deleteByDynamicIds(successfulDynamicIds)
                    officialInfoDao.deleteByDynamicIds(successfulDynamicIds)
                    dynamicInfoDao.deleteByIds(successfulDynamicIds)
                }

                if (errorCount > 0) {
                    _toastMessage.emit("删除错误 $errorCount 条")
                    return@launch
                }

                dynamicIdsDao.deleteByArticleId(articleId)
                taskDao.deleteByArticleId(articleId)
                articleDao.deleteByArticleId(articleId)
                _toastMessage.emit("删除成功")
                onComplete()

            } catch (e: Exception) {
                _toastMessage.emit("删除失败: ${e.localizedMessage}")
            } finally {
                _isDeleting.value = false
            }
        }
    }
}
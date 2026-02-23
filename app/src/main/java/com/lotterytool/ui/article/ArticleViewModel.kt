package com.lotterytool.ui.article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lotterytool.data.repository.ArticleRepository
import com.lotterytool.data.repository.actionRepository.RemoveRepository
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.article.ArticleDao
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.task.TaskDao
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.data.workers.ExtractDynamicWorker
import com.lotterytool.utils.FetchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

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
    private val removeRepository: RemoveRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val userMid: Long = savedStateHandle["mid"] ?: 0L

    // 加载状态
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // 删除状态
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // 删除错误信息
    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError = _deleteError.asStateFlow()

    // Toast通知
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    // 获取专栏列表数据流
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

    fun startAutoProcessAll(isBusy: Boolean) {
        val allArticles = articles.value
        if (allArticles.isEmpty() || isBusy) return

        val workRequests = allArticles.map { article ->
            OneTimeWorkRequestBuilder<ExtractDynamicWorker>()
                .setInputData(
                    Data.Builder()
                        .putLong("ARTICLE_ID", article.articleId)
                        .putLong("USER_MID", article.mid)
                        .build()
                )
                .addTag("extract_${article.articleId}")
                .build()
        }

        workManager.beginUniqueWork(
            "AUTO_PROCESS_CHAIN",
            ExistingWorkPolicy.REPLACE,
            workRequests
        ).enqueue()
    }

    fun startExtractionTask(articleId: Long, userMid: Long, isBusy: Boolean) {
        if (isBusy) return

        val inputData = Data.Builder()
            .putLong("ARTICLE_ID", articleId)
            .putLong("USER_MID", userMid)
            .build()

        val request = OneTimeWorkRequestBuilder<ExtractDynamicWorker>()
            .setInputData(inputData)
            .addTag("extract_$articleId")
            .addTag("MANUAL_EXTRACT")
            .build()

        workManager.enqueueUniqueWork(
            "unique_extract_$articleId",
            ExistingWorkPolicy.KEEP,
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

            try {
                _isDeleting.value = true
                val dynamicIds = dynamicInfoDao.getDynamicIdsByArticleId(articleId)
                val user = userDao.getUserById(userMid)
                if (user != null && dynamicIds.isNotEmpty()) {
                    val cookie = user.SESSDATA
                    val csrf = user.CSRF

                    // 循环删除服务器上的内容
                    dynamicIds.forEachIndexed { index, id ->
                        removeRepository.executeRemove(cookie, csrf, id)

                        // 如果不是最后一个元素，则增加延迟
                        if (index < dynamicIds.size - 1) {
                            // 随机延迟 1000ms - 2000ms
                            val shadowDelay = Random.nextLong(1000, 2000)
                            delay(shadowDelay)
                        }
                    }
                }
                actionDao.deleteByArticleId(articleId)
                if (dynamicIds.isNotEmpty()) {
                    officialInfoDao.deleteByDynamicIds(dynamicIds)
                }
                dynamicInfoDao.deleteByArticleId(articleId)
                dynamicIdsDao.deleteByArticleId(articleId)
                taskDao.deleteByArticleId(articleId)
                articleDao.deleteByArticleId(articleId)
                _toastMessage.emit("删除成功")
                onComplete()
            } catch (e: Exception) {
                _deleteError.value = "删除失败: ${e.localizedMessage}"
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }
}
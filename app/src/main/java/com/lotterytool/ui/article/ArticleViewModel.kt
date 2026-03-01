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
import com.lotterytool.data.workers.ExtractDynamicWorker
import com.lotterytool.utils.FetchResult
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val removeRepository: RemoveRepository,
    private val userDynamicDao: UserDynamicDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val userMid: Long = savedStateHandle["mid"] ?: 0L

    // 加载状态
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // 删除状态
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()

    // 错误信息（列表加载）
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // 同步数据 Dialog 错误信息（专供 loadServiceId 失败后在 Dialog 内展示）
    private val _syncDialogError = MutableStateFlow<String?>(null)
    val syncDialogError = _syncDialogError.asStateFlow()

    // Toast 通知（所有需要 toast 的场景，包括 deleteArticleFull 的成功与失败）
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

    /**
     * 同步动态服务 ID。
     * - 成功：_syncDialogError 保持 null，_isRefreshing 归 false → Screen 侧自动关闭 Dialog。
     * - 失败：将错误写入 _syncDialogError，Dialog 展示「重试 / 取消」。
     */
    fun loadServiceId(
        isRunning: Boolean,
        isGlobalBusy: Boolean,
    ) {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                // 每次发起请求时先清空上次的错误，保持 Dialog 内状态干净
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
                    // 失败：写入 Dialog 专用错误，不关 Dialog
                    _syncDialogError.value = result.message
                }
                // 成功：_syncDialogError 为 null，Screen 侧 LaunchedEffect 检测到后自动关 Dialog

            } catch (e: Exception) {
                _syncDialogError.value = "请求发生错误: ${e.localizedMessage}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 用于「取消」按钮或 Dialog 关闭时清除同步错误状态 */
    fun clearSyncDialogError() {
        _syncDialogError.value = null
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

    /**
     * 删除专栏全量数据。
     *
     * 删除策略（精细化删除）：
     * 1. 逐条处理该专栏下的每个动态，依据 [ dynamicId ] 查找对应的 [ serviceId ] 。
     * 2. 若某条动态找不到匹配的 [ serviceId ]，或远端删除请求失败，则该条计入错误计数。
     * 3. 远端删除成功的动态才会清除本地对应的 action_info、official_info、dynamic_info 记录。
     * 4. 存在任意错误时通过 Toast 提示「删除错误 X 条」；成功数量不额外提示。
     * 5. 仅当所有动态均删除成功时，才进一步删除 dynamic_ids、task、article 记录，
     *    并通过 [onComplete] 回调通知 UI 关闭/返回。
     * 6. 若存在删除失败的动态，专栏卡片与 DynamicInfoScreen 中对应条目均保持可见。
     */
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

                val allDynamicIds = dynamicInfoDao.getDynamicIdsByArticleId(articleId)

                // 专栏下无动态时，直接清理文章级记录
                if (allDynamicIds.isEmpty()) {
                    actionDao.deleteByArticleId(articleId)
                    dynamicIdsDao.deleteByArticleId(articleId)
                    taskDao.deleteByArticleId(articleId)
                    articleDao.deleteByArticleId(articleId)
                    onComplete()
                    return@launch
                }

                // 逐条尝试远端删除，分类记录成功与失败
                val successfulDynamicIds = mutableListOf<Long>()
                var errorCount = 0

                for (dynamicId in allDynamicIds) {
                    // 查找该动态在用户动态表中对应的远端 serviceId
                    val serviceId = userDynamicDao.getServiceIdByOriginalId(dynamicId)

                    if (serviceId == null) {
                        // 无法匹配 serviceId，视为删除失败
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
                        // 远端删除成功：同步删除本地 user_dynamic 记录
                        userDynamicDao.deleteByServiceId(serviceId)
                        successfulDynamicIds.add(dynamicId)
                    } else {
                        // 远端删除失败：保留本地数据，仅计入错误
                        errorCount++
                    }

                    kotlinx.coroutines.delay(300)
                }

                // 清理远端删除成功的动态的本地明细记录
                for (dynamicId in successfulDynamicIds) {
                    actionDao.deleteByDynamicId(dynamicId)
                    officialInfoDao.deleteByDynamicIds(listOf(dynamicId))
                    dynamicInfoDao.deleteById(dynamicId)
                }

                // 有任意删除失败：Toast 提示错误数量，保留专栏卡片与失败条目
                if (errorCount > 0) {
                    _toastMessage.emit("删除错误 $errorCount 条")
                    // 不执行 onComplete，专栏卡片和动态条目继续显示
                    return@launch
                }

                // 全部删除成功：清理文章级记录，触发 UI 返回/关闭
                actionDao.deleteByArticleId(articleId)
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
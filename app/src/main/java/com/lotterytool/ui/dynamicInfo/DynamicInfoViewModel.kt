package com.lotterytool.ui.dynamicInfo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotterytool.data.repository.DynamicInfoRepository
import com.lotterytool.data.repository.OfficialRepository
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDetail
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.officialInfo.OfficialInfoEntity
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.data.workers.DynamicAction
import com.lotterytool.utils.FetchResult
import com.lotterytool.utils.ReplyMessage
import com.lotterytool.utils.RepostContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DynamicInfoViewModel @Inject constructor(
    private val repository: DynamicInfoRepository,
    private val officialRepository: OfficialRepository,
    private val dynamicAction: DynamicAction,
    private val userDao: UserDao,
    private val officialInfoDao: OfficialInfoDao,
    dynamicInfoDao: DynamicInfoDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val articleId: Long = savedStateHandle.get<Long>("articleId") ?: -1L
    private val type: Int = savedStateHandle.get<Int>("type") ?: 0
    private val userMid: Long = savedStateHandle.get<Long>("userMid") ?: -1L

    // ── 选中的官方动态 ID（驱动 officialDetail Flow）──────────────────────────
    var selectedOfficialId by mutableStateOf<Long?>(null)
        private set

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: String? get() = _errorMessage.value

    // ── 动态列表（按 articleId + type 过滤）──────────────────────────────────
    /**
     * 从数据库视图 [DynamicInfoDetail] 中实时获取当前专栏页面对应 type 的动态列表。
     * type 由导航参数传入（0=官方, 1=普通, 2=特殊）。
     */
    val dynamicList: StateFlow<List<DynamicInfoDetail>> =
        dynamicInfoDao.getInfoByArticleAndType(articleId, type)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // ── 官方抽奖详情（随 selectedOfficialId 变化实时更新）────────────────────
    /**
     * 通过 [snapshotFlow] 将 Compose State 转为 Flow，
     * 再 flatMapLatest 切换到对应 dynamicId 的实时查询。
     * 当 selectedOfficialId 为 null 时发出 null（对话框不显示）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val officialDetail: StateFlow<OfficialInfoEntity?> =
        snapshotFlow { selectedOfficialId }
            .flatMapLatest { id ->
                if (id == null) flowOf(null)
                else officialInfoDao.getOfficialByIdFlow(id)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    // ── 对话框控制 ────────────────────────────────────────────────────────────
    fun showOfficialDetail(id: Long) { selectedOfficialId = id }
    fun dismissDialog() { selectedOfficialId = null }

    // ── 重试：解析错误 ────────────────────────────────────────────────────────
    fun retryExtraction(info: DynamicInfoDetail) {
        viewModelScope.launch {
            val currentUser = if (userMid != -1L) userDao.getUserById(userMid) else null
            val cookie = currentUser?.SESSDATA
            if (cookie.isNullOrBlank()) {
                _errorMessage.value = "未找到有效的登录状态，请先登录"
                return@launch
            }
            val result = repository.retrySingleDynamic(
                cookie = cookie,
                dynamicId = info.dynamicId,
                articleId = articleId,
                isSpecial = info.type == 2
            )
            if (result is FetchResult.Error) {
                _errorMessage.value = "重试失败: ${result.message}"
            }
        }
    }

    // ── 重试：官方抽奖信息 ────────────────────────────────────────────────────
    fun retryOfficial(id: Long) {
        viewModelScope.launch {
            val currentUser = if (userMid != -1L) userDao.getUserById(userMid) else null
            val cookie = currentUser?.SESSDATA
            if (cookie.isNullOrBlank()) return@launch
            val result = officialRepository.fetchOfficial(
                cookie = cookie,
                dynamicId = id
            )
            if (result is FetchResult.Error) {
                _errorMessage.value = "重试官方详情失败: ${result.message}"
            }
        }
    }

    // ── 重试：任务执行（转发 / 点赞 / 评论 / 关注）───────────────────────────

    /**
     * 当前待处理的动态信息（用于显示确认 Dialog）。
     * 非 null 时表示 Dialog 应当呈现。
     */
    var pendingRetryInfo by mutableStateOf<DynamicInfoDetail?>(null)
        private set

    /**
     * Dialog 内部是否正在执行重试任务。
     * true 时显示 loading 转圈，按钮禁用；false 且 [pendingRetryInfo] 非 null 时显示确认按钮。
     */
    var isRetrying by mutableStateOf(false)
        private set

    fun showRetryDialog(info: DynamicInfoDetail) {
        pendingRetryInfo = info
    }

    fun dismissRetryDialog() {
        // 只在未执行任务时允许手动关闭
        if (!isRetrying) {
            pendingRetryInfo = null
        }
    }

    /**
     * 执行重试：
     * 1. 保留 Dialog 并切换到 loading 状态（转圈）。
     * 2. 执行 [DynamicAction.performAction]，内部已跳过成功的步骤。
     * 3. 完成后关闭 Dialog，Room Flow 自动刷新卡片展示。
     *    若该动态所有步骤均成功，[DynamicProblemsViewModel] 的 [actionErrorItems] 将
     *    自动将其从可展开卡片中移除，无需额外处理。
     */
    fun retryAction(info: DynamicInfoDetail) {
        viewModelScope.launch {
            val currentUser = if (userMid != -1L) userDao.getUserById(userMid) else null
            val cookie = currentUser?.SESSDATA
            val csrf = currentUser?.CSRF

            if (cookie.isNullOrBlank() || csrf.isNullOrBlank()) {
                _errorMessage.value = "登录状态失效，请重新登录"
                pendingRetryInfo = null
                return@launch
            }

            // 开始执行：进入 loading 状态，Dialog 保持显示
            isRetrying = true

            val result = dynamicAction.performAction(
                dynamicId = info.dynamicId,
                cookie = cookie,
                csrf = csrf,
                content = RepostContent.getRandom(),
                message = ReplyMessage.getRandom()
            )

            if (result is FetchResult.Error) {
                _errorMessage.value = "执行失败: ${result.message}"
            }

            // 执行完毕：关闭 Dialog
            isRetrying = false
            pendingRetryInfo = null
        }
    }

    // ── 删除单条动态（带确认 Dialog）────────────────────────────────────────

    var pendingDeleteInfo by mutableStateOf<DynamicInfoDetail?>(null)
        private set

    fun showDeleteDialog(info: DynamicInfoDetail) {
        pendingDeleteInfo = info
    }

    fun dismissDeleteDialog() {
        pendingDeleteInfo = null
    }

    fun confirmDelete(info: DynamicInfoDetail) {
        viewModelScope.launch {
            // 只保留本地删除
            repository.deleteDynamicLocally(info.dynamicId, info.type == 0)
            dismissDeleteDialog()
        }
    }
}
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
import com.lotterytool.data.repository.actionRepository.RemoveRepository
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDetail
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.officialInfo.OfficialInfoEntity
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.data.room.user.UserEntity
import com.lotterytool.data.workers.DynamicAction
import com.lotterytool.utils.FetchResult
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
    private val removeRepository: RemoveRepository,
    private val dynamicAction: DynamicAction,
    private val userDao: UserDao,
    // 注入 DAO 用于读取动态列表与官方抽奖详情
    private val dynamicInfoDao: DynamicInfoDao,
    private val officialInfoDao: OfficialInfoDao,
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

    // ── 当前用户 ──────────────────────────────────────────────────────────────
    @OptIn(ExperimentalCoroutinesApi::class)
    val user: StateFlow<UserEntity?> = flowOf(userMid)
        .flatMapLatest { mid ->
            if (mid == -1L) flowOf(null)
            else userDao.getUserFlowById(mid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

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
            val cookie = user.value?.SESSDATA
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
            val cookie = user.value?.SESSDATA
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
    fun retryAction(info: DynamicInfoDetail) {
        viewModelScope.launch {
            val currentUser = user.value
            val cookie = currentUser?.SESSDATA
            val csrf = currentUser?.CSRF
            if (cookie.isNullOrBlank() || csrf.isNullOrBlank()) {
                _errorMessage.value = "登录状态失效或未登录，请重新登录"
                return@launch
            }
            val result = dynamicAction.performAction(
                dynamicId = info.dynamicId,
                cookie = cookie,
                csrf = csrf,
                content = "转发抽奖",
                message = "来了来了"
            )
            if (result is FetchResult.Error) {
                _errorMessage.value = "执行任务失败: ${result.message}"
            }
        }
    }

    // ── 删除单条动态（远端 + 本地）──────────────────────────────────────────
    fun deleteDynamic(info: DynamicInfoDetail) {
        viewModelScope.launch {
            val currentUser = user.value
            val cookie = currentUser?.SESSDATA
            val csrf = currentUser?.CSRF
            if (!cookie.isNullOrBlank() && !csrf.isNullOrBlank()) {
                val removeResult = removeRepository.executeRemove(
                    cookie = cookie,
                    csrf = csrf,
                    dynamicId = info.dynamicId
                )
                if (removeResult is FetchResult.Error) {
                    _errorMessage.value = "远端删除提示: ${removeResult.message}"
                }
            }
            repository.deleteDynamicLocally(
                dynamicId = info.dynamicId,
                isOfficial = info.type == 0
            )
        }
    }
}
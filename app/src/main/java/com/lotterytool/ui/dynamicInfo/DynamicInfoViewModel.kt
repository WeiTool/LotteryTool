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
import com.lotterytool.data.room.userDynamic.UserDynamicDao
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
    private val userDynamicDao: UserDynamicDao,
    private val removeRepository: RemoveRepository,
    dynamicInfoDao: DynamicInfoDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val articleId: Long = savedStateHandle.get<Long>("articleId") ?: -1L
    private val type: Int = savedStateHandle.get<Int>("type") ?: 0
    private val userMid: Long = savedStateHandle.get<Long>("userMid") ?: -1L

    // ── 选中的官方动态 ID（驱动 officialDetail Flow）──────────────────────────
    var selectedOfficialId by mutableStateOf<Long?>(null)
        private set

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

    // ── 官方详情对话框控制 ────────────────────────────────────────────────────
    fun showOfficialDetail(id: Long) { selectedOfficialId = id }
    fun dismissOfficialDialog() { selectedOfficialId = null }

    /** 官方详情对话框内：重试加载官方抽奖信息 */
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
                dialogError = "失败：${result.message}"
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 统一操作对话框（重试解析 / 重试任务 / 删除）
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * 当前待处理的操作状态。
     * 非 null 时统一操作对话框应当显示。
     */
    var pendingAction by mutableStateOf<PendingAction?>(null)
        private set

    /**
     * 对话框内部是否正在执行操作。
     * true 时显示 loading 转圈，所有按钮禁用；false 时显示确认/取消按钮。
     */
    var isExecuting by mutableStateOf(false)
        private set

    /**
     * 对话框内显示的错误信息。
     * 执行失败时非 null，对话框不自动关闭；执行成功或手动关闭时清空。
     */
    var dialogError by mutableStateOf<String?>(null)
        private set

    // ── 显示对话框 ────────────────────────────────────────────────────────────

    fun showRetryExtraction(info: DynamicInfoDetail) {
        dialogError = null
        pendingAction = PendingAction(info, ActionType.RETRY_EXTRACTION)
    }

    fun showRetryAction(info: DynamicInfoDetail) {
        dialogError = null
        pendingAction = PendingAction(info, ActionType.RETRY_ACTION)
    }

    fun showDeleteDialog(info: DynamicInfoDetail) {
        dialogError = null
        pendingAction = PendingAction(info, ActionType.DELETE)
    }

    // ── 关闭对话框（仅在未执行时允许，只能由取消按钮调用）────────────────────
    fun dismissActionDialog() {
        if (!isExecuting) {
            pendingAction = null
            dialogError = null
        }
    }

    // ── 执行当前对话框对应的操作 ───────────────────────────────────────────────
    /**
     * 根据 [pendingAction] 的类型分发执行：
     * - 执行期间 [isExecuting] = true，对话框保持显示（不可点击空白处关闭）。
     * - 成功后自动关闭对话框（[pendingAction] 置 null）。
     * - 失败时将错误信息写入 [dialogError]，对话框保持显示等待用户操作。
     */
    fun executeCurrentAction() {
        val current = pendingAction ?: return
        viewModelScope.launch {
            isExecuting = true
            dialogError = null

            when (current.type) {
                ActionType.RETRY_EXTRACTION -> executeRetryExtraction(current.info)
                ActionType.RETRY_ACTION    -> executeRetryAction(current.info)
                ActionType.DELETE          -> executeDelete(current.info)
            }

            isExecuting = false
            // 仅在没有错误时（即操作成功）自动关闭
            if (dialogError == null) {
                pendingAction = null
            }
        }
    }

    // ── 内部执行：重新解析 ─────────────────────────────────────────────────────
    private suspend fun executeRetryExtraction(info: DynamicInfoDetail) {
        val currentUser = if (userMid != -1L) userDao.getUserById(userMid) else null
        val cookie = currentUser?.SESSDATA
        if (cookie.isNullOrBlank()) {
            dialogError = "未找到有效的登录状态，请先登录"
            return
        }
        val result = repository.retrySingleDynamic(
            cookie = cookie,
            dynamicId = info.dynamicId,
            articleId = articleId,
            isSpecial = info.type == 2
        )
        if (result is FetchResult.Error) {
            dialogError = "重新解析失败：${result.message}"
        }
    }

    // ── 内部执行：重新执行任务 ─────────────────────────────────────────────────
    private suspend fun executeRetryAction(info: DynamicInfoDetail) {
        val currentUser = if (userMid != -1L) userDao.getUserById(userMid) else null
        val cookie = currentUser?.SESSDATA
        val csrf = currentUser?.CSRF

        if (cookie.isNullOrBlank() || csrf.isNullOrBlank()) {
            dialogError = "登录状态失效，请重新登录"
            return
        }

        val result = dynamicAction.performAction(
            dynamicId = info.dynamicId,
            cookie = cookie,
            csrf = csrf,
            content = RepostContent.getRandom(),
            message = ReplyMessage.getRandom()
        )

        if (result is FetchResult.Error) {
            dialogError = "执行失败：${result.message}"
        }
    }

    // ── 内部执行：删除动态 ─────────────────────────────────────────────────────
    private suspend fun executeDelete(info: DynamicInfoDetail) {
        val currentUser = if (userMid != -1L) userDao.getUserById(userMid) else null
        val cookie = currentUser?.SESSDATA
        val csrf = currentUser?.CSRF

        // 1. 尝试从 user_dynamic 表获取对应的 serviceId（远端 ID）
        val serviceId = userDynamicDao.getServiceIdByOriginalId(info.dynamicId)

        // 2. 如果有登录信息且找到了 serviceId，执行远端删除
        if (!cookie.isNullOrBlank() && !csrf.isNullOrBlank() && serviceId != null) {
            val result = removeRepository.executeRemove(
                cookie = cookie,
                csrf = csrf,
                dynamicId = serviceId
            )
            if (result is FetchResult.Error) {
                // 远端删除失败：展示错误信息，并阻断本地删除
                dialogError = "远端删除失败：${result.message}"
                return
            }
        }

        // 3. 远端删除成功（或无需远端删除）后，执行本地逻辑删除
        repository.deleteDynamicLocally(info.dynamicId, info.type == 0)
    }
}

// ── 统一操作对话框的动作类型 ─────────────────────────────────────────────────────

enum class ActionType {
    /** 重新解析（解析错误重试） */
    RETRY_EXTRACTION,
    /** 重新执行任务（转发/点赞/评论/关注） */
    RETRY_ACTION,
    /** 删除动态 */
    DELETE
}

/**
 * 统一对话框的待处理状态。
 * 非 null 时表示对话框应当显示。
 */
data class PendingAction(
    val info: DynamicInfoDetail,
    val type: ActionType
)
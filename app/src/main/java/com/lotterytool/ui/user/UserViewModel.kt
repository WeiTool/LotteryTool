package com.lotterytool.ui.user

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lotterytool.utils.toQrCode
import com.lotterytool.data.repository.QRRepository
import com.lotterytool.data.repository.UserRepository
import com.lotterytool.utils.FetchResult
import com.lotterytool.utils.parseBiliAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import javax.inject.Inject
import androidx.core.content.edit
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class UserViewModel @Inject constructor(
    private val qrRepository: QRRepository,
    private val userRepository: UserRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    // 接收错误信息
    private val _userErrors = MutableStateFlow<Map<Long?, String>>(emptyMap())
    val userErrors = _userErrors.asStateFlow()

    // 控制是否在加载
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // 控制按钮情况
    private val _qrState = MutableStateFlow<QRState>(QRState.Idle)
    val qrState = _qrState.asStateFlow()

    // 定义Job 变量
    private var loginJob: Job? = null

    // 获取数据库的 user 数据流
    val users = userRepository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        checkAndAutoRefresh()
    }

    private fun checkAndAutoRefresh() {
        viewModelScope.launch {
            val lastRefreshDate = prefs.getLong("last_auto_refresh_date", 0L)
            val currentTime = System.currentTimeMillis()

            if (isNewDay(lastRefreshDate, currentTime)) {
                refresh() // 执行你现有的刷新逻辑
                // 刷新成功后更新记录（也可以在 refresh() 内部成功后再存）
                prefs.edit { putLong("last_auto_refresh_date", currentTime) }
            }
        }
    }

    private fun isNewDay(lastTime: Long, currentTime: Long): Boolean {
        if (lastTime == 0L) return true
        val lastCal = Calendar.getInstance().apply { timeInMillis = lastTime }
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentTime }

        return lastCal.get(Calendar.DAY_OF_YEAR) != currentCal.get(Calendar.DAY_OF_YEAR) ||
                lastCal.get(Calendar.YEAR) != currentCal.get(Calendar.YEAR)
    }


    // 下拉整体刷新
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _userErrors.value = emptyMap()

            // 设置一个业务层面的硬超时，例如 15 秒
            val result = withTimeoutOrNull(15_000L) {
                userRepository.refreshAllUsers()
            } ?: FetchResult.Error("网络请求超时，请稍后重试") // 如果超时则返回自定义错误

            if (result is FetchResult.Error) {
                val errorMap = mutableMapOf<Long?, String>()
                result.message.split(";").forEach { entry ->
                    val parts = entry.split(":", limit = 2)
                    if (parts.size == 2) {
                        val mid = parts[0].toLongOrNull()
                        if (mid != null) {
                            errorMap[mid] = parts[1]
                        }
                    }
                }
                // 如果是硬超时导致的错误，message 会是 "网络请求超时..."
                if (errorMap.isEmpty() && result.message.isNotEmpty()) {
                    _userErrors.value = mapOf(null to result.message)
                } else {
                    _userErrors.value = errorMap
                }
            }
            _isRefreshing.value = false
        }
    }

    fun startLoginProcess() {
        // 1. 立即取消之前的任务，防止多个轮询同时运行
        loginJob?.cancel()

        // 2. 重新赋值
        loginJob = viewModelScope.launch {
            try {
                _qrState.value = QRState.Loading

                val result = qrRepository.getQR()
                if (result is FetchResult.Success) {
                    val qrData = result.data
                    val bitmap = qrData?.url?.toQrCode(600)

                    if (bitmap != null) {
                        _qrState.value = QRState.Success(bitmap, qrData.qrcodeKey)
                        // 这里的轮询会随 loginJob 的取消而自动停止
                        runQRPolling(qrData.qrcodeKey)
                    } else {
                        _qrState.value = QRState.Error("二维码数据异常")
                    }
                } else if (result is FetchResult.Error) {
                    _qrState.value = QRState.Error(result.message)
                }
            } catch (e: CancellationException) {
                // 如果协程被取消，可以不做处理，或者重置为 Idle
                _qrState.value = QRState.Idle
                throw e // 协程最佳实践：重新抛出取消异常
            }
        }
    }

    private suspend fun runQRPolling(qrcodeKey: String) {
        val startTime = System.currentTimeMillis()
        val totalTimeout = 180_000L
        var consecutiveTimeouts = 0 // 记录连续超时的次数

        while (currentCoroutineContext().isActive) {
            if (System.currentTimeMillis() - startTime > totalTimeout) {
                _qrState.value = QRState.Error("二维码已过期，请点击刷新")
                return
            }

            val result = withTimeoutOrNull(8_000L) { // 设置单次请求超时
                qrRepository.executionQR(qrcodeKey)
            }

            when (result) {
                is FetchResult.Success -> {
                    consecutiveTimeouts = 0 // 成功后重置计数
                    if (result.data != "PENDING") {
                        processLoginVerification(result.data)
                        return
                    }
                }
                is FetchResult.Error -> {
                    // 如果后端明确返回二维码失效的代码，这里直接重新获取
                    if (result.message.contains("失效")) {
                        startLoginProcess() // 关键：重新调用开始流程
                    } else {
                        _qrState.value = QRState.Error(result.message)
                    }
                    return
                }
                null -> {
                    // --- 重点：在这里处理单次请求超时 ---
                    consecutiveTimeouts++
                    if (consecutiveTimeouts >= 3) {
                        // 如果连续 3 次请求都超时（约 30 秒无响应），主动刷新二维码
                        startLoginProcess()
                        return // 结束当前的轮询协程
                    }
                    // 如果次数还没到，继续循环（下一次 delay 后重试）
                }
            }
            delay(2000)
        }
    }

    // 获取用户信息
    private suspend fun processLoginVerification(authData: String?) {
        val auth = authData?.parseBiliAuth()
        if (auth != null) {
            _qrState.value = QRState.Verifying
            val result = userRepository.fetchUser(
                SESSDATA = auth.sessDataCookie,
                CSRF = auth.csrfCookie
            )

            if (result is FetchResult.Success) {
                _qrState.value = QRState.Idle
            } else if (result is FetchResult.Error) {
                _qrState.value = QRState.Idle // 关闭验证状态
                // 使用 0L 作为扫码登录失败的占位 Key
                _userErrors.value = mapOf(0L to (result.message))
            }
        } else {
            _qrState.value = QRState.Error("解析登录信息失败")
        }
    }

    fun resetQRState() {
        _qrState.value = QRState.Idle
        loginJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        loginJob?.cancel()
    }
}
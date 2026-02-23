package com.lotterytool.ui.user

import android.graphics.Bitmap

/**
 * 二维码登录状态机
 */
sealed class QRState {
    /** 初始状态：未开始或已结束 */
    object Idle : QRState()

    /** 加载状态：正在请求服务器获取二维码 URL */
    object Loading : QRState()

    /** * 获取成功：显示二维码
     * @property bitmap 生成的二维码图片
     * @property qrcodeKey 用于轮询的唯一标识
     */
    data class Success(
        val bitmap: Bitmap,
        val qrcodeKey: String
    ) : QRState()

    /** * 扫码成功：用户已在手机端点击确认，正在进行最后的令牌换取和用户信息拉取
     */
    object Verifying : QRState()

    /**
     * 错误状态：包含网络错误、二维码失效等
     * @property message 错误提示信息
     */
    data class Error(val message: String) : QRState()
}
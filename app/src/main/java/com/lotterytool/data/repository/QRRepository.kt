package com.lotterytool.data.repository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.utils.FetchResult
import javax.inject.Inject

class QRRepository @Inject constructor(
    val apiServices: ApiServices
) {
    data class QRData(val url: String, val qrcodeKey: String)

    suspend fun getQR(): FetchResult<QRData> {
        return try {
            val response = apiServices.getQR()
            if (response.code == 0) {
                FetchResult.Success(QRData(response.data.url, response.data.key))
            } else {
                FetchResult.Error("获取失败: ${response.message}")
            }
        } catch (e: Exception) {
            val userFriendlyMessage = when (e) {
                is java.net.UnknownHostException -> "无法连接服务器，请检查网络设置"
                is java.net.SocketTimeoutException -> "连接超时，请稍后重试"
                else -> e.message ?: "网络异常"
            }
            FetchResult.Error(userFriendlyMessage)
        }
    }

    suspend fun executionQR(qrcodeKey: String): FetchResult<String> {
        return try {
            val response = apiServices.QR(qrcodeKey = qrcodeKey)

            // 1. 检查 API 请求本身是否成功（假设外层 code=0 代表接口调通）
            if (response.code == 0) {
                val qrData = response.data // 获取图片中的 data 对象

                // 2. 在 data 内部检查扫码的 code
                when (qrData.code) {
                    0 -> {
                        // 扫码成功，提取 url
                        FetchResult.Success(qrData.url)
                    }
                    86101, 86090 -> FetchResult.Success("PENDING")
                    86038 -> FetchResult.Error("二维码已失效")
                    else -> FetchResult.Error(qrData.message)
                }
            } else {
                // API 外层报错
                FetchResult.Error(response.message)
            }
        } catch (e: Exception) {
            FetchResult.Error(e.message ?: "网络异常")
        }
    }
}


package com.lotterytool.data.repository.actionRepository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.models.LikeRequest
import com.lotterytool.utils.FetchResult
import javax.inject.Inject

class LikeRepository @Inject constructor(
    private val apiServices: ApiServices,
) {
    suspend fun executeLike(cookie: String, csrf: String, dynamicId: Long): FetchResult<Unit> {
        return try {
            val requestBody = LikeRequest(
                dyn_id_str = dynamicId.toString(),
                up = 1
            )

            val response = apiServices.like(
                cookie = cookie,
                csrf = csrf,
                request = requestBody
            )

            when (response.code) {
                0 -> FetchResult.Success()
                -101 -> FetchResult.Error("账号未登录")
                -111 -> FetchResult.Error("csrf 校验失败")
                4100001 -> FetchResult.Error("参数错误")
                else -> FetchResult.Error(response.message)
            }
        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "网络请求失败")
        }
    }
}

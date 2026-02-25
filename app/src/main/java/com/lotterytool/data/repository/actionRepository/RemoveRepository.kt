package com.lotterytool.data.repository.actionRepository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.models.RemoveRequest
import com.lotterytool.utils.FetchResult
import javax.inject.Inject

class RemoveRepository @Inject constructor(
    private val apiServices: ApiServices
) {
    suspend fun executeRemove(cookie: String, csrf: String, dynamicId: Long): FetchResult<Unit> {
        return try {
            val requestBody = RemoveRequest(
                dyn_id_str = dynamicId.toString(),
            )
            val response = apiServices.remove(
                cookie = cookie,
                csrf = csrf,
                body = requestBody
            )

            when (response.code) {
                0 -> FetchResult.Success()
                -101 -> FetchResult.Error("账号未登录")
                -111 -> FetchResult.Error("csrf 校验失败")
                -400 -> FetchResult.Error("请求错误")
                4101001 -> FetchResult.Error("参数错误")
                4101144 -> FetchResult.Error("只能删除自身的动态")
                else -> FetchResult.Error(response.message)
            }

        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "网络请求失败")
        }
    }

}

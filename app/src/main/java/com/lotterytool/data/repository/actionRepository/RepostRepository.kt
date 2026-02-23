package com.lotterytool.data.repository.actionRepository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.utils.FetchResult
import javax.inject.Inject

class RepostRepository @Inject constructor(
    private val apiServices: ApiServices
) {
    suspend fun executeRepost(
        cookie: String,
        csrf: String,
        dynamicId: Long,
        content: String
    ): FetchResult<Unit> {
        return try {
            val response = apiServices.repost(
                cookie = cookie,
                dynamicId = dynamicId,
                content = content,
                csrf = csrf
            )

            // 根据 B 站 API 常见的返回码进行判断
            when (response.code) {
                0 -> FetchResult.Success()
                else -> FetchResult.Error(response.message)
            }
        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "网络请求失败")
        }
    }
}
package com.lotterytool.data.repository.actionRepository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.utils.FetchResult
import javax.inject.Inject

class FollowRepository @Inject constructor(
    private val apiServices: ApiServices
) {
    suspend fun executeFollow(cookie: String, uid: Long, act: Int, csrf: String): FetchResult<Unit> {
        return try {
            // act = 1 表示关注
            val response = apiServices.follow(cookie = cookie, fid = uid, act = act, csrf = csrf)
            when (response.code) {
                0 -> FetchResult.Success()
                -101 -> FetchResult.Error("账号未登录")
                -102 -> FetchResult.Error("账号被封停")
                -111 -> FetchResult.Error("csrf校验失败")
                -400 -> FetchResult.Error("请求错误")
                22001 -> FetchResult.Error("不能对自己进行此操作")
                22002 -> FetchResult.Error("因对方隐私设置，你还不能关注")
                22003 -> FetchResult.Error("关注失败，请将该用户移除黑名单之后再试")
                22008 -> FetchResult.Error("黑名单达到上限")
                22009 -> FetchResult.Error("关注失败，已达关注上限")
                22013 -> FetchResult.Error("账号已注销，无法完成操作")
                22014 -> FetchResult.Error("已经关注用户，无法重复关注")
                22120 -> FetchResult.Error("重复加入黑名单")
                40061 -> FetchResult.Error("用户不存在")
                else -> FetchResult.Error(response.message)
            }
        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "网络请求失败")
        }
    }
}
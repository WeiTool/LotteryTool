package com.lotterytool.data.repository.actionRepository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.utils.FetchResult
import javax.inject.Inject

class ReplyRepository @Inject constructor(
    private val apiServices: ApiServices
) {
    suspend fun executeReply(
        cookie: String,
        type: Int,
        rid: Long,
        message: String,
        csrf: String
    ): FetchResult<Unit> {
        return try {
            val response = apiServices.reply(
                cookie = cookie,
                type = type,
                oid = rid,
                message = message,
                csrf = csrf
            )
            when (response.code) {
                0 -> FetchResult.Success()
                -101 -> FetchResult.Error("账号未登录")
                -102 -> FetchResult.Error("账号被封停")
                -111 -> FetchResult.Error("csrf校验失败")
                -400 -> FetchResult.Error("请求错误")
                -404 -> FetchResult.Error("无此项")
                -509 -> FetchResult.Error("请求过于频繁")
                12001 -> FetchResult.Error("已经存在评论主题")
                12002 -> FetchResult.Error("评论区已关闭")
                12003 -> FetchResult.Error("禁止回复")
                12006 -> FetchResult.Error("没有该评论")
                12009 -> FetchResult.Error("评论主体的type不合法")
                12015 -> FetchResult.Error("需要评论验证码")
                12016 -> FetchResult.Error("评论内容包含敏感信息")
                12025 -> FetchResult.Error("评论字数过多")
                12035 -> FetchResult.Error("该账号被UP主列入评论黑名单")
                12051 -> FetchResult.Error("重复评论，请勿刷屏")
                12052 -> FetchResult.Error("评论区已关闭")
                12045 -> FetchResult.Error("购买后即可发表评论")
                else -> FetchResult.Error(response.message)
            }
        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "网络请求失败")
        }
    }
}
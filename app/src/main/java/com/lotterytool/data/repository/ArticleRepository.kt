package com.lotterytool.data.repository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.room.article.ArticleDao
import com.lotterytool.data.room.article.ArticleEntity
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.utils.FetchResult
import com.lotterytool.utils.LotteryUser
import com.lotterytool.utils.WbiSigner
import javax.inject.Inject

class ArticleRepository @Inject constructor(
    private val apiServices: ApiServices,
    private val articleDao: ArticleDao,
    private val userDao: UserDao
) {
    suspend fun fetchArticles(cookie: String, mid: Long, ps: Int): FetchResult<Unit> {
        // 获取本地用户信息，用于 WBI 签名
        val user = userDao.getUserById(mid)
        if (user?.imgUrlId == null) {
            return FetchResult.Error("缺少签名密钥，请先刷新用户信息")
        }

        // 构造请求参数
        val params = mapOf(
            "mid" to LotteryUser.USER_1.toString(),
            "pn" to "1",
            "ps" to ps.toString(),
            "sort" to "publish_time",
        )

        // WBI 签名加密
        val signedParams = WbiSigner.sign(
            params = params, imgKey = user.imgUrlId, subKey = user.subUrlId
        )

        return try {
            val response = apiServices.getArticleIDs(cookie, params = signedParams)
            when (response.code) {
                0 -> {
                    // 提取 data 里的 articles 列表
                    val articleList = response.data?.articles

                    if (articleList.isNullOrEmpty() || articleList.any { it.id == 0L || it.publishTime == 0L }) {
                        return FetchResult.Error("文章数据不完整或为空，已取消存库")
                    }

                    if (articleList.isNotEmpty()) {
                        // 将 Ids 转换为 ArticleEntity
                        val entities = articleList.map { item ->
                            ArticleEntity(
                                articleId = item.id,
                                mid = mid,
                                publishTime = item.publishTime,
                                lastUpdated = System.currentTimeMillis()
                            )
                        }
                        // 存入本地数据库
                        articleDao.insertArticles(entities)
                    }
                    FetchResult.Success()
                }

                -400 -> FetchResult.Error("请求错误")
                else -> FetchResult.Error(response.message)
            }
        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "网络请求失败")
        }
    }
    fun getAllArticlesFlow() = articleDao.getAllArticles()
}
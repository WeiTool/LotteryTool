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
                    val articleList = response.data?.articles
                    if (articleList.isNullOrEmpty()) return FetchResult.Error("文章列表为空")

                    // 获取数据库中目前已有的最早文章时间
                    val minTimeInDb = articleDao.getMinPublishTime()

                    // 如果数据库非空，则过滤掉早于该时间的文章
                    val filteredList = if (minTimeInDb != null && minTimeInDb > 0) {
                        articleList.filter { it.publishTime >= minTimeInDb }
                    } else {
                        articleList
                    }

                    if (filteredList.isEmpty()) {
                        // 如果过滤后全没了，说明这批文章都比库里的旧
                        return FetchResult.Success() // 或者返回特定的提示
                    }

                    // 将过滤后的数据转换为 Entity 并存库
                    val entities = filteredList.map { item ->
                        ArticleEntity(
                            articleId = item.id,
                            mid = mid,
                            publishTime = item.publishTime,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }

                    articleDao.insertArticles(entities)
                    FetchResult.Success()
                }

                -400 -> FetchResult.Error("请求错误")
                else -> FetchResult.Error(response.message)
            }
        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "网络请求失败")
        }
    }

    // 提供给viewmodel
    fun getAllArticlesFlow() = articleDao.getAllArticles()
}
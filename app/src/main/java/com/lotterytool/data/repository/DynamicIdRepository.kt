package com.lotterytool.data.repository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.room.dynamicID.DynamicIdEntity
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.utils.FetchResult
import javax.inject.Inject

class DynamicIdRepository @Inject constructor(
    private val apiServices: ApiServices,
    private val dynamicIdsDao: DynamicIdsDao
) {
    companion object {
        private val DYNAMIC_REGEX_LEGACY = Regex("""https://www\.bilibili\.com/opus/(\d+)""")
        private val SPECIAL_DYNAMIC_REGEX_LEGACY = Regex("""https://t\.bilibili\.com/(\d+)""")
    }

    suspend fun extractDynamic(cookie: String, articleId: Long): FetchResult<Unit> {
        return try {
            val response = apiServices.getDynamicIDs(
                cookie = cookie,
                articleId = articleId
            )

            when (response.code) {
                0 -> {
                    // 1. 解析网络返回的新 ID 并去重
                    val entitiesToInsert = mutableListOf<DynamicIdEntity>()

                    response.data?.opus?.content?.paragraphs?.forEach { paraText ->
                        paraText.text.nodes.forEach { node ->
                            if (node.nodeType == 4) {
                                val url = node.link.link

                                // 匹配普通动态 ID
                                DYNAMIC_REGEX_LEGACY.find(url)?.groupValues?.getOrNull(1)
                                    ?.toLongOrNull()?.let { id ->
                                        entitiesToInsert.add(
                                            DynamicIdEntity(
                                                articleId,
                                                id,
                                                isSpecial = false
                                            )
                                        )
                                    }

                                // 匹配特殊动态 ID
                                SPECIAL_DYNAMIC_REGEX_LEGACY.find(url)?.groupValues?.getOrNull(1)
                                    ?.toLongOrNull()?.let { id ->
                                        entitiesToInsert.add(
                                            DynamicIdEntity(
                                                articleId,
                                                id,
                                                isSpecial = true
                                            )
                                        )
                                    }
                            }
                        }
                    }

                    // 【性能优化】早期退出：如果本次解析没有新 ID，直接返回成功
                    // 避免不必要的数据库读取
                    if (entitiesToInsert.isEmpty()) {
                        return FetchResult.Success()
                    }

                    dynamicIdsDao.insertIds(entitiesToInsert)

                    // 即使没有变化也返回成功，因为任务本身执行成功了
                    FetchResult.Success()
                }

                -352 -> FetchResult.Error("请求被风控 (-352)")
                -400 -> FetchResult.Error("请求错误 (-400)")
                -404 -> FetchResult.Error("啥都木有 (-404)")
                else -> FetchResult.Error(response.message)
            }
        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "专栏解析失败")
        }
    }
}
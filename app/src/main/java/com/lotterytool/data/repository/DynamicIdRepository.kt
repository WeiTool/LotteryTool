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
                    val entitiesToInsert = mutableListOf<DynamicIdEntity>()

                    // 每一层都用 ?: 或 orEmpty() 做空安全，
                    // 任何一层字段缺失只跳过当前元素，不抛异常、不中断整个遍历
                    response.data?.opus?.content?.paragraphs.orEmpty().forEach { paraText ->

                        // text / nodes 均已声明为可空，用 orEmpty() 安全降级为空列表
                        paraText.text?.nodes.orEmpty().forEach { node ->

                            if (node.nodeType == 4) {
                                // link?.link 双重空安全；为空则跳过本节点
                                val url = node.link?.link ?: return@forEach

                                // 匹配普通动态 ID
                                DYNAMIC_REGEX_LEGACY.find(url)?.groupValues?.getOrNull(1)
                                    ?.toLongOrNull()?.let { id ->
                                        entitiesToInsert.add(
                                            DynamicIdEntity(
                                                articleId = articleId,
                                                dynamicId = id,
                                                isSpecial = false
                                            )
                                        )
                                    }

                                // 匹配特殊动态 ID
                                SPECIAL_DYNAMIC_REGEX_LEGACY.find(url)?.groupValues?.getOrNull(1)
                                    ?.toLongOrNull()?.let { id ->
                                        entitiesToInsert.add(
                                            DynamicIdEntity(
                                                articleId = articleId,
                                                dynamicId = id,
                                                isSpecial = true
                                            )
                                        )
                                    }
                            }
                        }
                    }

                    if (entitiesToInsert.isEmpty()) {
                        return FetchResult.Success()
                    }

                    dynamicIdsDao.insertIds(entitiesToInsert)
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
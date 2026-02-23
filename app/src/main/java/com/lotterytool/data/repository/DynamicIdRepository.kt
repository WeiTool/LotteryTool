package com.lotterytool.data.repository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.data.room.dynamicID.DynamicIdsEntity
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
                    val newNormalSet = mutableSetOf<Long>()
                    val newSpecialSet = mutableSetOf<Long>()

                    response.data?.opus?.content?.paragraphs?.forEach { paraText ->
                        paraText.text.nodes.forEach { node ->
                            if (node.nodeType == 4) {
                                val url = node.link.link
                                DYNAMIC_REGEX_LEGACY.find(url)?.groupValues?.getOrNull(1)
                                    ?.toLongOrNull()?.let { id -> newNormalSet.add(id) }

                                SPECIAL_DYNAMIC_REGEX_LEGACY.find(url)?.groupValues?.getOrNull(1)
                                    ?.toLongOrNull()?.let { id -> newSpecialSet.add(id) }
                            }
                        }
                    }

                    // 【性能优化】早期退出：如果本次解析没有新 ID，直接返回成功
                    // 避免不必要的数据库读取
                    if (newNormalSet.isEmpty() && newSpecialSet.isEmpty()) {
                        return FetchResult.Success()
                    }

                    // 2. 获取本地已存在的记录
                    val existingEntity = dynamicIdsDao.getDynamicByArticleId(articleId)
                    val oldNormalSet = existingEntity?.normalDynamicIds?.toSet() ?: emptySet()
                    val oldSpecialSet = existingEntity?.specialDynamicIds?.toSet() ?: emptySet()

                    // 3. 合并新旧数据并自动去重（Set 特性）
                    val finalNormalSet = oldNormalSet + newNormalSet
                    val finalSpecialSet = oldSpecialSet + newSpecialSet

                    // 4. 【性能优化核心】比对集合内容是否发生任何改变
                    // 使用 Set 相等性比较，底层会比较元素内容而非引用
                    // 只有当真正有新增 ID 时，才执行数据库写入
                    val hasChanges = finalNormalSet != oldNormalSet || finalSpecialSet != oldSpecialSet


                    if (hasChanges) {
                        val newEntity = DynamicIdsEntity(
                            articleId = articleId,
                            normalDynamicIds = finalNormalSet.toList(),
                            specialDynamicIds = finalSpecialSet.toList()
                        )
                        // 使用 REPLACE 策略，只在有变化时触发 Room Flow 更新
                        dynamicIdsDao.insertDynamicIds(newEntity)
                    }

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
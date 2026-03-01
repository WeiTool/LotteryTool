package com.lotterytool.data.repository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.room.userDynamic.UserDynamicDao
import com.lotterytool.data.room.userDynamic.UserDynamicEntity
import com.lotterytool.utils.FetchResult
import kotlinx.coroutines.delay
import javax.inject.Inject

class UserDynamicRepository @Inject constructor(
    private val apiServices: ApiServices,
    private val userDynamicDao: UserDynamicDao,
) {

    /**
     * 增量同步：发现连续3条重复数据后停止
     */
    suspend fun fetchUserDynamic(cookie: String, mid: String): FetchResult<Unit> {
        return performFetch(cookie, mid, checkWatermark = true)
    }

    /**
     * 全量抓取：忽略水位线，直到 API 返回没有更多数据为止
     */
    suspend fun fetchUserDynamicAll(cookie: String, mid: String): FetchResult<Unit> {
        return performFetch(cookie, mid, checkWatermark = false)
    }

    /**
     * 核心获取逻辑
     * @param checkWatermark 是否检查本地水位线以提前结束循环
     */
    private suspend fun performFetch(
        cookie: String,
        mid: String,
        checkWatermark: Boolean
    ): FetchResult<Unit> {
        var currentOffset: String? = null
        // 获取本地数据库中该用户最新的 serviceId (仅在需要检查水位线时有用)
        val latestServiceId =
            if (checkWatermark) userDynamicDao.getLatestServiceId(mid.toLong()) else null

        return try {
            while (true) {
                val response = apiServices.getUserDynamic(
                    cookie = cookie,
                    mid = mid,
                    offset = currentOffset
                )

                if (response.code == 0 && response.data != null) {
                    val data = response.data
                    val entities = mutableListOf<UserDynamicEntity>()
                    var matchCount = 0
                    var shouldStop = false

                    for (item in data.items) {
                        val sId = item.idStr.toLong()

                        // 只有在启用 checkWatermark 时才进行重复校验
                        if (checkWatermark && latestServiceId != null && sId == latestServiceId) {
                            matchCount++
                            if (matchCount >= 3) {
                                shouldStop = true
                                break // 连续发现3条旧数据，标记停止
                            }
                            continue
                        }

                        // 如果这条动态没有 orig，说明它不是我们要找的“转发专栏产生的动态”，直接跳过
                        item.orig?.idStr?.toLongOrNull() ?: continue

                        entities.add(
                            UserDynamicEntity(
                                serviceId = sId,
                                dynamicId = item.orig.idStr.toLong(),
                                mid = mid.toLong(),
                                type = item.type,
                                offset = data.offset,
                                lastUpdated = System.currentTimeMillis() / 1000
                            )
                        )
                    }

                    if (entities.isNotEmpty()) {
                        userDynamicDao.insertAll(entities)
                    }

                    // 判定是否还需要翻页
                    // 1. 如果触发了水位线停止标记
                    // 2. 或者 API 明确表示没有更多数据
                    // 3. 或者 offset 为空
                    if (shouldStop || !data.hasMore || data.offset.isEmpty()) {
                        break
                    }

                    currentOffset = data.offset
                    delay(2000)
                } else {
                    return FetchResult.Error("API 错误: ${response.message}")
                }
            }
            FetchResult.Success(Unit)
        } catch (e: Exception) {
            val errorMsg = when {
                e is NullPointerException || e.message?.contains("null object") == true ->
                    "解析失败：发现异常动态数据，请检查是否有动态被删除"

                e.message?.contains("timeout") == true -> "网络连接超时，请重试"
                else -> "同步失败：${e.localizedMessage ?: "未知错误"}"
            }
            FetchResult.Error(errorMsg)
        }
    }
}
package com.lotterytool.data.repository

import com.google.gson.Gson
import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.models.ExtendData
import com.lotterytool.data.models.Item
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoEntity
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.utils.FetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DynamicInfoRepository @Inject constructor(
    private val apiServices: ApiServices,
    private val dynamicInfoDao: DynamicInfoDao,
    private val dynamicIdsDao: DynamicIdsDao,
    private val gson: Gson,
    private val officialRepository: OfficialRepository,
    private val officialInfoDao: OfficialInfoDao
) {
    suspend fun processAndStoreAllDynamics(
        cookie: String,
        articleId: Long,
        onProgress: suspend (current: Int, total: Int) -> Unit // 删除了 error: String?
    ) {
        val dynamicIdsEntity = dynamicIdsDao.getDynamicByArticleId(articleId) ?: return
        val normals = dynamicIdsEntity.normalDynamicIds
        val specials = dynamicIdsEntity.specialDynamicIds
        val total = normals.size + specials.size

        if (total == 0) return

        val taskSequence = sequence {
            normals.forEach { yield(it to false) }
            specials.forEach { yield(it to true) }
        }

        var currentIndex = 0

        for ((id, isSpecial) in taskSequence) {
            if (!currentCoroutineContext().isActive) break

            currentIndex++

            val existingInfo = dynamicInfoDao.getInfoById(id)
            if (existingInfo != null) {
                onProgress(currentIndex, total)
                continue
            }

            try {
                onProgress(currentIndex, total)
                // 内部 fetchAndProcessDynamic 依然会处理 FetchResult.Error 并存入本地 DB
                fetchAndProcessDynamic(cookie, id, articleId, isSpecial)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 不再通过回调传递错误字符串
            }

            if (currentIndex < total) {
                delay(3000)
            }
        }

        withContext(NonCancellable) {
            onProgress(total, total)
        }
    }

    suspend fun fetchAndProcessDynamic(
        cookie: String,
        dynamicId: Long,
        articleId: Long,
        isSpecial: Boolean
    ): FetchResult<Unit> = withContext(NonCancellable) {

        suspend fun saveErrorToDb(msg: String) {
            val errorEntity = DynamicInfoEntity(
                dynamicId = dynamicId,
                articleId = articleId,
                content = "解析失败",
                description = "错误原因: $msg",
                timestamp = System.currentTimeMillis() / 1000L,
                uid = 0, rid = 0,
                type = if (isSpecial) 2 else 1,
                errorMessage = msg
            )
            dynamicInfoDao.insertDynamicInfo(errorEntity)
        }

        return@withContext try {
            val response = apiServices.getDynamicInfo(cookie = cookie, dynamicId = dynamicId)

            // 1. API 响应状态检查
            if (response.code != 0) {
                val errorMsg = if (response.code == 4128001) "频率限制" else response.message
                saveErrorToDb(errorMsg)
                return@withContext FetchResult.Error(errorMsg)
            }

            val data = response.data?.firstCard
            val desc = data?.desc
            if (data == null || desc == null) {
                val errorMsg = "动态数据不存在或已删除 (Card/Desc Null)"
                saveErrorToDb(errorMsg)
                return@withContext FetchResult.Error(errorMsg)
            }

            // 2. 安全解析 ExtendJson (判定是否为官方抽奖)
            val isOfficial = if (!data.extendJson.isNullOrBlank()) {
                try {
                    val extendData = gson.fromJson(data.extendJson, ExtendData::class.java)
                    extendData?.lott?.lotteryId != null
                } catch (_: Exception) {
                    false
                }
            } else false

            // 3. 确定最终类型
            val finalType = when {
                isSpecial -> 2
                isOfficial -> 0
                else -> 1
            }

            // 4. 如果是官方动态，尝试同步抓取（此处需额外捕获异常防止干扰主流程）
            if (finalType == 0) {
                try {
                    officialRepository.fetchOfficial(cookie, dynamicId)
                } catch (_: Exception) {
                    // 记录日志但不中止，因为基础动态信息仍可保存
                }
            }

            // 5. 安全解析 SecondCard (核心内容)
            val item = if (!data.secondCard.isNullOrBlank()) {
                try {
                    gson.fromJson(data.secondCard, Item::class.java)
                } catch (_: Exception) {
                    null // 解析失败返回 null，后续使用默认值
                }
            } else null

            // 6. 构造实体并保存
            val entity = DynamicInfoEntity(
                dynamicId = dynamicId,
                articleId = articleId,
                content = item?.item?.content ?: "内容解析失败或为空",
                description = item?.item?.description ?: "描述解析失败或为空",
                timestamp = desc.timestamp ?: (System.currentTimeMillis() / 1000L),
                uid = desc.uid ?: 0L,
                rid = desc.rid ?: 0L,
                type = finalType,
                errorMessage = if (item == null && !data.secondCard.isNullOrBlank()) "JSON内容解析异常" else null
            )

            dynamicInfoDao.insertDynamicInfo(entity)
            FetchResult.Success(Unit)

        } catch (e: Exception) {
            val errorMsg = "系统崩溃: ${e.localizedMessage ?: "未知错误"}"
            saveErrorToDb(errorMsg)
            FetchResult.Error(errorMsg)
        }
    }

    suspend fun retrySingleDynamic(
        cookie: String,
        dynamicId: Long,
        articleId: Long,
        isSpecial: Boolean
    ): FetchResult<Unit> {
        // 逻辑与 fetchAndProcessDynamic 一致
        // 重新抓取成功后，Room 的 OnConflictStrategy.REPLACE 会自动覆盖掉之前的错误记录
        return fetchAndProcessDynamic(cookie, dynamicId, articleId, isSpecial)
    }

    suspend fun deleteDynamicLocally(dynamicId: Long, isOfficial: Boolean) {
        if (isOfficial) {
            officialInfoDao.deleteOfficialById(dynamicId)
        }
        dynamicInfoDao.deleteById(dynamicId)
    }
}

package com.lotterytool.data.repository

import com.google.gson.Gson
import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.models.ExtendData
import com.lotterytool.data.models.Item
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.data.room.dynamicInfo.DynamicDeleteDao
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
    private val dynamicDeleteDao: DynamicDeleteDao
) {
    /**
     * @param forceRefresh true 时跳过"已处理"检查，强制重新抓取所有动态（重试场景）。
     *                     false 时保持原有行为，已成功抓取的动态直接跳过。
     */
    suspend fun processAndStoreAllDynamics(
        cookie: String,
        articleId: Long,
        forceRefresh: Boolean = false,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ) {
        val allDynamicEntities = dynamicIdsDao.getIdsByArticleId(articleId)
        if (allDynamicEntities.isEmpty()) return

        val existingIdSet = dynamicIdsDao.getAllExistingIds().toSet()

        val filteredEntities = allDynamicEntities.filter { entity ->
            !existingIdSet.contains(entity.dynamicId)
        }

        if (filteredEntities.isEmpty()) {
            onProgress(allDynamicEntities.size, allDynamicEntities.size) // 直接完成
            return
        }

        val total = filteredEntities.size
        var currentIndex = 0

        for (entity in filteredEntities) {
            if (!currentCoroutineContext().isActive) break

            currentIndex++

            // forceRefresh=true 时跳过此检查，强制重新请求网络
            if (!forceRefresh) {
                val existingInfo = dynamicInfoDao.getInfoById(entity.dynamicId)
                if (existingInfo != null) {
                    onProgress(currentIndex, total)
                    continue
                }
            }

            try {
                onProgress(currentIndex, total)
                fetchAndProcessDynamic(
                    cookie = cookie,
                    dynamicId = entity.dynamicId,
                    articleId = articleId,
                    isSpecial = entity.isSpecial
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
                errorMessage = msg,
                normalTime = null,
                specialTime = null,
            )
            dynamicInfoDao.insertDynamicInfo(errorEntity)
        }

        return@withContext try {
            val response = apiServices.getDynamicInfo(cookie = cookie, dynamicId = dynamicId)

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

            val isOfficial = if (!data.extendJson.isNullOrBlank()) {
                try {
                    val extendData = gson.fromJson(data.extendJson, ExtendData::class.java)
                    extendData?.lott?.lotteryId != null
                } catch (_: Exception) {
                    false
                }
            } else false

            val finalType = when {
                isSpecial -> 2
                isOfficial -> 0
                else -> 1
            }

            if (finalType == 0) {
                try {
                    officialRepository.fetchOfficial(cookie, dynamicId)
                } catch (_: Exception) {
                }
            }

            val item = if (!data.secondCard.isNullOrBlank()) {
                try {
                    gson.fromJson(data.secondCard, Item::class.java)
                } catch (_: Exception) {
                    null
                }
            } else null

            var normalTime: Long? = null
            var specialTime: Long? = null

            when (finalType) {
                1 -> normalTime = parseTimeToSeconds(item?.item?.description ?: "")
                2 -> specialTime = parseTimeToSeconds(item?.item?.description ?: "")
            }

            val entity = DynamicInfoEntity(
                dynamicId = dynamicId,
                articleId = articleId,
                content = item?.item?.content ?: "内容解析失败或为空",
                description = item?.item?.description ?: "描述解析失败或为空",
                timestamp = desc.timestamp ?: (System.currentTimeMillis() / 1000L),
                uid = desc.uid ?: 0L,
                rid = desc.rid ?: 0L,
                type = finalType,
                normalTime = normalTime,
                specialTime = specialTime,
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

    private fun parseTimeToSeconds(text: String): Long? {
        val keywords = listOf("抽", "开奖", "截止", "公布")
        if (keywords.none { text.contains(it) }) return null

        val regex =
            "((\\d{4})[年./-])?(\\d{1,2})[月./-](\\d{1,2})[日号]?(\\s*(\\d{1,2})[:点时](\\d{2})?)?".toRegex()
        val matchResult = regex.findAll(text).lastOrNull() ?: return null
        val groups = matchResult.groupValues
        val calendar = java.util.Calendar.getInstance()

        val year =
            if (groups[2].isNotEmpty()) groups[2].toInt() else calendar.get(java.util.Calendar.YEAR)
        val month = groups[3].toInt() - 1
        val day = groups[4].toInt()
        val hour = if (groups[6].isNotEmpty()) groups[6].toInt() else 0

        calendar.set(year, month, day, hour, 0, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        return calendar.timeInMillis / 1000L
    }

    suspend fun retrySingleDynamic(
        cookie: String,
        dynamicId: Long,
        articleId: Long,
        isSpecial: Boolean
    ): FetchResult<Unit> {
        return fetchAndProcessDynamic(cookie, dynamicId, articleId, isSpecial)
    }

    suspend fun deleteDynamicLocally(dynamicId: Long) {
        dynamicDeleteDao.deleteFullDynamicLocally(dynamicId)
    }

    suspend fun getSuccessfulDynamicIds(articleId: Long) =
        dynamicInfoDao.getSuccessfulDynamicIds(articleId)

    suspend fun getInfoById(dynamicId: Long) = dynamicInfoDao.getInfoById(dynamicId)
}
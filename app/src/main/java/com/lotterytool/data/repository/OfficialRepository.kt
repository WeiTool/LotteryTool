package com.lotterytool.data.repository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.officialInfo.OfficialInfoEntity
import com.lotterytool.utils.FetchResult
import javax.inject.Inject

class OfficialRepository @Inject constructor(
    private val apiServices: ApiServices,
    private val officialInfoDao: OfficialInfoDao,
) {
    suspend fun fetchOfficial(cookie: String, dynamicId: Long): FetchResult<Unit>? {
        return try {
            val response = apiServices.getOfficialDynamic(cookie = cookie, dynamicId = dynamicId)
            when (response.code) {
                0 -> {
                    val officialInfo = response.data
                    val time = officialInfo?.time

                    if (officialInfo != null && time != null) {
                        val entity = OfficialInfoEntity(
                            dynamicId = dynamicId,
                            time = time,
                            firstPrize = officialInfo.firstPrize ?: 0,
                            secondPrize = officialInfo.secondPrize ?: 0,
                            thirdPrize = officialInfo.thirdPrize ?: 0,
                            firstPrizeCmt = officialInfo.firstPrizeCmt ?: "无描述",
                            secondPrizeCmt = officialInfo.secondPrizeCmt ?: "无描述",
                            thirdPrizeCmt = officialInfo.thirdPrizeCmt ?: "无描述",
                            errorMessage = null,
                            isError = false
                        )
                        officialInfoDao.insertOfficialInfo(entity)
                        FetchResult.Success()
                    } else {
                        // 如果 officialInfo 或 time 为空，走错误逻辑
                        val msg = if (officialInfo == null) "数据为空" else "缺少关键字段: time"
                        saveErrorToDb(dynamicId, msg)
                        FetchResult.Error(msg)
                    }
                }

                -9999 -> {
                    val msg = "服务系统错误"
                    saveErrorToDb(dynamicId, msg)
                    FetchResult.Error(msg)
                }

                else -> {
                    val msg = response.message
                    saveErrorToDb(dynamicId, msg)
                    FetchResult.Error(msg)
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "网络请求失败"
            saveErrorToDb(dynamicId, errorMsg)
            FetchResult.Error(errorMsg)
        }
    }

    private suspend fun saveErrorToDb(id: Long, msg: String) {
        officialInfoDao.insertOfficialInfo(
            OfficialInfoEntity(dynamicId = id, errorMessage = msg, isError = true)
        )
    }
}
package com.lotterytool.data.room.dynamicInfo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DynamicInfoDao {
    // 存入数据库
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDynamicInfo(info: DynamicInfoEntity)

    // 获取某个id信息
    @Query("SELECT * FROM dynamic_info WHERE dynamicId = :dynamicId")
    suspend fun getInfoById(dynamicId: Long): DynamicInfoEntity?

    // 获取没有错误的动态
    @Query("SELECT dynamicId FROM dynamic_info WHERE articleId = :articleId AND (errorMessage IS NULL OR errorMessage = '')")
    suspend fun getSuccessfulDynamicIds(articleId: Long): List<Long>

    @Query("DELETE FROM dynamic_info WHERE dynamicId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

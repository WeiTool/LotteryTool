package com.lotterytool.data.room.officialInfo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfficialInfoDao {
    // 存入数据库
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfficialInfo(officialDynamic: OfficialInfoEntity)

    // 删除
    @Query("DELETE FROM official_info WHERE dynamicId IN (:dynamicIds)")
    suspend fun deleteByDynamicIds(dynamicIds: List<Long>)

    // 根据 dynamicId 获取单条官方抽奖信息的实时流，供详情对话框使用。
    @Query("SELECT * FROM official_info WHERE dynamicId = :dynamicId")
    fun getOfficialByIdFlow(dynamicId: Long): Flow<OfficialInfoEntity?>

}
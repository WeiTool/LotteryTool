package com.lotterytool.data.room.userDynamic

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDynamicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<UserDynamicEntity>)

    /**
     * 获取用户最新的一个转发动态 ID，用于增量同步判断
     */
    @Query("SELECT serviceId FROM user_dynamic WHERE mid = :mid ORDER BY serviceId DESC LIMIT 1")
    suspend fun getLatestServiceId(mid: Long): Long?

    /**
     * 根据原动态 ID 列表，检查哪些动态已经被你转发过了
     */
    @Query("SELECT serviceId FROM user_dynamic WHERE dynamicId = :dynamicId LIMIT 1")
    suspend fun getServiceIdByOriginalId(dynamicId: Long): Long?

    // 根据原动态 ID 列表获取对应的 serviceId 列表
    @Query("SELECT serviceId FROM user_dynamic WHERE dynamicId IN (:dynamicIds)")
    suspend fun getServiceIdsByOriginalIds(dynamicIds: List<Long>): List<Long>

    // 根据 serviceId 删除
    @Query("DELETE FROM user_dynamic WHERE serviceId = :serviceId")
    suspend fun deleteByServiceId(serviceId: Long)
}
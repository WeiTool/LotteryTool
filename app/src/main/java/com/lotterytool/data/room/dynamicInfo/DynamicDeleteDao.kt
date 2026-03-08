package com.lotterytool.data.room.dynamicInfo

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface DynamicDeleteDao {

    @Transaction
    suspend fun deleteFullDynamicLocally(dynamicId: Long) {
        // 1. 删除基础信息 (dynamic_info)
        deleteDynamicInfo(dynamicId)

        // 2. 删除动作结果 (action_info)
        deleteActionInfo(dynamicId)

        // 3. 删除官方抽奖详情 (official_info)
        deleteOfficialInfo(dynamicId)

        // 4. 删除 ID 映射关系 (dynamic_ids)
        deleteDynamicIdMapping(dynamicId)

        // 5. 删除用户动态同步记录 (user_dynamic)
        deleteUserDynamic(dynamicId)
    }

    @Query("DELETE FROM dynamic_info WHERE dynamicId = :dynamicId")
    suspend fun deleteDynamicInfo(dynamicId: Long)

    @Query("DELETE FROM action_info WHERE dynamicId = :dynamicId")
    suspend fun deleteActionInfo(dynamicId: Long)

    @Query("DELETE FROM official_info WHERE dynamicId = :dynamicId")
    suspend fun deleteOfficialInfo(dynamicId: Long)

    @Query("DELETE FROM dynamic_ids WHERE dynamicId = :dynamicId")
    suspend fun deleteDynamicIdMapping(dynamicId: Long)

    @Query("DELETE FROM user_dynamic WHERE dynamicId = :dynamicId")
    suspend fun deleteUserDynamic(dynamicId: Long)
}
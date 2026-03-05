package com.lotterytool.data.room.action

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionDao {
    // 存入数据库
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: ActionEntity)

    // 获取已经处理的动态
    @Query("SELECT * FROM action_info WHERE dynamicId = :dynamicId")
    suspend fun getActionById(dynamicId: Long): ActionEntity?

    @Query("DELETE FROM action_info WHERE dynamicId IN (:ids)")
    suspend fun deleteByDynamicIds(ids: List<Long>)

}
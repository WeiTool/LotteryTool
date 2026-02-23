package com.lotterytool.data.room.dynamicID

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DynamicIdsDao {
    // 存入数据库
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDynamicIds(entity: DynamicIdsEntity)

    // 获取本地已存在的记录
    @Query("SELECT * FROM dynamic_ids WHERE articleId = :articleId")
    suspend fun getDynamicByArticleId(articleId: Long): DynamicIdsEntity?

    // 删除动态id
    @Query("DELETE FROM dynamic_ids WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: Long)

}
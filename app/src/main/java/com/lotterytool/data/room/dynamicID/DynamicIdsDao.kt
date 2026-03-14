package com.lotterytool.data.room.dynamicID

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DynamicIdsDao {
    // 批量插入，如果已存在则忽略（符合 Set 的去重逻辑）
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIds(entities: List<DynamicIdEntity>)

    // 获取某篇文章下的所有 ID
    @Query("SELECT * FROM dynamic_ids WHERE articleId = :articleId")
    suspend fun getIdsByArticleId(articleId: Long): List<DynamicIdEntity>

    @Query("SELECT dynamicId FROM dynamic_ids")
    suspend fun getAllExistingIds(): List<Long>

    @Query("DELETE FROM dynamic_ids WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: Long)
}
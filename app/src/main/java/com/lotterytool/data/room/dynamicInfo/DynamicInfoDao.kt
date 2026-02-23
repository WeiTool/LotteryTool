package com.lotterytool.data.room.dynamicInfo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DynamicInfoDao {
    // 存入数据库
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDynamicInfo(info: DynamicInfoEntity)

    // 获取某个id信息
    @Query("SELECT * FROM dynamic_info WHERE dynamicId = :dynamicId")
    suspend fun getInfoById(dynamicId: Long): DynamicInfoEntity?

    // 查询有空值的 type
    @Query("""
        SELECT articleId 
        FROM dynamic_info 
        GROUP BY articleId 
        HAVING 
            SUM(CASE WHEN type = 0 THEN 1 ELSE 0 END) = 0 OR 
            SUM(CASE WHEN type = 1 THEN 1 ELSE 0 END) = 0 OR 
            SUM(CASE WHEN type = 2 THEN 1 ELSE 0 END) = 0
    """)
    fun getArticlesWithMissingTypes(): Flow<List<Long>>

    data class DynamicCountResult(
        val officialCount: Int,
        val normalCount: Int,
        val specialCount: Int
    )

    // 获取每个动态类型的数量
    @Query("""
        SELECT 
            SUM(CASE WHEN type = 0 THEN 1 ELSE 0 END) as officialCount,
            SUM(CASE WHEN type = 1 THEN 1 ELSE 0 END) as normalCount,
            SUM(CASE WHEN type = 2 THEN 1 ELSE 0 END) as specialCount
        FROM dynamic_info 
        WHERE articleId = :articleId
    """)
    fun getDynamicCountsFlow(articleId: Long): Flow<DynamicCountResult?>

    // 获取所有动态
    @Query("SELECT * FROM dynamic_info_detail WHERE articleId = :articleId AND type = :type ORDER BY timestamp DESC")
    fun getInfoByArticleAndType(articleId: Long, type: Int): Flow<List<DynamicInfoDetail>>

    // 获取所有解析错误的动态
    @Query("SELECT * FROM dynamic_info WHERE articleId = :articleId AND errorMessage IS NOT NULL")
    fun getErroredDynamics(articleId: Long): Flow<List<DynamicInfoEntity>>

    // 用于监听专栏任务失败的 Flow
    @Query("SELECT DISTINCT articleId FROM dynamic_info WHERE errorMessage IS NOT NULL")
    fun getErroredArticleIds(): Flow<List<Long>>

    // 删除某个id（用于删除某个卡片）
    @Query("DELETE FROM dynamic_info WHERE dynamicId = :dynamicId")
    suspend fun deleteById(dynamicId: Long)

    // 删除某个专栏里面所有动态信息
    @Query("DELETE FROM dynamic_info WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: Long)

    // 获取某个专栏的动态ID列表
    @Query("SELECT dynamicId FROM dynamic_info WHERE articleId = :articleId")
    suspend fun getDynamicIdsByArticleId(articleId: Long): List<Long>

    // 获取没有错误的动态
    @Query("SELECT dynamicId FROM dynamic_info WHERE articleId = :articleId AND (errorMessage IS NULL OR errorMessage = '')")
    suspend fun getSuccessfulDynamicIds(articleId: Long): List<Long>

    // 获取所有某个专栏里所有id的动态信息
    @Query("SELECT * FROM dynamic_info_detail WHERE articleId = :articleId ORDER BY timestamp DESC")
    fun getAllInfoByArticle(articleId: Long): Flow<List<DynamicInfoDetail>>
}

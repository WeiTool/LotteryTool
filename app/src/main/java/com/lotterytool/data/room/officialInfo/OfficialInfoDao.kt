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
    @Query("DELETE FROM official_info WHERE dynamicId = :dynamicId")
    suspend fun deleteOfficialById(dynamicId: Long)

    // 删除
    @Query("DELETE FROM official_info WHERE dynamicId IN (:dynamicIds)")
    suspend fun deleteByDynamicIds(dynamicIds: List<Long>)

    /**
     * 根据 dynamicId 获取单条官方抽奖信息的实时流，供详情对话框使用。
     */
    @Query("SELECT * FROM official_info WHERE dynamicId = :dynamicId")
    fun getOfficialByIdFlow(dynamicId: Long): Flow<OfficialInfoEntity?>

    /**
     * 获取所有包含已过期官方动态的文章ID列表。
     *
     * 1. JOIN 连接 official_info 和 dynamic_info 表，通过 dynamicId。
     * 2. WHERE 筛选出 official_info 中 time (秒) 小于当前时间戳的记录。
     * 3. SELECT DISTINCT 只选择不重复的 articleId。
     */
    @Query(
        """
        SELECT DISTINCT T2.articleId
        FROM official_info AS T1
        INNER JOIN dynamic_info AS T2 ON T1.dynamicId = T2.dynamicId
        WHERE T1.time < strftime('%s', 'now')
    """
    )
    fun getExpiredArticleIds(): Flow<List<Long>>

    /**
     * 查询那些包含"官方信息缺失"动态的文章 ID。
     * 1. 遍历 dynamic_info。
     * 2. LEFT JOIN official_info。
     * 3. 筛选 T2.dynamicId IS NULL（说明完全没抓取过）
     * 或者 T2.isError = 1（说明抓取过但失败了，记录为空实体）。
     */
    @Query("""
    SELECT DISTINCT T1.articleId
    FROM dynamic_info AS T1
    LEFT JOIN official_info AS T2 ON T1.dynamicId = T2.dynamicId
    WHERE T1.type = 0  -- 只检查官方类型的动态
      AND (
          T2.dynamicId IS NULL        -- 完全没记录
          OR T2.isError = 1           -- 明确记录了错误
          OR T2.time = 0              -- 有记录但时间戳非法（默认值）
          OR T2.firstPrizeCmt = ''    -- 或者检查关键文本是否为空
      )
""")
    fun getArticlesWithMissingOfficialInfo(): Flow<List<Long>>

    @Query(
        """
    SELECT EXISTS(
        SELECT 1 
        FROM dynamic_info AS T1
        LEFT JOIN official_info AS T2 ON T1.dynamicId = T2.dynamicId
        WHERE T1.articleId = :articleId 
          AND T1.type = 0
          AND (T2.dynamicId IS NULL OR T2.isError = 1)
    )
"""
    )
    fun hasMissingOfficialInfo(articleId: Long): Flow<Boolean>
}
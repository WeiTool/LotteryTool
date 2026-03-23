package com.lotterytool.data.room.view.viewDao

import androidx.room.Dao
import androidx.room.Query
import com.lotterytool.data.room.view.DynamicInfoDetail
import kotlinx.coroutines.flow.Flow

@Dao
interface DynamicInfoDetailDao {

    /**
     * 获取指定文章下某类型的所有动态，按时间降序。。
     */
    @Query("""
        SELECT * FROM dynamic_info_detail
        WHERE articleId = :articleId AND type = :type
        ORDER BY timestamp DESC
    """)
    fun getInfoByArticleAndType(articleId: Long, type: Int): Flow<List<DynamicInfoDetail>>

    /**
     * 一次性查出指定专栏下所有动态及其关联数据（用于批量删除场景）。
     */
    @Query("SELECT * FROM dynamic_info_detail WHERE articleId = :articleId")
    suspend fun getDetailsByArticleId(articleId: Long): List<DynamicInfoDetail>

    /**
     * 聚合查询所有文章的状态标志位，供文章列表图标使用。
     *
     * 修复：移除外层对 tasks 表的冗余 JOIN（视图内部已包含 task_state），
     * 统一通过视图列 d.task_state / d.task_error_message 读取任务状态，
     * 避免与外层别名冲突。
     */
    @Query("""
        SELECT
            a.articleId AS articleId,

            MAX(CASE
                WHEN d.task_state = 'FAILED' OR d.task_error_message IS NOT NULL
                THEN 1 ELSE 0 END) AS hasError,

            MAX(CASE
                WHEN (d.official_time > 0  AND d.official_time  < :currentTimeSeconds) OR
                     (d.normalTime    > 0  AND d.normalTime     < :currentTimeSeconds) OR
                     (d.specialTime   > 0  AND d.specialTime    < :currentTimeSeconds)
                THEN 1 ELSE 0 END) AS isExpired,

            MAX(CASE WHEN
                (d.repostResult IS NOT NULL AND d.repostResult != '成功') OR
                (d.likeResult   IS NOT NULL AND d.likeResult   != '成功') OR
                (d.replyResult  IS NOT NULL AND d.replyResult  != '成功') OR
                (d.followResult IS NOT NULL AND d.followResult != '成功'
                    AND d.followResult != '已经关注用户，无法重复关注')
                THEN 1 ELSE 0 END) AS hasActionError,

            CASE WHEN
                MAX(CASE WHEN d.task_state IN ('ACTION_PHASE', 'SUCCESS') THEN 1 ELSE 0 END) = 1
                AND (
                    SUM(CASE WHEN d.type = 0 THEN 1 ELSE 0 END) = 0 OR
                    SUM(CASE WHEN d.type = 1 THEN 1 ELSE 0 END) = 0 OR
                    SUM(CASE WHEN d.type = 2 THEN 1 ELSE 0 END) = 0
                )
            THEN 1 ELSE 0 END AS hasMissingTypes,

            MAX(CASE WHEN d.type = 0 AND (
                (d.official_time IS NOT NULL AND d.official_is_error IS NULL) OR
                (d.official_time IS NULL     AND d.official_is_error IS NOT NULL) OR
                d.official_is_error = 1
            ) THEN 1 ELSE 0 END) AS hasMissingOfficialTime,

            MAX(CASE WHEN d.task_state = 'SUCCESS' THEN 1 ELSE 0 END) AS isProcessed

        FROM article AS a
        LEFT JOIN dynamic_info_detail AS d ON a.articleId = d.articleId
        GROUP BY a.articleId
    """)
    fun getArticleStatusRows(currentTimeSeconds: Long): Flow<List<ArticleStatusRow>>

    /**
     * 聚合查询单篇文章内各类型动态的图标标志位，供动态列表页 SummaryCard 使用。
     */
    @Query("""
        SELECT
            COUNT(DISTINCT CASE WHEN type = 0 THEN dynamicId END) AS countType0,
            COUNT(DISTINCT CASE WHEN type = 1 THEN dynamicId END) AS countType1,
            COUNT(DISTINCT CASE WHEN type = 2 THEN dynamicId END) AS countType2,

            MAX(CASE WHEN type = 0 AND official_is_error = 1
                THEN 1 ELSE 0 END) AS hasParseErrorType0,
            MAX(CASE WHEN type = 1 AND error_message IS NOT NULL
                THEN 1 ELSE 0 END) AS hasParseErrorType1,
            MAX(CASE WHEN type = 2 AND error_message IS NOT NULL
                THEN 1 ELSE 0 END) AS hasParseErrorType2,

            MAX(CASE WHEN type = 0 AND official_time > 0 AND official_time < :currentTimeSeconds
                THEN 1 ELSE 0 END) AS hasExpiredType0,
            MAX(CASE WHEN type = 1 AND normalTime   > 0 AND normalTime    < :currentTimeSeconds
                THEN 1 ELSE 0 END) AS hasExpiredType1,
            MAX(CASE WHEN type = 2 AND specialTime  > 0 AND specialTime   < :currentTimeSeconds
                THEN 1 ELSE 0 END) AS hasExpiredType2,

            MAX(CASE WHEN type = 0 AND (
                (repostResult IS NOT NULL AND repostResult != '成功') OR
                (likeResult   IS NOT NULL AND likeResult   != '成功') OR
                (replyResult  IS NOT NULL AND replyResult  != '成功') OR
                (followResult IS NOT NULL AND followResult != '成功'
                    AND followResult != '已经关注用户，无法重复关注')
            ) THEN 1 ELSE 0 END) AS hasActionErrorType0,

            MAX(CASE WHEN type = 1 AND (
                (repostResult IS NOT NULL AND repostResult != '成功') OR
                (likeResult   IS NOT NULL AND likeResult   != '成功') OR
                (replyResult  IS NOT NULL AND replyResult  != '成功') OR
                (followResult IS NOT NULL AND followResult != '成功'
                    AND followResult != '已经关注用户，无法重复关注')
            ) THEN 1 ELSE 0 END) AS hasActionErrorType1,

            MAX(CASE WHEN type = 2 AND (
                (repostResult IS NOT NULL AND repostResult != '成功') OR
                (likeResult   IS NOT NULL AND likeResult   != '成功') OR
                (replyResult  IS NOT NULL AND replyResult  != '成功') OR
                (followResult IS NOT NULL AND followResult != '成功'
                    AND followResult != '已经关注用户，无法重复关注')
            ) THEN 1 ELSE 0 END) AS hasActionErrorType2,

            MAX(CASE WHEN type = 0 AND (
                (official_time IS NOT NULL AND official_is_error IS NULL) OR
                (official_time IS NULL     AND official_is_error IS NOT NULL) OR
                official_is_error = 1
            ) THEN 1 ELSE 0 END) AS hasMissingOfficial

        FROM dynamic_info_detail
        WHERE articleId = :articleId
        GROUP BY articleId
    """)
    fun getIconStatusRow(articleId: Long, currentTimeSeconds: Long): Flow<ListIconStatusRow?>

    @Query("SELECT DISTINCT articleId FROM dynamic_info_detail WHERE task_state = 'SUCCESS'")
    suspend fun getProcessedArticleIds(): List<Long>
}
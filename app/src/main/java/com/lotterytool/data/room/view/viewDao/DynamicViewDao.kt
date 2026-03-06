package com.lotterytool.data.room.view.viewDao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DynamicViewDao {

    /**
     * 通过条件聚合一次性计算所有状态标记，替代原先 6 条独立查询。
     *
     * 各列说明：
     *  hasError               — 任务 FAILED 或动态解析出错
     *  isExpired              — 存在已超过当前时间的官方动态
     *  hasActionError         — 转发/点赞/回复/关注中有失败结果
     *  hasMissingTypes        — 任务处于 ACTION_PHASE 但缺少 type 0/1/2 中的某种
     *  hasMissingOfficialTime — 任务处于 ACTION_PHASE 但官方时间为空
     *  isProcessed            — 任务已 SUCCESS
     */
    @Query("""
    SELECT
        articleId,
        MAX(CASE WHEN taskState = 'FAILED' OR infoErrorMessage IS NOT NULL
            THEN 1 ELSE 0 END) AS hasError,
        MAX(CASE WHEN officialTime > 0 AND officialTime < :currentTimeSeconds
            THEN 1 ELSE 0 END) AS isExpired,
        MAX(CASE WHEN
            (repostResult IS NOT NULL AND repostResult != '成功') OR
            (likeResult   IS NOT NULL AND likeResult   != '成功') OR
            (replyResult  IS NOT NULL AND replyResult  != '成功') OR
            (followResult IS NOT NULL AND followResult != '成功'
                AND followResult != '已经关注用户，无法重复关注')
            THEN 1 ELSE 0 END) AS hasActionError,
        CASE WHEN
            MAX(CASE WHEN taskState IN ('ACTION_PHASE', 'SUCCESS') THEN 1 ELSE 0 END) = 1 AND (
                SUM(CASE WHEN type = 0 THEN 1 ELSE 0 END) = 0 OR
                SUM(CASE WHEN type = 1 THEN 1 ELSE 0 END) = 0 OR
                SUM(CASE WHEN type = 2 THEN 1 ELSE 0 END) = 0
            )
        THEN 1 ELSE 0 END AS hasMissingTypes,
        MAX(CASE WHEN type = 0 AND (
            (officialTime IS NOT NULL AND officialHasError IS NULL) OR
            (officialTime IS NULL     AND officialHasError IS NOT NULL) OR
            officialHasError = 1
        ) THEN 1 ELSE 0 END) AS hasMissingOfficialTime,
        
        MAX(CASE WHEN taskState = 'SUCCESS'
            THEN 1 ELSE 0 END) AS isProcessed
    FROM dynamic_view
    GROUP BY articleId
""")
    fun getArticleStatusRows(currentTimeSeconds: Long): Flow<List<ArticleStatusRow>>


    /**
     * 用条件聚合一次性计算所有图标标志位，替代原先多条独立查询。
     *
     * 各列说明：
     *  countType0/1/2        — 各类型动态数量
     *  hasParseErrorType0    — type=0 行中存在 officialHasError=1
     *  hasParseErrorType1/2  — type=1/2 行中存在 dynamic_info 解析错误
     *  hasExpired            — 存在 officialTime 早于当前时间的动态
     *  hasActionErrorType*   — 对应类型存在转发/点赞/回复/关注失败
     *  hasMissingOfficial    — type=0 行中 officialTime 与 officialHasError
     *                          有且仅有一项有值（不一致），或 officialHasError=1
     */
    @Query("""
        SELECT
            SUM(CASE WHEN type = 0 THEN 1 ELSE 0 END) AS countType0,
            SUM(CASE WHEN type = 1 THEN 1 ELSE 0 END) AS countType1,
            SUM(CASE WHEN type = 2 THEN 1 ELSE 0 END) AS countType2,

            MAX(CASE WHEN type = 0 AND officialHasError = 1
                THEN 1 ELSE 0 END) AS hasParseErrorType0,
            MAX(CASE WHEN type = 1 AND infoErrorMessage IS NOT NULL
                THEN 1 ELSE 0 END) AS hasParseErrorType1,
            MAX(CASE WHEN type = 2 AND infoErrorMessage IS NOT NULL
                THEN 1 ELSE 0 END) AS hasParseErrorType2,

            MAX(CASE WHEN officialTime > 0 AND officialTime < :currentTimeSeconds
                THEN 1 ELSE 0 END) AS hasExpired,

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
                (officialTime IS NOT NULL AND officialHasError IS NULL) OR
                (officialTime IS NULL     AND officialHasError IS NOT NULL) OR
                officialHasError = 1
            ) THEN 1 ELSE 0 END) AS hasMissingOfficial

        FROM dynamic_view
        WHERE articleId = :articleId
        GROUP BY articleId
    """)
    fun getIconStatusRow(articleId: Long, currentTimeSeconds: Long): Flow<ListIconStatusRow?>
}


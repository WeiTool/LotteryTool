package com.lotterytool.data.room.dynamicInfo

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

/**
 * 一个数据库视图，用于将 DynamicInfoEntity 和 OfficialInfoEntity 连接起来。
 * 这使得我们可以用一个简单的查询同时获取动态的基本信息和官方动态的开奖时间。
 */
@DatabaseView("""
    SELECT
        d.*,
        o.time AS official_time,
        o.isError AS official_isError,
        a.repostResult,
        a.likeResult,
        a.replyResult,
        a.followResult
    FROM dynamic_info AS d
    LEFT JOIN official_info AS o ON d.dynamicId = o.dynamicId
    LEFT JOIN action_info AS a ON d.dynamicId = a.dynamicId
""", viewName = "dynamic_info_detail")
data class DynamicInfoDetail(
    val dynamicId: Long,
    val articleId: Long,
    val content: String,
    val description: String,
    val timestamp: Long,
    val uid: Long,
    val rid: Long,
    val type: Int,
    val errorMessage: String? = null,

    @ColumnInfo(name = "official_time")
    val officialTime: Long?,
    @ColumnInfo(name = "official_isError")
    val officialIsError: Boolean?,

    // --- 新增字段 ---
    val repostResult: String? = null,
    val likeResult: String? = null,
    val replyResult: String? = null,
    val followResult: String? = null
)

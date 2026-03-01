package com.lotterytool.data.room.dynamicInfo

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

@DatabaseView("""
    SELECT
        d.*,
        o.time AS official_time,
        o.isError AS official_isError,
        a.repostResult,
        a.likeResult,
        a.replyResult,
        a.followResult,
        u.serviceId AS service_id
    FROM dynamic_info AS d
    LEFT JOIN official_info AS o ON d.dynamicId = o.dynamicId
    LEFT JOIN action_info AS a ON d.dynamicId = a.dynamicId
    LEFT JOIN user_dynamic AS u ON d.dynamicId = u.dynamicId
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

    val repostResult: String? = null,
    val likeResult: String? = null,
    val replyResult: String? = null,
    val followResult: String? = null,

    @ColumnInfo(name = "service_id")
    val serviceId: Long? = null
)

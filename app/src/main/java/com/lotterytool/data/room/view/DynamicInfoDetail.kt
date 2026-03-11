package com.lotterytool.data.room.view

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import com.lotterytool.data.room.task.TaskState

@DatabaseView(
    viewName = "dynamic_info_detail",
    value = """
        SELECT
            d.dynamicId,
            d.articleId,
            d.content,
            d.description,
            d.timestamp,
            d.uid,
            d.rid,
            d.type,
            d.errorMessage          AS error_message,
            d.specialTime,        
            d.normalTime,           
            di.isSpecial,
            ar.mid                  AS article_mid,
            ar.publishTime          AS article_publish_time,
            ar.lastUpdated          AS article_last_updated,
            t.state                 AS task_state,
            t.currentProgress       AS task_current_progress,
            t.totalProgress         AS task_total_progress,
            t.errorMessage          AS task_error_message,
            t.lastUpdateTime        AS task_last_update_time,
            o.time                  AS official_time,
            o.isError               AS official_is_error,
            o.errorMessage          AS official_error_message,
            o.firstPrize            AS official_first_prize,
            o.firstPrizeCmt         AS official_first_prize_cmt,
            o.secondPrize           AS official_second_prize,
            o.secondPrizeCmt        AS official_second_prize_cmt,
            o.thirdPrize            AS official_third_prize,
            o.thirdPrizeCmt         AS official_third_prize_cmt,
            a.repostResult,
            a.likeResult,
            a.replyResult,
            a.followResult,
            u.serviceId             AS service_id,
            u.mid                   AS user_mid,
            u.type                  AS user_dynamic_type,
            u.`offset`              AS user_dynamic_offset,
            u.lastUpdated           AS user_dynamic_last_updated

        FROM dynamic_info AS d
        LEFT JOIN dynamic_ids   AS di ON  d.dynamicId  = di.dynamicId
        LEFT JOIN article       AS ar ON  d.articleId  = ar.articleId
        LEFT JOIN tasks         AS t  ON  d.articleId  = t.articleId
        LEFT JOIN official_info AS o  ON  d.dynamicId  = o.dynamicId
        LEFT JOIN action_info   AS a  ON  d.dynamicId  = a.dynamicId
        LEFT JOIN user_dynamic  AS u  ON  d.dynamicId  = u.dynamicId
    """
)
data class DynamicInfoDetail(

    // ── dynamic_info ──────────────────────────────────────────────────────────
    val dynamicId: Long,
    val articleId: Long,
    val content: String,
    val description: String,
    val timestamp: Long,
    val uid: Long,
    val rid: Long,
    val type: Int,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    val specialTime: Long? = null,
    val normalTime: Long? = null,

    // ── dynamic_ids ───────────────────────────────────────────────────────────
    val isSpecial: Boolean? = null,

    // ── article ───────────────────────────────────────────────────────────────
    @ColumnInfo(name = "article_mid")
    val articleMid: Long? = null,
    @ColumnInfo(name = "article_publish_time")
    val articlePublishTime: Long? = null,
    @ColumnInfo(name = "article_last_updated")
    val articleLastUpdated: Long? = null,

    // ── tasks ─────────────────────────────────────────────────────────────────
    @ColumnInfo(name = "task_state")
    val taskState: TaskState? = null,
    @ColumnInfo(name = "task_current_progress")
    val taskCurrentProgress: Int? = null,
    @ColumnInfo(name = "task_total_progress")
    val taskTotalProgress: Int? = null,
    @ColumnInfo(name = "task_error_message")
    val taskErrorMessage: String? = null,
    @ColumnInfo(name = "task_last_update_time")
    val taskLastUpdateTime: Long? = null,

    // ── official_info ─────────────────────────────────────────────────────────
    @ColumnInfo(name = "official_time")
    val officialTime: Long? = null,
    @ColumnInfo(name = "official_is_error")
    val officialIsError: Boolean? = null,
    @ColumnInfo(name = "official_error_message")
    val officialErrorMessage: String? = null,
    @ColumnInfo(name = "official_first_prize")
    val officialFirstPrize: Int? = null,
    @ColumnInfo(name = "official_first_prize_cmt")
    val officialFirstPrizeCmt: String? = null,
    @ColumnInfo(name = "official_second_prize")
    val officialSecondPrize: Int? = null,
    @ColumnInfo(name = "official_second_prize_cmt")
    val officialSecondPrizeCmt: String? = null,
    @ColumnInfo(name = "official_third_prize")
    val officialThirdPrize: Int? = null,
    @ColumnInfo(name = "official_third_prize_cmt")
    val officialThirdPrizeCmt: String? = null,

    // ── action_info ───────────────────────────────────────────────────────────
    val repostResult: String? = null,
    val likeResult: String? = null,
    val replyResult: String? = null,
    val followResult: String? = null,

    // ── user_dynamic ──────────────────────────────────────────────────────────
    @ColumnInfo(name = "service_id")
    val serviceId: Long? = null,
    @ColumnInfo(name = "user_mid")
    val userMid: Long? = null,
    @ColumnInfo(name = "user_dynamic_type")
    val userDynamicType: String? = null,
    @ColumnInfo(name = "user_dynamic_offset")
    val userDynamicOffset: String? = null,
    @ColumnInfo(name = "user_dynamic_last_updated")
    val userDynamicLastUpdated: Long? = null
)
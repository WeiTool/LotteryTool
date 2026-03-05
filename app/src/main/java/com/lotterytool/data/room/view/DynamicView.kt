package com.lotterytool.data.room.view

import androidx.room.DatabaseView
import com.lotterytool.data.room.task.TaskState

@DatabaseView(
    viewName = "dynamic_view",
    value = """
        SELECT 
            di.articleId,
            di.dynamicId,
            ud.serviceId,
            info.type,
            info.errorMessage AS infoErrorMessage,
            t.state AS taskState,
            t.currentProgress,
            t.totalProgress,
            t.errorMessage AS taskErrorMessage,
            act.repostResult,
            act.likeResult,
            act.replyResult,
            act.followResult,
            off.time AS officialTime,
            off.isError AS officialHasError
        FROM dynamic_ids AS di
        LEFT JOIN tasks AS t ON di.articleId = t.articleId
        LEFT JOIN dynamic_info AS info ON di.dynamicId = info.dynamicId
        LEFT JOIN action_info AS act ON di.dynamicId = act.dynamicId
        LEFT JOIN official_info AS off ON di.dynamicId = off.dynamicId
        LEFT JOIN user_dynamic AS ud ON di.dynamicId = ud.dynamicId
    """
)
data class DynamicView(
    val articleId: Long,
    val dynamicId: Long,
    val serviceId: Long,
    val type: Int,
    val infoErrorMessage: String?,
    val taskState: TaskState?,
    val currentProgress: Int,
    val totalProgress: Int,
    val taskErrorMessage: String?,
    val repostResult: String?,
    val likeResult: String?,
    val replyResult: String?,
    val followResult: String?,
    val officialTime: Long?,
    val officialHasError: Boolean?
)
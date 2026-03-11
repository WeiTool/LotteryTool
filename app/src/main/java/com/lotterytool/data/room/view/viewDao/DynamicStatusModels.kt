package com.lotterytool.data.room.view.viewDao

data class ArticleStatusRow(
    val articleId: Long,
    val hasError: Boolean,
    val isExpired: Boolean,
    val hasActionError: Boolean,
    val hasMissingTypes: Boolean,
    val hasMissingOfficialTime: Boolean,
    val isProcessed: Boolean
)

/**
 * 合并查询的结果，每个 articleId 对应一行。
 * 包含按 type 分组的计数、解析错误、过期状态、操作错误及官方信息缺失标志。
 * Room 自动将 SQLite 0/1 整数映射为 Boolean。
 */
data class ListIconStatusRow(
    // 各 type 的动态数量
    val countType0: Int,
    val countType1: Int,
    val countType2: Int,

    // 解析错误：type 0 用 officialHasError，type 1/2 用 dynamic_info.errorMessage
    val hasParseErrorType0: Boolean,
    val hasParseErrorType1: Boolean,
    val hasParseErrorType2: Boolean,

    val hasExpiredType0: Boolean = false,
    val hasExpiredType1: Boolean = false,
    val hasExpiredType2: Boolean = false,

    // 操作执行失败（按 type）
    val hasActionErrorType0: Boolean,
    val hasActionErrorType1: Boolean,
    val hasActionErrorType2: Boolean,

    // 官方信息异常：officialTime 与 officialHasError 有一项有值另一项无值，或 officialHasError 为真
    val hasMissingOfficial: Boolean
)


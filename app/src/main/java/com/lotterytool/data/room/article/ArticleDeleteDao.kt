package com.lotterytool.data.room.article

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ArticleDeleteDao {

    /**
     * 完整删除一篇文章及其所有关联数据。
     * 删除顺序：子表数据 → dynamic_ids → tasks → article
     *
     * 修复：原版本仅删除 tasks / dynamic_ids / article，
     * 导致 dynamic_info / action_info / official_info / user_dynamic 中的
     * 孤立记录残留，视图查询时产生脏数据。
     */
    @Transaction
    suspend fun deleteArticleFully(articleId: Long) {
        // 先取出所有 dynamicId，用于删除子表记录
        val dynamicIds = getDynamicIdsByArticleId(articleId)

        if (dynamicIds.isNotEmpty()) {
            // 按依赖顺序删除各子表
            deleteDynamicInfoByIds(dynamicIds)
            deleteActionInfoByIds(dynamicIds)
            deleteOfficialInfoByIds(dynamicIds)
            deleteUserDynamicByIds(dynamicIds)
        }

        // 最后删除关联表和主表
        deleteTaskByArticleId(articleId)
        deleteDynamicIdsByArticleId(articleId)
        deleteArticleByArticleId(articleId)
    }

    // ── 查询辅助 ──────────────────────────────────────────────────────────────

    @Query("SELECT dynamicId FROM dynamic_ids WHERE articleId = :articleId")
    suspend fun getDynamicIdsByArticleId(articleId: Long): List<Long>

    // ── 子表删除 ──────────────────────────────────────────────────────────────

    @Query("DELETE FROM dynamic_info WHERE dynamicId IN (:ids)")
    suspend fun deleteDynamicInfoByIds(ids: List<Long>)

    @Query("DELETE FROM action_info WHERE dynamicId IN (:ids)")
    suspend fun deleteActionInfoByIds(ids: List<Long>)

    @Query("DELETE FROM official_info WHERE dynamicId IN (:ids)")
    suspend fun deleteOfficialInfoByIds(ids: List<Long>)

    @Query("DELETE FROM user_dynamic WHERE dynamicId IN (:ids)")
    suspend fun deleteUserDynamicByIds(ids: List<Long>)

    // ── 主表及关联表删除 ───────────────────────────────────────────────────────

    @Query("DELETE FROM tasks WHERE articleId = :articleId")
    suspend fun deleteTaskByArticleId(articleId: Long)

    @Query("DELETE FROM dynamic_ids WHERE articleId = :articleId")
    suspend fun deleteDynamicIdsByArticleId(articleId: Long)

    @Query("DELETE FROM article WHERE articleId = :articleId")
    suspend fun deleteArticleByArticleId(articleId: Long)
}
package com.lotterytool.data.room.action

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionDao {
    // 存入数据库
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: ActionEntity)

    // 获取已经处理的动态
    @Query("SELECT * FROM action_info WHERE dynamicId = :dynamicId")
    suspend fun getActionById(dynamicId: Long): ActionEntity?

    // 删除单条动态的操作记录（用于精细化删除：仅删除成功远端删除的动态）
    @Query("DELETE FROM action_info WHERE dynamicId = :dynamicId")
    suspend fun deleteByDynamicId(dynamicId: Long)

    // 删除专栏任务（全量删除，仅在整个专栏可完整删除时调用）
    @Query("DELETE FROM action_info WHERE dynamicId IN (SELECT dynamicId FROM dynamic_info WHERE articleId = :articleId)")
    suspend fun deleteByArticleId(articleId: Long)

    // 监听专栏任务失败的 Flow
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM action_info 
            WHERE dynamicId IN (
                SELECT dynamicId FROM dynamic_info WHERE articleId = :articleId
            )
            AND (
                repostResult != '成功' OR 
                likeResult != '成功' OR 
                replyResult != '成功' OR 
                (followResult != '成功' AND followResult != '已经关注用户，无法重复关注')
            )
            LIMIT 1
        )
    """)
    fun hasActionErrorForArticle(articleId: Long): Flow<Boolean>

    // 检查是否有抽奖动作出错
    @Query("""
        SELECT 
            EXISTS(
                SELECT 1 FROM action_info a 
                JOIN dynamic_info d ON a.dynamicId = d.dynamicId 
                WHERE d.articleId = :articleId AND d.type = 0 AND (
                    a.repostResult != '成功' OR a.likeResult != '成功' OR a.replyResult != '成功' OR 
                    (a.followResult != '成功' AND a.followResult != '已经关注用户，无法重复关注')
                )
            ) as hasOfficialError,
            EXISTS(
                SELECT 1 FROM action_info a 
                JOIN dynamic_info d ON a.dynamicId = d.dynamicId 
                WHERE d.articleId = :articleId AND d.type = 1 AND (
                    a.repostResult != '成功' OR a.likeResult != '成功' OR a.replyResult != '成功' OR 
                    (a.followResult != '成功' AND a.followResult != '已经关注用户，无法重复关注')
                )
            ) as hasNormalError,
            EXISTS(
                SELECT 1 FROM action_info a 
                JOIN dynamic_info d ON a.dynamicId = d.dynamicId 
                WHERE d.articleId = :articleId AND d.type = 2 AND (
                    a.repostResult != '成功' OR a.likeResult != '成功' OR a.replyResult != '成功' OR 
                    (a.followResult != '成功' AND a.followResult != '已经关注用户，无法重复关注')
                )
            ) as hasSpecialError
    """)
    fun getActionErrorStatusFlow(articleId: Long): Flow<ActionErrorResult?>
    data class ActionErrorResult(
        val hasOfficialError: Boolean,
        val hasNormalError: Boolean,
        val hasSpecialError: Boolean
    )
}
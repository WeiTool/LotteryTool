package com.lotterytool.data.room.article

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ArticleDeleteDao {

    @Transaction
    suspend fun deleteArticleFully(articleId: Long) {
        // 1. 删除任务状态
        deleteTaskByArticleId(articleId)
        // 2. 删除专栏与动态的关联 ID
        deleteDynamicIdsByArticleId(articleId)
        // 3. 最后删除专栏实体本身
        deleteArticleByArticleId(articleId)
    }

    @Query("DELETE FROM tasks WHERE articleId = :articleId")
    suspend fun deleteTaskByArticleId(articleId: Long)

    @Query("DELETE FROM dynamic_ids WHERE articleId = :articleId")
    suspend fun deleteDynamicIdsByArticleId(articleId: Long)

    @Query("DELETE FROM article WHERE articleId = :articleId")
    suspend fun deleteArticleByArticleId(articleId: Long)
}
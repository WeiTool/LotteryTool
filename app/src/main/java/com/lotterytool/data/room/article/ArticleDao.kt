package com.lotterytool.data.room.article

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    // 存入数据库
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    // 获取专栏数据流，用于UI展示
    @Query("SELECT * FROM article ORDER BY publishTime DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    // 删除专栏
    @Query("DELETE FROM article WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: Long)

}
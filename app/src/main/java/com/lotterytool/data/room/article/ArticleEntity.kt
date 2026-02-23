package com.lotterytool.data.room.article

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "article",
    indices = [Index("mid")]
)
data class ArticleEntity(
    @PrimaryKey val articleId: Long,
    val mid: Long,
    val publishTime: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
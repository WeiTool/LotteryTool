package com.lotterytool.data.models

import com.google.gson.annotations.SerializedName

data class ArticleResponse(
    //顶层字段
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: ArticleData?
)

data class ArticleData(
    //data字段
    @SerializedName("articles") val articles: List<Ids>,
)

data class Ids(
    //id信息
    @SerializedName("id") val id: Long,
    @SerializedName("publish_time") val publishTime: Long,
)
package com.lotterytool.data.models

import com.google.gson.annotations.SerializedName

data class ActionResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
)

data class LikeRequest(
    val dyn_id_str: String, // 动态 id
    val up: Int             // 点赞状态：1 为点赞
)

data class RemoveRequest(
    val dyn_id_str: String,
)
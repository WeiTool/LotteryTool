package com.lotterytool.data.models

import com.google.gson.annotations.SerializedName

data class UserDynamicResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: UserDynamicData?
)

data class UserDynamicData(
    @SerializedName("has_more") val hasMore: Boolean,
    @SerializedName("items") val items: List<UserDynamicItem>,
    @SerializedName("offset") val offset: String,
)

data class UserDynamicItem(
    @SerializedName("id_str") val idStr: String,
    @SerializedName("type") val type: String,
    @SerializedName("orig") val orig: DynamicOrig?,
)

data class DynamicOrig(
    @SerializedName("id_str") val idStr: String,
)
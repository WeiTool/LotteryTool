package com.lotterytool.data.models

import com.google.gson.annotations.SerializedName

data class UserResponse(
    //顶层字段
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: UserData?
)

data class UserData(
    //data字段
    @SerializedName("mid") val mid: Long,
    @SerializedName("face") val face: String,
    @SerializedName("uname") val uname: String,
    @SerializedName("isLogin") val isLogin: Boolean,
    @SerializedName("wbi_img") val wbiImg: WbiImg?
)

data class WbiImg(
    //wbi签名字段
    @SerializedName("img_url") val imgUrl: String,
    @SerializedName("sub_url") val subUrl: String
)
package com.lotterytool.data.models

import com.google.gson.annotations.SerializedName

data class QRResponse<T> (
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T
)

data class ApplicationQR (
    @SerializedName("url") val url: String,
    @SerializedName("qrcode_key") val key: String,
)

data class Execution(
    @SerializedName("url") val url: String,
    @SerializedName("refresh_token") val token: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
)


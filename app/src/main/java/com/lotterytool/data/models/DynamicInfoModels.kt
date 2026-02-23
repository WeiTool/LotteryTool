package com.lotterytool.data.models

import com.google.gson.annotations.SerializedName

data class DynamicInfoResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: DynamicInfoData?
)

//data层下面card
data class DynamicInfoData(
    @SerializedName("card") val firstCard: CardData?
)

//card层下面
data class CardData(
    @SerializedName("desc") val desc: DescData?,
    @SerializedName("card") val secondCard: String?,
    @SerializedName("extend_json") val extendJson: String?
)

data class DescData(
    @SerializedName("rid") val rid: Long?,
    @SerializedName("uid") val uid: Long?,
    @SerializedName("timestamp") val timestamp: Long?
)

//card层下面card里面的需要重新json化的card
data class Item(
    @SerializedName("item") val item: CardItem?
)

data class CardItem(
    @SerializedName("description") val description: String?,
    @SerializedName("content") val content: String?,
)

//重新转换成json解析extend_json
data class ExtendData(
    @SerializedName("lott") val lott: LottData?
)

data class LottData(
    @SerializedName("lottery_id") val lotteryId: Long?
)
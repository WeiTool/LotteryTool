package com.lotterytool.data.models

import com.google.gson.annotations.SerializedName

data class OfficialLotteryResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: LotteryData?
)

data class LotteryData(
    //开奖日期
    @SerializedName("lottery_time") val time: Long?,
    //抽取人数
    @SerializedName("first_prize") val firstPrize: Int?,
    @SerializedName("second_prize") val secondPrize: Int?,
    @SerializedName("third_prize") val thirdPrize: Int?,
    //奖品信息
    @SerializedName("first_prize_cmt") val firstPrizeCmt: String?,
    @SerializedName("second_prize_cmt") val secondPrizeCmt: String?,
    @SerializedName("third_prize_cmt") val thirdPrizeCmt: String?,
)
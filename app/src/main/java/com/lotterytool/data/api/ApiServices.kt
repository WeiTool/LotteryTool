package com.lotterytool.data.api

import com.lotterytool.data.models.ActionResponse
import com.lotterytool.data.models.ApplicationQR
import com.lotterytool.data.models.ArticleResponse
import com.lotterytool.data.models.CheckVersionResponse
import com.lotterytool.data.models.DynamicIdResponse
import com.lotterytool.data.models.DynamicInfoResponse
import com.lotterytool.data.models.Execution
import com.lotterytool.data.models.LikeRequest
import com.lotterytool.data.models.OfficialLotteryResponse
import com.lotterytool.data.models.QRResponse
import com.lotterytool.data.models.RemoveRequest
import com.lotterytool.data.models.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface ApiServices {
    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }

    //----------------GET------------------
    @GET("https://api.bilibili.com/x/web-interface/nav")
    suspend fun getUserInfo(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") ua: String = USER_AGENT
    ): UserResponse

    @GET("https://api.bilibili.com/x/space/wbi/article")
    suspend fun getArticleIDs(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") ua: String = USER_AGENT,
        @QueryMap params:Map<String, String>
    ): ArticleResponse

    @GET("https://api.bilibili.com/x/article/view")
    suspend fun getDynamicIDs(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") ua: String = USER_AGENT,
        @Query("id") articleId: Long,
    ): DynamicIdResponse

    @GET("https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/get_dynamic_detail")
    suspend fun getDynamicInfo(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") ua: String = USER_AGENT,
        @Query("dynamic_id") dynamicId: Long,
    ): DynamicInfoResponse

    @GET("https://api.vc.bilibili.com/lottery_svr/v1/lottery_svr/lottery_notice")
    suspend fun getOfficialDynamic(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") ua: String = USER_AGENT,
        @Query("business_id") dynamicId: Long,
        @Query("business_type") businessType: Int = 1
    ): OfficialLotteryResponse

    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
    suspend fun getQR(
        @Header("User-Agent") ua: String = USER_AGENT
    ): QRResponse<ApplicationQR>

    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/poll")
    suspend fun qr(
        @Header("User-Agent") ua: String = USER_AGENT,
        @Query("qrcode_key") qrcodeKey: String,
    ): QRResponse<Execution>

    @GET("https://gitee.com/api/v5/repos/weitool/lottery-tool/releases")
    suspend fun checkVersion(
        @Query("page") page : Int = 1,
        @Query("per_page") perPage: Int = 1,
        @Query("direction") direction: String = "desc"
    ): Response<List<CheckVersionResponse>>

    //----------------POST------------------
    // 点赞
    @POST("https://api.bilibili.com/x/dynamic/feed/dyn/thumb")
    suspend fun like(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") ua: String = USER_AGENT,
        @Query("csrf") csrf: String,
        @Body request: LikeRequest
    ): ActionResponse

    // 评论
    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/v2/reply/add")
    suspend fun reply(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") ua: String = USER_AGENT,
        @Field("type") type: Int,
        @Field("oid") oid: Long,
        @Field("message") message: String,
        @Field("csrf") csrf: String
    ): ActionResponse

    // 关注
    @FormUrlEncoded
    @POST("https://api.bilibili.com/x/relation/modify")
    suspend fun follow(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") ua: String = USER_AGENT,
        @Field("fid") fid: Long,
        @Field("act") act: Int,
        @Field("csrf") csrf: String
    ): ActionResponse

    // 转发
    @FormUrlEncoded
    @POST("https://api.vc.bilibili.com/dynamic_repost/v1/dynamic_repost/repost")
    suspend fun repost(
        @Header("Cookie") cookie: String,
        @Field("dynamic_id") dynamicId: Long,
        @Field("content") content: String,
        @Field("csrf_token") csrf: String
    ): ActionResponse

    //删除
    @POST("https://api.bilibili.com/x/dynamic/feed/operate/remove")
    suspend fun remove(
        @Header("Cookie") cookie: String,
        @Query("csrf") csrf: String,
        @Body body: RemoveRequest
    ): ActionResponse
}
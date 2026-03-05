package com.lotterytool.data.room.view.viewDao

import androidx.room.Dao
import androidx.room.Query
import com.lotterytool.data.room.view.DynamicInfoDetail

@Dao
interface DynamicInfoDetailDao {

    /**
     * 一次性查出指定专栏下所有动态及其关联的 serviceId。
     *
     * 用于 deleteArticleFull：替代原先"先查所有 dynamicId，再逐条查 serviceId"的两步模式，
     * 单次 IO 即可拿到删除所需的全部信息。
     *
     * 注意：serviceId 可能为 null（user_dynamic 中无对应记录），调用方需自行处理。
     */
    @Query("SELECT * FROM dynamic_info_detail WHERE articleId = :articleId")
    suspend fun getDetailsByArticleId(articleId: Long): List<DynamicInfoDetail>
}
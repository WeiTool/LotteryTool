package com.lotterytool.data.room.officialInfo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "official_info")
data class OfficialInfoEntity(
    @PrimaryKey val dynamicId: Long,
    val time: Long = 0,
    val firstPrize: Int = 0,
    val secondPrize: Int = 0,
    val thirdPrize: Int = 0,
    val firstPrizeCmt: String = "",
    val secondPrizeCmt: String = "",
    val thirdPrizeCmt: String = "",
    val errorMessage: String? = null,
    val isError: Boolean = false
)
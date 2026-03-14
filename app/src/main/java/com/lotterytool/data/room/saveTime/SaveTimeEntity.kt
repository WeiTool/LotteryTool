package com.lotterytool.data.room.saveTime

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "save_time")
data class SaveTimeEntity(
    @PrimaryKey val id: Int = 1,
    val saveTime: Int
)
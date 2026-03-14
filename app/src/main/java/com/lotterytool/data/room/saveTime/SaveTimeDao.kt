package com.lotterytool.data.room.saveTime

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SaveTimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSaveTime(saveTimeEntity: SaveTimeEntity)

    @Query("SELECT saveTime FROM save_time WHERE id = 1")
    suspend fun getLatestSaveTime(): Int?
}
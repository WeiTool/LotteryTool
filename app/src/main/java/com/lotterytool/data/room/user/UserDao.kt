package com.lotterytool.data.room.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    // 存入数据库
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // 获取所有用户列表（用于 UI 展示多卡片）
    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    // 获取所有用户列表（用于整体刷新）
    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    // 按 mid 同步查询单个用户（供 Worker / 后台协程使用）
    @Query("SELECT * FROM users WHERE mid = :mid")
    suspend fun getUserById(mid: Long): UserEntity?

    // 按 mid 同步查询单个用户
    @Query("SELECT * FROM users WHERE mid = :mid")
    fun getUserFlowById(mid: Long): Flow<UserEntity?>

    @Query("DELETE FROM users WHERE mid = :mid")
    suspend fun deleteUserById(mid: Long)
}
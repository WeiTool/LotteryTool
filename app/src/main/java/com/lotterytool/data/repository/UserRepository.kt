package com.lotterytool.data.repository

import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.data.room.user.UserEntity
import com.lotterytool.utils.FetchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val apiService: ApiServices,
    private val userDao: UserDao
) {
    suspend fun refreshAllUsers(): FetchResult<Unit> {
        return try {
            val users = userDao.getAllUsers()
            val errorList = mutableListOf<String>()

            users.forEachIndexed { index, user ->
                val result = fetchUser(user.SESSDATA, user.CSRF)

                if (result is FetchResult.Error) {
                    errorList.add("${user.mid}:${result.message}")
                }

                if (index < users.size - 1) {
                    delay(1000L + (0..500).random())
                }
            }

            if (errorList.isEmpty()) {
                FetchResult.Success(Unit)
            } else {
                FetchResult.Error(errorList.joinToString(";"))
            }
        } catch (e: Exception) {
            FetchResult.Error("CRITICAL_ERROR:${e.localizedMessage}")
        }
    }
    //获取用户信息并保存
    suspend fun fetchUser(SESSDATA: String, CSRF: String): FetchResult<Unit> {
        return try {
            val response = apiService.getUserInfo(SESSDATA)

            when (response.code) {
                0 -> {
                    response.data?.let { data ->
                        // 提取 ID（这里假设你定义的 extractUrlId 可能返回空字符串）
                        val imgId = extractUrlId(data.wbiImg?.imgUrl)
                        val subId = extractUrlId(data.wbiImg?.subUrl)

                        // 只要 mid 为 0 (或 null)、用户名为空、头像为空、或者签名 ID 为空，就跳过
                        val isValid = data.mid != 0L &&
                                data.uname.isNotBlank() &&
                                data.face.isNotBlank() &&
                                imgId.isNotBlank() &&
                                subId.isNotBlank()

                        if (isValid) {
                            userDao.insertUser(
                                UserEntity(
                                    mid = data.mid,
                                    face = data.face,
                                    uname = data.uname,
                                    isLogin = data.isLogin,
                                    imgUrlId = imgId,
                                    subUrlId = subId,
                                    SESSDATA = SESSDATA,
                                    CSRF = CSRF,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                            FetchResult.Success(Unit)
                        } else {
                            // 如果数据不完整，可以返回一个特定的错误提示
                            FetchResult.Error("获取到的用户信息不完整，已跳过存库")
                        }
                    } ?: FetchResult.Error("响应数据为空") // 处理 data 为 null 的情况
                }

                -101 -> {
                    FetchResult.Error(response.message)
                }

                else -> {
                    // 其他错误码的处理
                    FetchResult.Error("未知错误: ${response.message}")
                }
            }
        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "网络请求失败")
        }
    }

    //提取Wbi签名的数字部分
    private fun extractUrlId(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        return url.substringAfterLast('/').substringBeforeLast('.')
    }
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsersFlow()
}
// CheckVersionRepository.kt
package com.lotterytool.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.lotterytool.data.api.ApiServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CheckVersionRepository @Inject constructor(
    private val apiServices: ApiServices,
    @param:ApplicationContext private val context: Context // 注入 Context 以获取当前版本
) {
    suspend fun checkUpdate(): UpdateResult {
        return try {
            val response = apiServices.checkVersion()

            if (response.code() == 200) {
                val releases = response.body()
                if (!releases.isNullOrEmpty()) {
                    // 1. 获取服务器上最新的版本 (列表第一个)
                    val latestRelease = releases[0]
                    val latestVersion = latestRelease.name.replace(Regex("[Vv]"), "") // 去掉 V

                    // 2. 获取本地当前版本
                    val currentVersion = getCurrentVersionName().replace(Regex("[Vv]"), "")

                    // 3. 比较版本 (这里简单判断字符串不一致则更新，也可做更复杂的版本号解析)
                    if (latestVersion != currentVersion && latestVersion.isNotEmpty()) {
                        UpdateResult.HasUpdate(
                            latestVersion = latestVersion,
                            currentVersion = currentVersion,
                            releaseNotes = latestRelease.body,
                            downloadUrl = latestRelease.assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl
                        )
                    } else {
                        UpdateResult.NoUpdate
                    }
                } else {
                    UpdateResult.Error("未发现发布版本")
                }
            } else {
                UpdateResult.Error("服务器响应错误: ${response.code()}")
            }
        } catch (e: Exception) {
            UpdateResult.Error("网络连接失败: ${e.message}")
        }
    }

    // 获取当前 App 的 VersionName
    private fun getCurrentVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}

// 定义一个结果密封类，方便 ViewModel 处理
sealed class UpdateResult {
    data class HasUpdate(
        val latestVersion: String,
        val currentVersion: String,
        val releaseNotes: String,
        val downloadUrl: String?
    ) : UpdateResult()
    object NoUpdate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
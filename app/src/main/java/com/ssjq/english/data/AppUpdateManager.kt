package com.ssjq.english.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 版本信息：对应 version.json 返回的 JSON。
 * 注意：没有 versionCode 字段，只有 versionName 字符串。
 */
data class AppVersion(
    @SerializedName("latestVersion") val latestVersion: String,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("changelog") val changelog: String,
    @SerializedName("forceUpdate") val forceUpdate: Boolean = false,
)

/**
 * 公告数据：对应 log.json 返回的 JSON。
 */
data class NoticeData(
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("is_force") val isForce: Boolean,
    @SerializedName("btn_text") val btnText: String,
    @SerializedName("url") val url: String,
)

/** 公告拉取结果 */
enum class NoticeResult {
    HAS_NOTICE,
    NO_NOTICE,
    NETWORK_ERROR,
}

/** 版本比较结果 */
enum class UpdateResult {
    NEW_VERSION_AVAILABLE,
    NO_UPDATE,
    NETWORK_ERROR,
}

/**
 * App 更新管理器：
 * - OkHttp + Gson 拉取版本信息
 * - 版本号对比（语义化版本 major.minor.patch）
 * - 用系统浏览器打开下载链接
 */
object AppUpdateManager {

    private const val VERSION_URL =
        "https://TBssjq.github.io/version/english.json"

    private const val NOTICE_URL =
        "https://TBssjq.github.io/version/english.log.json"

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val gson: Gson by lazy { Gson() }

    /**
     * 从服务端获取公告信息。
     * @return Pair(公告数据, 结果)
     */
    suspend fun fetchNotice(): Pair<NoticeData?, NoticeResult> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(NOTICE_URL).get().build()
                val response = okHttp.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null to NoticeResult.NETWORK_ERROR
                val body = response.body?.string() ?: return@withContext null to NoticeResult.NETWORK_ERROR
                val notice = gson.fromJson(body, NoticeData::class.java)
                notice to NoticeResult.HAS_NOTICE
            } catch (e: Exception) {
                null to NoticeResult.NETWORK_ERROR
            }
        }
    }

    /**
     * 从服务端获取最新版本信息。
     * @return Pair(版本信息, 更新结果)
     */
    suspend fun checkUpdate(context: Context): Pair<AppVersion?, UpdateResult> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(VERSION_URL).get().build()
                val response = okHttp.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null to UpdateResult.NETWORK_ERROR
                val body = response.body?.string() ?: return@withContext null to UpdateResult.NETWORK_ERROR
                val version = gson.fromJson(body, AppVersion::class.java)
                val currentVersion = getCurrentVersionName(context)
                val result = if (isVersionNewer(version.latestVersion, currentVersion))
                    UpdateResult.NEW_VERSION_AVAILABLE
                else
                    UpdateResult.NO_UPDATE
                version to result
            } catch (e: Exception) {
                null to UpdateResult.NETWORK_ERROR
            }
        }
    }

    /** 获取当前 App 的 versionName */
    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0"
        } catch (_: Exception) {
            "0.0"
        }
    }

    /**
     * 版本号对比：a 比 b 新返回 true。
     * 支持 "1.0"、"1.2.3"、"1.0.0-alpha" 等常见格式。
     * 按段按数字比较；段数不足补 0。
     */
    fun isVersionNewer(a: String, b: String): Boolean {
        val aParts = a.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val bParts = b.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val ai = aParts.getOrElse(i) { 0 }
            val bi = bParts.getOrElse(i) { 0 }
            if (ai > bi) return true
            if (ai < bi) return false
        }
        return false
    }

    /**
     * 用系统浏览器打开下载链接。
     */
    fun openDownloadUrl(context: Context, version: AppVersion) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(version.downloadUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}

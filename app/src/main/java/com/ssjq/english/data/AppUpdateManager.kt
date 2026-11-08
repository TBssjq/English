package com.ssjq.english.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 版本信息：对应 english.json。
 *
 * 所有字段都带默认值，服务端缺字段时得到空串而不是 null，
 * 避免在 UI 层把 null 传给 [androidx.compose.material3.Text] 直接 NPE 闪退。
 */
data class AppVersion(
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val changelog: String = "",
    val forceUpdate: Boolean = false,
)

/**
 * 公告数据。
 *
 * 服务端历史上出现过两种结构，解析层统一归一化成本对象后再交给 UI：
 * 1. 单条公告：{ title, content, is_force, btn_text, url }
 * 2. 更新日志：{ latest, updated, entries: [ { version, date, changes: [...] } ] }
 *
 * 注意：字段全部带默认值。此前这里是非空 String，而 Gson 在字段缺失时
 * 会用 Unsafe 直接塞 null（不经过构造函数，默认值不生效），
 * 导致首启拉取公告时 `Text(notice.title)` 抛 NPE 闪退。
 */
data class NoticeData(
    val title: String = "",
    val content: String = "",
    val isForce: Boolean = false,
    val btnText: String = "",
    val url: String = "",
) {
    /** 是否值得展示：没有任何文案时不弹空对话框 */
    fun hasContent(): Boolean = title.isNotBlank() || content.isNotBlank()

    /**
     * 公告标识。标题或正文任一处变化即视为新公告，
     * 用它代替「已读过」的布尔值，作者发布新公告时老用户仍能收到。
     */
    fun seenKey(): String = "$title#${content.length}#${content.hashCode()}"
}

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
 * - OkHttp 拉取版本/公告，解析层对脏数据零容忍（缺字段、结构变更都不会崩）
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

    /**
     * 从服务端获取公告信息。
     * @return Pair(公告数据, 结果)
     */
    suspend fun fetchNotice(): Pair<NoticeData?, NoticeResult> {
        return withContext(Dispatchers.IO) {
            val body = get(NOTICE_URL)
                ?: return@withContext null to NoticeResult.NETWORK_ERROR
            val notice = parseNotice(body)
            if (notice == null) {
                null to NoticeResult.NO_NOTICE
            } else {
                notice to NoticeResult.HAS_NOTICE
            }
        }
    }

    /**
     * 从服务端获取最新版本信息。
     * @return Pair(版本信息, 更新结果)
     */
    suspend fun checkUpdate(context: Context): Pair<AppVersion?, UpdateResult> {
        return withContext(Dispatchers.IO) {
            val body = get(VERSION_URL)
                ?: return@withContext null to UpdateResult.NETWORK_ERROR
            val version = parseVersion(body)
                ?: return@withContext null to UpdateResult.NETWORK_ERROR
            val result = if (isVersionNewer(version.latestVersion, getCurrentVersionName(context))) {
                UpdateResult.NEW_VERSION_AVAILABLE
            } else {
                UpdateResult.NO_UPDATE
            }
            version to result
        }
    }

    /** 发起 GET 请求，失败/非 2xx 统一返回 null */
    private fun get(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 解析公告，兼容「单条公告」与「更新日志」两种服务端结构。
     * 无法识别时返回 null（调用方按 NO_NOTICE 处理，绝不弹出空内容对话框）。
     */
    internal fun parseNotice(body: String): NoticeData? {
        val root = parseObject(body) ?: return null

        // 格式一：单条公告
        val title = root.string("title")
        val content = root.string("content")
        if (title.isNotBlank() || content.isNotBlank()) {
            return NoticeData(
                title = title.ifBlank { "公告" },
                content = content,
                isForce = root.bool("is_force"),
                btnText = root.string("btn_text"),
                url = root.string("url"),
            )
        }

        // 格式二：更新日志（entries 变更列表）
        val entries = root.array("entries")
        if (!entries.isNullOrEmpty()) {
            val latest = root.string("latest").ifBlank { "更新" }
            val text = buildString {
                for (element in entries) {
                    val entry = element as? JsonObject ?: continue
                    val version = entry.string("version")
                    val date = entry.string("date")
                    val changes = entry.array("changes")
                        .orEmpty()
                        .mapNotNull { it.textOrNull() }
                    if (changes.isEmpty() && version.isBlank()) continue
                    if (isNotEmpty()) append("\n\n")
                    append("v").append(version.ifBlank { latest })
                    if (date.isNotBlank()) append(" · ").append(date)
                    for (change in changes) {
                        append("\n· ").append(change)
                    }
                }
            }
            if (text.isBlank()) return null
            return NoticeData(
                title = "更新公告 v$latest",
                content = text,
                isForce = false,
                btnText = "知道了",
                url = "",
            )
        }

        return null
    }

    internal fun parseVersion(body: String): AppVersion? {
        val root = parseObject(body) ?: return null
        val latestVersion = root.string("latestVersion")
        if (latestVersion.isBlank()) return null
        return AppVersion(
            latestVersion = latestVersion,
            downloadUrl = root.string("downloadUrl"),
            changelog = root.string("changelog").ifBlank { "新版本已发布，立即体验！" },
            forceUpdate = root.bool("forceUpdate"),
        )
    }

    /**
     * 解析服务端返回的 JSON 对象。
     *
     * 版本与公告都托管在静态站点上，域名失效、被劫持或误传 HTML 时会返回
     * 状态码 200 的非 JSON 内容。直接交给 [JsonParser] 会抛 JsonSyntaxException，
     * 异常沿首启公告的协程冒到顶层，表现为一启动就闪退。这里把「不是 JSON」
     * 归为解析失败（返回 null），由调用方转成 NETWORK_ERROR 提示用户。
     */
    private fun parseObject(body: String): JsonObject? =
        try {
            JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: JsonSyntaxException) {
            null
        }

    // 服务端存在「单条公告」与「更新日志」两种结构，字段允许缺失
    private fun JsonObject.string(key: String): String =
        get(key)?.textOrNull().orEmpty()

    private fun JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asBoolean ?: false

    private fun JsonObject.array(key: String): List<JsonElement>? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toList()

    private fun JsonElement.textOrNull(): String? =
        takeIf { isJsonPrimitive && !isJsonNull }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** 获取当前 App 的 versionName */
    fun getCurrentVersionName(context: Context): String {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return checkNotNull(pInfo.versionName) { "应用版本名为空" }
    }

    /**
     * 版本号对比：a 比 b 新返回 true。
     * 按点分段、逐段取其中的数字比较，段数不足补 0，因此 "2.10" 比 "2.9" 新。
     * 分段里的非数字后缀（如 "1.0.0-alpha" 里的 alpha）不参与比较。
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

    /** 用系统浏览器打开下载链接；返回是否真的发起了跳转 */
    fun openDownloadUrl(context: Context, version: AppVersion): Boolean =
        openUrl(context, version.downloadUrl)

    /**
     * 用系统浏览器打开链接。
     *
     * 只放行 http/https，并在没有应用能处理 VIEW 时返回 false：
     * 服务端给出空串、非法 scheme、或设备上没有浏览器时，
     * 盲目 startActivity 会抛 ActivityNotFoundException 崩溃；
     * 只检查 scheme 而不检查接收方，则表现为点了按钮毫无反应。
     */
    fun openUrl(context: Context, url: String): Boolean {
        val uri = url.toUri()
        val scheme = uri.scheme
        if (scheme != "http" && scheme != "https") return false
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 清单里的 <queries> 声明了 VIEW + http(s)，否则 Android 11 起这里恒为 null
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
    }
}

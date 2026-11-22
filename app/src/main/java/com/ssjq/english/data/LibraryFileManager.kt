package com.ssjq.english.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object LibraryFileManager {

    const val MIME_TYPE_JSON = "application/json"
    const val SCHEMA_VERSION = 2
    private const val LEGACY_SCHEMA_VERSION = 1

    // 修复（导入无大小上限）：错题/收藏的导出文件是纯文本 JSON，正常在 1MB 以内。
    // 上限设为 32MB，防止用户误选几百 MB 的词典转储/压缩包后一次性读进内存直接 OOM。
    const val MAX_IMPORT_BYTES = 32L * 1024L * 1024L

    // 读取时的字符上限，避免 URI 未声明大小时仍把超大内容读进内存（中文约 3 字节/字）
    private const val MAX_IMPORT_CHARS = 8 * 1024 * 1024

    // 导入所需的最小剩余空间，用于兜底「文件大小未知」的情况
    private const val MIN_REQUIRED_SPACE_BYTES = 8L * 1024L * 1024L

    fun serializeToJson(data: ExportData): String {
        require(data.version == SCHEMA_VERSION) { "导出格式版本必须为 $SCHEMA_VERSION" }
        return JSONObject().apply {
            put("version", data.version)
            put("exportTime", data.exportTime)
            put("scope", data.scope.name)
            put("scopeName", data.scopeName)
            put("contentType", data.contentType.name)
            put("wrongWords", entriesToJson(data.wrongWords))
            put("favorites", entriesToJson(data.favorites))
            put("totalDays", data.totalDays)
            put("currentStreak", data.currentStreak)
            put("longestStreak", data.longestStreak)
            put("totalWordsLearned", data.totalWordsLearned)
            put("totalWordsMastered", data.totalWordsMastered)
            put("totalMinutes", data.totalMinutes)
        }.toString(2)
    }

    /**
     * 读取导出文件。版本 1 缺少范围、内容类型与统计字段，统一补齐成当前结构。
     * 版本号无法识别时在读取位置抛出，避免把未知格式当成有效数据导入。
     */
    fun deserializeFromJson(jsonString: String): ExportData {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", LEGACY_SCHEMA_VERSION)
        require(version == SCHEMA_VERSION || version == LEGACY_SCHEMA_VERSION) {
            "导入文件格式版本不匹配，需要 $SCHEMA_VERSION 或 $LEGACY_SCHEMA_VERSION"
        }
        return ExportData(
            version = SCHEMA_VERSION,
            exportTime = root.optLong("exportTime", System.currentTimeMillis()),
            scope = parseEnum(root.optString("scope"), ExportScope.ALL),
            scopeName = root.optString("scopeName"),
            contentType = parseEnum(root.optString("contentType"), ExportContentType.BOTH),
            wrongWords = jsonToEntries(root.optJSONArray("wrongWords")),
            favorites = jsonToEntries(root.optJSONArray("favorites")),
            totalDays = root.optInt("totalDays"),
            currentStreak = root.optInt("currentStreak"),
            longestStreak = root.optInt("longestStreak"),
            totalWordsLearned = root.optInt("totalWordsLearned"),
            totalWordsMastered = root.optInt("totalWordsMastered"),
            totalMinutes = root.optInt("totalMinutes"),
        )
    }

    /** 校验文件结构。版本无法识别或必需数组缺失时在读取位置抛出 */
    fun validateJson(jsonString: String) {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", LEGACY_SCHEMA_VERSION)
        require(version == SCHEMA_VERSION || version == LEGACY_SCHEMA_VERSION) {
            "导入文件格式版本不匹配，需要 $SCHEMA_VERSION 或 $LEGACY_SCHEMA_VERSION"
        }
        require(root.optJSONArray("wrongWords") != null) { "导入文件缺少错题列表" }
        require(root.optJSONArray("favorites") != null) { "导入文件缺少收藏列表" }
    }

    suspend fun writeFile(context: Context, uri: Uri, data: ExportData) {
        val outputStream = checkNotNull(context.contentResolver.openOutputStream(uri)) {
            "无法打开导出文件: $uri"
        }
        outputStream.use { os ->
            BufferedWriter(OutputStreamWriter(os, StandardCharsets.UTF_8)).use { writer ->
                writer.write(serializeToJson(data))
            }
        }
    }

    /**
     * 导入前预检：文件大小上限 + 剩余存储空间。
     * 旧实现无任何校验，误选超大文件会读爆内存，空间不足时还会把词库数据写成半截，
     * 这里在真正读取/写入之前就拒绝。
     */
    fun checkImportFeasible(context: Context, uri: Uri) {
        val declaredBytes = queryFileSize(context, uri)
        require(declaredBytes <= MAX_IMPORT_BYTES) {
            "导入文件过大（${formatMb(declaredBytes)}），上限为 ${formatMb(MAX_IMPORT_BYTES)}"
        }
        // usableSpace 返回应用私有目录所在分区剩余可写字节，取不到（-1）时跳过空间校验
        val freeBytes = runCatching { context.filesDir.usableSpace }.getOrDefault(-1L)
        if (freeBytes >= 0L) {
            // 导入需要序列化后的 XML + 写入过程中的临时副本，按 3 倍文件大小估算，
            // 文件大小未知时用最小余量兜底
            val requiredBytes = (declaredBytes.coerceAtLeast(1L) * 3L)
                .coerceAtLeast(MIN_REQUIRED_SPACE_BYTES)
            require(freeBytes >= requiredBytes) {
                "存储空间不足：需要约 ${formatMb(requiredBytes)}，当前可用 ${formatMb(freeBytes)}"
            }
        }
    }

    /** 查询 URI 声明的文件大小（字节），无法获取时返回 -1 */
    private fun queryFileSize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
            } else {
                -1L
            }
        } ?: -1L
    }.getOrDefault(-1L)

    private fun formatMb(bytes: Long): String =
        if (bytes < 0L) "未知大小" else "${bytes / 1024L / 1024L} MB"

    suspend fun readFile(context: Context, uri: Uri): String {
        val inputStream = checkNotNull(context.contentResolver.openInputStream(uri)) {
            "无法打开导入文件: $uri"
        }
        // 边读边计长度：部分 provider 不上报 SIZE，只在读取过程中拦截，避免无上限读入内存
        return inputStream.use {
            BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                val builder = StringBuilder()
                val buffer = CharArray(8 * 1024)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    if (builder.length + read > MAX_IMPORT_CHARS) {
                        throw IllegalArgumentException(
                            "导入文件过大，上限为 ${formatMb(MAX_IMPORT_BYTES)}"
                        )
                    }
                    builder.append(buffer, 0, read)
                }
                builder.toString()
            }
        }
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String, fallback: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)

    private fun entriesToJson(entries: List<WordEntry>): JSONArray {
        return JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().apply {
                    put("wordId", entry.wordId)
                    put("headWord", entry.headWord)
                    put("dbName", entry.dbName)
                    put("tranCn", entry.tranCn ?: "")
                    put("addedAt", entry.addedAt)
                })
            }
        }
    }

    private fun jsonToEntries(arr: JSONArray?): List<WordEntry> {
        if (arr == null) return emptyList()
        val seen = HashSet<String>(arr.length())
        val list = ArrayList<WordEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val wordId = o.optString("wordId")
            val dbName = o.optString("dbName").removeSuffix(".db")
            if (wordId.isBlank() || dbName.isBlank()) continue
            val key = "$wordId#$dbName"
            if (!seen.add(key)) continue
            list.add(
                WordEntry(
                    wordId = wordId,
                    headWord = o.optString("headWord"),
                    dbName = dbName,
                    tranCn = o.optString("tranCn").takeIf { it.isNotBlank() },
                    addedAt = o.optLong("addedAt", 0L),
                )
            )
        }
        return list
    }
}

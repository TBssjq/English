package com.ssjq.english.data

import android.content.Context
import android.net.Uri
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

    suspend fun readFile(context: Context, uri: Uri): String {
        val inputStream = checkNotNull(context.contentResolver.openInputStream(uri)) {
            "无法打开导入文件: $uri"
        }
        return inputStream.use {
            BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
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

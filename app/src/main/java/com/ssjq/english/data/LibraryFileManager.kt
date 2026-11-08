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

    fun deserializeFromJson(jsonString: String): ExportData {
        val root = JSONObject(jsonString)
        require(root.getInt("version") == SCHEMA_VERSION) {
            "导入文件格式版本不匹配，需要 $SCHEMA_VERSION"
        }
        return ExportData(
            version = root.getInt("version"),
            exportTime = root.getLong("exportTime"),
            scope = ExportScope.valueOf(root.getString("scope")),
            scopeName = root.getString("scopeName"),
            contentType = ExportContentType.valueOf(root.getString("contentType")),
            wrongWords = jsonToEntries(root.getJSONArray("wrongWords")),
            favorites = jsonToEntries(root.getJSONArray("favorites")),
            totalDays = root.getInt("totalDays"),
            currentStreak = root.getInt("currentStreak"),
            longestStreak = root.getInt("longestStreak"),
            totalWordsLearned = root.getInt("totalWordsLearned"),
            totalWordsMastered = root.getInt("totalWordsMastered"),
            totalMinutes = root.getInt("totalMinutes"),
        )
    }

    /** 校验文件结构。版本或必需数组缺失时直接抛出 */
    fun validateJson(jsonString: String) {
        val root = JSONObject(jsonString)
        require(root.getInt("version") == SCHEMA_VERSION) {
            "导入文件格式版本不匹配，需要 $SCHEMA_VERSION"
        }
        root.getJSONArray("wrongWords")
        root.getJSONArray("favorites")
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

    private fun jsonToEntries(arr: JSONArray): List<WordEntry> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val wordId = o.getString("wordId")
            val dbName = o.getString("dbName")
            require(wordId.isNotBlank() && dbName.isNotBlank()) {
                "导入记录缺少主键: 第 $i 条"
            }
            WordEntry(
                wordId = wordId,
                headWord = o.getString("headWord"),
                dbName = dbName,
                tranCn = o.optString("tranCn").takeIf { it.isNotBlank() },
                addedAt = o.getLong("addedAt"),
            )
        }
}

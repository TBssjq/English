package com.ssjq.english.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset

object LibraryFileManager {

    private const val TAG = "Export"
    const val MIME_TYPE_JSON = "application/json"

    fun serializeToJson(data: ExportData): String {
        return JSONObject().apply {
            put("version", data.version)
            put("exportTime", data.exportTime)
            put("scope", data.scope.name)
            put("scopeName", data.scopeName)
            put("contentType", data.contentType.name)
            put("wrongWords", entriesToJson(data.wrongWords))
            put("favorites", entriesToJson(data.favorites))
        }.toString(2)
    }

    fun deserializeFromJson(jsonString: String): ExportData? {
        return try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            val exportTime = root.optLong("exportTime", System.currentTimeMillis())
            val scopeName = root.optString("scope", "ALL")
            val scope = try {
                ExportScope.valueOf(scopeName)
            } catch (_: Exception) {
                ExportScope.ALL
            }
            val scopeLabel = root.optString("scopeName", "")
            val contentTypeName = root.optString("contentType", "BOTH")
            val contentType = try {
                ExportContentType.valueOf(contentTypeName)
            } catch (_: Exception) {
                ExportContentType.BOTH
            }
            val wrongWords = jsonToEntries(root.optJSONArray("wrongWords"))
            val favorites = jsonToEntries(root.optJSONArray("favorites"))
            ExportData(version, exportTime, scope, scopeLabel, contentType, wrongWords, favorites)
        } catch (e: Exception) {
            Log.e(TAG, "deserializeFromJson failed: ${e.message}", e)
            null
        }
    }

    fun validateJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            root.has("version") &&
                root.has("wrongWords") && root.optJSONArray("wrongWords") != null &&
                root.has("favorites") && root.optJSONArray("favorites") != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun writeFile(context: Context, uri: Uri, data: ExportData): Boolean {
        return try {
            Log.d(TAG, "writeFile: opening output stream for URI: $uri")
            val outputStream = context.contentResolver.openOutputStream(uri)
            if (outputStream == null) {
                Log.e(TAG, "openOutputStream returned null for URI: $uri")
                return false
            }
            outputStream.use { os ->
                BufferedWriter(OutputStreamWriter(os, Charset.forName("UTF-8"))).use { writer ->
                    val json = serializeToJson(data)
                    Log.d(TAG, "writeFile: JSON size: ${json.length} chars")
                    writer.write(json)
                    writer.flush()
                    Log.d(TAG, "writeFile: flush completed successfully")
                }
            }
            Log.d(TAG, "writeFile: completed successfully")
            true
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File not found: ${e.message}", e)
            false
        } catch (e: IOException) {
            Log.e(TAG, "IO error: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            false
        }
    }

    suspend fun readFile(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charset.forName("UTF-8"))).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File not found: ${e.message}", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "IO error: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            null
        }
    }

    private fun entriesToJson(entries: List<WordEntry>): JSONArray {
        return JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().apply {
                    put("wordId", entry.wordId ?: "")
                    put("headWord", entry.headWord ?: "")
                    put("dbName", entry.dbName ?: "")
                    put("tranCn", entry.tranCn ?: "")
                    put("addedAt", entry.addedAt)
                })
            }
        }
    }

    private fun jsonToEntries(arr: JSONArray?): List<WordEntry> {
        if (arr == null) return emptyList()
        return try {
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    WordEntry(
                        wordId = o.getString("wordId"),
                        headWord = o.getString("headWord"),
                        dbName = o.getString("dbName"),
                        tranCn = o.optString("tranCn").takeIf { it.isNotBlank() },
                        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
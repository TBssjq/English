package com.ssjq.english.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户词单条目：错题本 / 收藏夹共用结构。
 * 持久化在 SharedPreferences 中（asset db 只读，不能写入）。
 */
data class WordEntry(
    val wordId: String,
    val headWord: String,
    val dbName: String,
    val tranCn: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * 用户词单管理：错题本（不认识）与收藏夹（手动收藏）。
 * 单例 + SharedPreferences，通过 [snapshot] 拿到当前内存快照。
 */
object UserLibrary {

    private const val PREF_NAME = "user_library"
    const val KEY_WRONG = "wrong_words"
    const val KEY_FAVORITE = "favorites"
    private const val KEY_STUDY_INDEX = "study_index_"   // +dbName
    private const val KEY_AUTO_FAV = "auto_favorite_"     // +dbName
    private const val KEY_CATEGORY_ORDER = "category_order" // 主页分类自定义顺序

    private lateinit var prefs: SharedPreferences

    /** 必须在 Application / Activity 启动时调用一次 */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // ---------- 背诵进度 ----------

    /** 读取某词库的背诵进度（已背诵到第几个，0-based）。从未背诵返回 0 */
    fun studyIndex(dbName: String): Int = prefs.getInt(KEY_STUDY_INDEX + dbName, 0)

    /** 写入背诵进度 */
    fun saveStudyIndex(dbName: String, index: Int) {
        prefs.edit().putInt(KEY_STUDY_INDEX + dbName, index.coerceAtLeast(0)).apply()
    }

    /** 清空某词库的背诵进度（下次从头开始） */
    fun resetStudyIndex(dbName: String) {
        prefs.edit().remove(KEY_STUDY_INDEX + dbName).apply()
    }

    // ---------- 自动收藏开关 ----------

    /** 读取「不认识时同步加入收藏夹」开关，默认 false */
    fun autoFavoriteEnabled(dbName: String): Boolean = prefs.getBoolean(KEY_AUTO_FAV + dbName, false)

    /** 设置自动收藏开关 */
    fun setAutoFavorite(dbName: String, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FAV + dbName, enabled).apply()
    }

    // ---------- 主页分类顺序 ----------

    /**
     * 读取主页分类顺序（分类名称列表）。
     * 未设置返回空列表，表示使用默认顺序。
     */
    fun categoryOrder(): List<String> {
        val json = prefs.getString(KEY_CATEGORY_ORDER, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 保存主页分类顺序 */
    fun saveCategoryOrder(order: List<String>) {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        prefs.edit().putString(KEY_CATEGORY_ORDER, arr.toString()).apply()
    }

    /** 重置为默认顺序 */
    fun resetCategoryOrder() {
        prefs.edit().remove(KEY_CATEGORY_ORDER).apply()
    }

    // ---------- 错题本 ----------

    fun addWrong(entry: WordEntry) {
        val list = readList(KEY_WRONG).toMutableList()
        if (list.none { it.wordId == entry.wordId && it.dbName == entry.dbName }) {
            list.add(entry)
            writeList(KEY_WRONG, list)
        }
    }

    fun removeWrong(dbName: String, wordId: String) {
        val list = readList(KEY_WRONG).filterNot { it.wordId == wordId && it.dbName == dbName }
        writeList(KEY_WRONG, list)
    }

    fun isWrong(dbName: String, wordId: String): Boolean =
        readList(KEY_WRONG).any { it.wordId == wordId && it.dbName == dbName }

    fun wrongWords(dbName: String): List<WordEntry> =
        readList(KEY_WRONG).filter { it.dbName == dbName }.sortedBy { it.addedAt }

    fun wrongCount(dbName: String): Int = wrongWords(dbName).size

    // ---------- 收藏夹 ----------

    fun addFavorite(entry: WordEntry) {
        val list = readList(KEY_FAVORITE).toMutableList()
        if (list.none { it.wordId == entry.wordId && it.dbName == entry.dbName }) {
            list.add(entry)
            writeList(KEY_FAVORITE, list)
        }
    }

    fun removeFavorite(dbName: String, wordId: String) {
        val list = readList(KEY_FAVORITE).filterNot { it.wordId == wordId && it.dbName == dbName }
        writeList(KEY_FAVORITE, list)
    }

    fun isFavorite(dbName: String, wordId: String): Boolean =
        readList(KEY_FAVORITE).any { it.wordId == wordId && it.dbName == dbName }

    fun favorites(dbName: String): List<WordEntry> =
        readList(KEY_FAVORITE).filter { it.dbName == dbName }.sortedByDescending { it.addedAt }

    fun favoriteCount(dbName: String): Int = favorites(dbName).size

    // ---------- 持久化 ----------

    internal fun readList(key: String): List<WordEntry> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WordEntry(
                    wordId = o.getString("wordId"),
                    headWord = o.getString("headWord"),
                    dbName = o.getString("dbName"),
                    tranCn = o.optString("tranCn").takeIf { it.isNotBlank() },
                    addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun writeList(key: String, list: List<WordEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("wordId", e.wordId)
                put("headWord", e.headWord)
                put("dbName", e.dbName)
                put("tranCn", e.tranCn ?: "")
                put("addedAt", e.addedAt)
            })
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}

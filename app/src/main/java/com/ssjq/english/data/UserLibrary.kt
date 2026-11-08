package com.ssjq.english.data

import android.content.Context
import androidx.core.content.edit
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
    private const val KEY_AUTO_PLAY = "quiz_auto_play"    // 测验自动发音
    private const val KEY_CATEGORY_ORDER = "category_order" // 主页分类自定义顺序

    @Volatile
    private var appContext: Context? = null

    /** 必须在 Application / Activity 启动时调用一次 */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** 供 data 层其他单例复用同一个 application Context */
    internal fun applicationContext(): Context =
        checkNotNull(appContext) { "UserLibrary 未初始化，请先调用 init(context)" }

    /**
     * 读取 SharedPreferences。
     * 早期用 `lateinit var prefs`，在 init 之前调用任一方法都会
     * 抛 UninitializedPropertyAccessException 直接崩溃；改为惰性取值，
     * 未初始化时给出明确错误信息，且不会因调用时序问题炸掉整个 App。
     */
    private val prefs: SharedPreferences
        get() = appContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            ?: error("UserLibrary 未初始化，请先调用 init(context)")

    // ---------- 背诵进度 ----------

    /** 词库名统一去掉 .db 后缀：路由里两种写法都存在，混用会让同一词库分裂出两套进度 */
    private fun canonicalDb(name: String): String = name.removeSuffix(".db")

    /** 旧版本以带后缀的名称写入过进度，读取时兼容一次 */
    private fun legacyDb(name: String): String = "${name.removeSuffix(".db")}.db"

    /** 读取某词库的背诵进度（已背诵到第几个，0-based）。从未背诵返回 0 */
    fun studyIndex(dbName: String): Int {
        val name = canonicalDb(dbName)
        val key = KEY_STUDY_INDEX + name
        if (prefs.contains(key)) return prefs.getInt(key, 0)
        return prefs.getInt(KEY_STUDY_INDEX + legacyDb(name), 0)
    }

    /** 写入背诵进度，同时清掉旧后缀键，避免下次读到过期值 */
    fun saveStudyIndex(dbName: String, index: Int) {
        val name = canonicalDb(dbName)
        prefs.edit {
            putInt(KEY_STUDY_INDEX + name, index.coerceAtLeast(0))
            remove(KEY_STUDY_INDEX + legacyDb(name))
        }
    }

    /** 清空某词库的背诵进度（下次从头开始） */
    fun resetStudyIndex(dbName: String) {
        val name = canonicalDb(dbName)
        prefs.edit {
            remove(KEY_STUDY_INDEX + name)
            remove(KEY_STUDY_INDEX + legacyDb(name))
        }
    }

    // ---------- 自动收藏开关 ----------

    /** 读取「不认识时同步加入收藏夹」开关，默认 false */
    fun autoFavoriteEnabled(dbName: String): Boolean {
        val name = canonicalDb(dbName)
        val key = KEY_AUTO_FAV + name
        if (prefs.contains(key)) return prefs.getBoolean(key, false)
        return prefs.getBoolean(KEY_AUTO_FAV + legacyDb(name), false)
    }

    /** 设置自动收藏开关 */
    fun setAutoFavorite(dbName: String, enabled: Boolean) {
        val name = canonicalDb(dbName)
        prefs.edit {
            putBoolean(KEY_AUTO_FAV + name, enabled)
            remove(KEY_AUTO_FAV + legacyDb(name))
        }
    }

    // ---------- 测验自动发音开关 ----------

    /** 测验里是否自动播放当前单词，默认关闭；开关状态跨轮次保留 */
    fun autoPlayAudioEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_PLAY, false)

    fun setAutoPlayAudio(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_PLAY, enabled) }
    }

    // ---------- 主页分类顺序 ----------

    /**
     * 读取主页分类顺序（分类名称列表）。
     * 未设置返回空列表，表示使用默认顺序。
     */
    fun categoryOrder(): List<String> {
        val json = prefs.getString(KEY_CATEGORY_ORDER, null) ?: return emptyList()
        val arr = JSONArray(json)
        return List(arr.length()) { arr.getString(it) }
    }

    /** 保存主页分类顺序 */
    fun saveCategoryOrder(order: List<String>) {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        prefs.edit { putString(KEY_CATEGORY_ORDER, arr.toString()) }
    }

    /** 重置为默认顺序 */
    fun resetCategoryOrder() {
        prefs.edit { remove(KEY_CATEGORY_ORDER) }
    }

    // ---------- 错题本 ----------

    /** 写操作统一加锁：读-改-写不是原子操作，并发调用会丢更新 */
    @Synchronized
    fun addWrong(entry: WordEntry) {
        val normalized = entry.copy(dbName = canonicalDb(entry.dbName))
        val list = readListSafe(KEY_WRONG).toMutableList()
        if (list.none { it.wordId == normalized.wordId && it.dbName == normalized.dbName }) {
            list.add(normalized)
            writeList(KEY_WRONG, list)
        }
    }

    @Synchronized
    fun removeWrong(dbName: String, wordId: String) {
        val db = canonicalDb(dbName)
        val list = readListSafe(KEY_WRONG)
        writeList(KEY_WRONG, list.filterNot { it.wordId == wordId && it.dbName == db })
    }

    fun isWrong(dbName: String, wordId: String): Boolean {
        val db = canonicalDb(dbName)
        return readList(KEY_WRONG).any { it.wordId == wordId && it.dbName == db }
    }

    fun wrongWords(dbName: String): List<WordEntry> {
        val db = canonicalDb(dbName)
        return readList(KEY_WRONG).filter { it.dbName == db }.sortedBy { it.addedAt }
    }

    fun wrongCount(dbName: String): Int = wrongWords(dbName).size

    // ---------- 收藏夹 ----------

    @Synchronized
    fun addFavorite(entry: WordEntry) {
        val normalized = entry.copy(dbName = canonicalDb(entry.dbName))
        val list = readListSafe(KEY_FAVORITE).toMutableList()
        if (list.none { it.wordId == normalized.wordId && it.dbName == normalized.dbName }) {
            list.add(normalized)
            writeList(KEY_FAVORITE, list)
        }
    }

    @Synchronized
    fun removeFavorite(dbName: String, wordId: String) {
        val db = canonicalDb(dbName)
        val list = readListSafe(KEY_FAVORITE)
        writeList(KEY_FAVORITE, list.filterNot { it.wordId == wordId && it.dbName == db })
    }

    fun isFavorite(dbName: String, wordId: String): Boolean {
        val db = canonicalDb(dbName)
        return readList(KEY_FAVORITE).any { it.wordId == wordId && it.dbName == db }
    }

    fun favorites(dbName: String): List<WordEntry> {
        val db = canonicalDb(dbName)
        return readList(KEY_FAVORITE).filter { it.dbName == db }.sortedByDescending { it.addedAt }
    }

    fun favoriteCount(dbName: String): Int = favorites(dbName).size

    // ---------- 批量读取（性能） ----------

    /**
     * 一次性读取全部错题。
     *
     * 提供批量接口是为了避免调用方按词库循环读取 —— 那会对同一份 JSON
     * 反复反序列化（N 个词库 = N 次全量解析），几十个词库时主线程明显卡顿。
     */
    fun allWrongWords(): List<WordEntry> = readList(KEY_WRONG)

    /** 一次性读取全部收藏 */
    fun allFavorites(): List<WordEntry> = readList(KEY_FAVORITE)

    /** 各词库的错题数（只解析一次 JSON 得出全部结果） */
    fun wrongCountByDb(): Map<String, Int> =
        readList(KEY_WRONG).groupingBy { it.dbName }.eachCount()

    /** 各词库的收藏数（只解析一次 JSON 得出全部结果） */
    fun favoriteCountByDb(): Map<String, Int> =
        readList(KEY_FAVORITE).groupingBy { it.dbName }.eachCount()

    // ---------- 持久化 ----------

    /**
     * 解析词单。字段缺失或格式错误就地抛出，避免写操作基于残缺数据覆盖原文件。
     */
    private fun readListSafe(key: String): List<WordEntry> {
        cachedList(key)?.let { return it }
        val raw = prefs.getString(key, null)
        if (raw == null) {
            setCachedList(key, emptyList())
            return emptyList()
        }
        val arr = JSONArray(raw)
        val list = ArrayList<WordEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val wordId = o.getString("wordId")
            val dbName = canonicalDb(o.getString("dbName"))
            require(wordId.isNotBlank() && dbName.isNotBlank()) {
                "词单记录缺少主键: $key 第 $i 条"
            }
            list.add(
                WordEntry(
                    wordId = wordId,
                    headWord = o.getString("headWord"),
                    dbName = dbName,
                    tranCn = o.optString("tranCn").takeIf { it.isNotBlank() },
                    addedAt = o.getLong("addedAt"),
                )
            )
        }
        return list.also { setCachedList(key, it) }
    }

    /**
     * wrong / favorite 两个 key 的解析结果缓存。
     * 这两个 key 的所有写入都经过 [writeList]，缓存可在写入时同步更新，
     * 从而避免 isWrong/isFavorite/wrongCount 等每次调用都全量解析 JSON。
     */
    @Volatile
    private var wrongCache: List<WordEntry>? = null

    @Volatile
    private var favCache: List<WordEntry>? = null

    private fun cachedList(key: String): List<WordEntry>? = when (key) {
        KEY_WRONG -> wrongCache
        KEY_FAVORITE -> favCache
        else -> null
    }

    private fun setCachedList(key: String, list: List<WordEntry>) {
        when (key) {
            KEY_WRONG -> wrongCache = list
            KEY_FAVORITE -> favCache = list
        }
    }

    /** 只读场景使用的解析 */
    internal fun readList(key: String): List<WordEntry> = readListSafe(key)

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
        prefs.edit { putString(key, arr.toString()) }
        setCachedList(key, list)
    }
}

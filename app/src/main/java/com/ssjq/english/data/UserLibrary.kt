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

    @Volatile
    private var appContext: Context? = null

    /** 必须在 Application / Activity 启动时调用一次 */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

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

    /** 写操作统一加锁：读-改-写不是原子操作，并发调用会丢更新 */
    @Synchronized
    fun addWrong(entry: WordEntry) {
        // 数据损坏时中止写入，避免把用户积累的错题清零
        val list = readListSafe(KEY_WRONG)?.toMutableList() ?: return
        if (list.none { it.wordId == entry.wordId && it.dbName == entry.dbName }) {
            list.add(entry)
            writeList(KEY_WRONG, list)
        }
    }

    @Synchronized
    fun removeWrong(dbName: String, wordId: String) {
        val list = readListSafe(KEY_WRONG) ?: return
        writeList(KEY_WRONG, list.filterNot { it.wordId == wordId && it.dbName == dbName })
    }

    fun isWrong(dbName: String, wordId: String): Boolean =
        readList(KEY_WRONG).any { it.wordId == wordId && it.dbName == dbName }

    fun wrongWords(dbName: String): List<WordEntry> =
        readList(KEY_WRONG).filter { it.dbName == dbName }.sortedBy { it.addedAt }

    fun wrongCount(dbName: String): Int = wrongWords(dbName).size

    // ---------- 收藏夹 ----------

    @Synchronized
    fun addFavorite(entry: WordEntry) {
        // 数据损坏时中止写入，避免把用户积累的收藏清零
        val list = readListSafe(KEY_FAVORITE)?.toMutableList() ?: return
        if (list.none { it.wordId == entry.wordId && it.dbName == entry.dbName }) {
            list.add(entry)
            writeList(KEY_FAVORITE, list)
        }
    }

    @Synchronized
    fun removeFavorite(dbName: String, wordId: String) {
        val list = readListSafe(KEY_FAVORITE) ?: return
        writeList(KEY_FAVORITE, list.filterNot { it.wordId == wordId && it.dbName == dbName })
    }

    fun isFavorite(dbName: String, wordId: String): Boolean =
        readList(KEY_FAVORITE).any { it.wordId == wordId && it.dbName == dbName }

    fun favorites(dbName: String): List<WordEntry> =
        readList(KEY_FAVORITE).filter { it.dbName == dbName }.sortedByDescending { it.addedAt }

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
     * 解析词单。
     *
     * 旧实现在解析异常时返回 `emptyList()`，而调用它的 addWrong / removeWrong
     * 紧接着就会把「空列表」写回去 —— 只要有一条记录损坏或 JSON 结构异常，
     * 用户积累的全部错题/收藏就被静默清零，且不可恢复。
     *
     * 现在：
     * - 单条记录损坏只跳过该条，不影响其余数据；
     * - 整体解析失败返回 **null**，写操作遇到 null 一律中止，宁可不写也不能清空。
     */
    private fun readListSafe(key: String): List<WordEntry>? {
        cachedList(key)?.let { return it }
        val raw = prefs.getString(key, null)
        if (raw == null) {
            setCachedList(key, emptyList())
            return emptyList()
        }
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<WordEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val wordId = o.optString("wordId", "")
                val dbName = o.optString("dbName", "")
                // 缺主键的脏数据直接跳过
                if (wordId.isBlank() || dbName.isBlank()) continue
                list.add(
                    WordEntry(
                        wordId = wordId,
                        headWord = o.optString("headWord", wordId),
                        dbName = dbName,
                        tranCn = o.optString("tranCn").takeIf { it.isNotBlank() },
                        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                    )
                )
            }
            list.also { setCachedList(key, it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * wrong / favorite 两个 key 的解析结果缓存。
     * 这两个 key 的所有写入都经过 [writeList]，缓存可在写入时同步更新，
     * 从而避免 isWrong/isFavorite/wrongCount 等每次调用都全量解析 JSON。
     * 解析失败（null）不缓存，以便下次重试。
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

    /** 只读场景使用的解析（失败降级为空列表）；写操作请用 [readListSafe] */
    internal fun readList(key: String): List<WordEntry> = readListSafe(key) ?: emptyList()

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
        setCachedList(key, list)
    }
}

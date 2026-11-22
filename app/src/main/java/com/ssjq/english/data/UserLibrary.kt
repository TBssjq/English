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
) {
    /** 错题本与测验错题集共用这个标识，跨词库同 wordId 不会互相覆盖 */
    val sourceId: String get() = WordSource.id(dbName, wordId)

    /** 词单条目转回测验可用的词条，保留所属词库供答错时写回 */
    fun toWordDetail(): WordDetail = WordDetail(
        wordId = wordId,
        headWord = headWord,
        usPhone = null,
        ukPhone = null,
        star = 0,
        remMethod = null,
        trans = listOfNotNull(tranCn?.takeIf { it.isNotBlank() }?.let { TransItem(null, it) }),
        phrases = emptyList(),
        sentences = emptyList(),
        relWords = emptyList(),
        synos = emptyList(),
        antos = emptyList(),
        dbName = WordSource.canonicalDb(dbName),
    )
}

/** 词库名与词号构成的稳定标识，供错题本、测验断点、疯狂刷题共用 */
object WordSource {
    fun canonicalDb(name: String?): String = name?.removeSuffix(".db").orEmpty()

    fun id(dbName: String?, wordId: String): String =
        "${canonicalDb(dbName)}#$wordId"

    /** 兼容旧断点里只有 wordId 的写法；返回 null 表示格式无效 */
    fun parseId(raw: String): Pair<String, String>? {
        val split = raw.indexOf('#')
        if (split <= 0 || split == raw.lastIndex) return null
        val db = canonicalDb(raw.substring(0, split))
        val wordId = raw.substring(split + 1)
        if (db.isBlank() || wordId.isBlank()) return null
        return db to wordId
    }

    /** 旧存档只保存 wordId 时也能参与统一去重，新存档仍使用完整词源标识 */
    fun canonicalSourceId(raw: String): String =
        parseId(raw)?.let { id(it.first, it.second) } ?: raw
}

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

    /**
     * 升级或导入遗留数据后，清理 SharedPreferences 里已经损坏的词单与分类顺序。
     * 由应用入口在 IO 线程调用，避免启动阶段在主线程解析 JSON。
     */
    fun repairStoredLists() {
        listOf(KEY_WRONG, KEY_FAVORITE).forEach { key ->
            val raw = prefs.getString(key, null) ?: return@forEach
            val parsed = runCatching { parseList(raw) }.getOrNull()
            if (parsed == null) {
                writeList(key, emptyList())
                return@forEach
            }
            // 即使条数相同，也要把旧版 dbName 后缀与重复项规范化后写回，
            // 否则错题本与测验错题集会因同一词的两个标识继续分裂
            writeList(key, parsed)
        }
        val orderRaw = prefs.getString(KEY_CATEGORY_ORDER, null) ?: return
        if (runCatching { JSONArray(orderRaw) }.isFailure) {
            prefs.edit { remove(KEY_CATEGORY_ORDER) }
        }
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
    private fun canonicalDb(name: String): String = WordSource.canonicalDb(name)

    /** 旧版本以带后缀的名称写入过进度，读取时兼容一次 */
    private fun legacyDb(name: String): String = "${WordSource.canonicalDb(name)}.db"

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
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { arr.optString(it) }
                .filter { it.isNotBlank() }
                .distinct()
        }.getOrDefault(emptyList())
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
        if (list.none { it.sourceId == normalized.sourceId }) {
            list.add(normalized)
            writeList(KEY_WRONG, list)
        }
    }

    @Synchronized
    fun removeWrong(dbName: String, wordId: String) {
        val db = canonicalDb(dbName)
        val list = readListSafe(KEY_WRONG)
        writeList(KEY_WRONG, list.filterNot { it.sourceId == WordSource.id(db, wordId) })
    }

    fun isWrong(dbName: String, wordId: String): Boolean {
        val db = canonicalDb(dbName)
        return readList(KEY_WRONG).any { it.sourceId == WordSource.id(db, wordId) }
    }

    fun wrongWords(dbName: String): List<WordEntry> {
        val db = canonicalDb(dbName)
        return readList(KEY_WRONG).filter { it.dbName == db }.sortedBy { it.addedAt }
    }

    fun wrongCount(dbName: String): Int = wrongWords(dbName).size

    /**
     * 全部词库的错题，按加入时间倒序。跨词库测验的错题靠它才能看到。
     * 一次性读完整份 JSON，避免调用方按词库循环读取时反复反序列化。
     */
    fun allWrongWords(): List<WordEntry> =
        readList(KEY_WRONG).sortedByDescending { it.addedAt }

    /**
     * 按统一词源标识取错题条目。测验结果页与疯狂刷题共用这份数据，
     * 用户在错题本里删除某词后，回到结果页不会再次看到旧快照。
     */
    fun wrongWordsBySourceIds(ids: Collection<String>): List<WordEntry> {
        val wanted = ids.map(WordSource::canonicalSourceId).toSet()
        val wantedWordIds = ids.filter { WordSource.parseId(it) == null }.toSet()
        return readList(KEY_WRONG).filter {
            it.sourceId in wanted || it.wordId in wantedWordIds
        }
    }

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

    /** 全部词库的收藏，按加入时间倒序 */
    fun allFavorites(): List<WordEntry> =
        readList(KEY_FAVORITE).sortedByDescending { it.addedAt }

    // ---------- 批量读取（性能） ----------

    /** 各词库的错题数（只解析一次 JSON 得出全部结果） */
    fun wrongCountByDb(): Map<String, Int> =
        readList(KEY_WRONG).groupingBy { it.dbName }.eachCount()

    /** 各词库的收藏数（只解析一次 JSON 得出全部结果） */
    fun favoriteCountByDb(): Map<String, Int> =
        readList(KEY_FAVORITE).groupingBy { it.dbName }.eachCount()

    // ---------- 持久化 ----------

    /**
     * 解析词单。逐条校验并去重，损坏条目不会让整份词单无法读取。
     * [repairStoredLists] 会把清理后的结果写回 SharedPreferences。
     */
    private fun parseList(raw: String): List<WordEntry> {
        val arr = JSONArray(raw)
        val list = ArrayList<WordEntry>(arr.length())
        val seen = HashSet<String>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val wordId = o.optString("wordId")
            val dbName = canonicalDb(o.optString("dbName"))
            if (wordId.isBlank() || dbName.isBlank()) continue
            val key = WordSource.id(dbName, wordId)
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

    private fun readListSafe(key: String): List<WordEntry> {
        cachedList(key)?.let { return it }
        val raw = prefs.getString(key, null)
        if (raw == null) {
            setCachedList(key, emptyList())
            return emptyList()
        }
        // 数据文件由 repairStoredLists 在启动时清理；此处仍隔离解析异常，
        // 防止外部写入的残缺 JSON 沿 LaunchedEffect 冒到界面。
        val list = runCatching { parseList(raw) }.getOrDefault(emptyList())
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
        val normalized = list
            .map { it.copy(dbName = canonicalDb(it.dbName)) }
            .distinctBy { it.sourceId }
        val arr = JSONArray()
        normalized.forEach { e ->
            arr.put(JSONObject().apply {
                put("wordId", e.wordId)
                put("headWord", e.headWord)
                put("dbName", e.dbName)
                put("tranCn", e.tranCn ?: "")
                put("addedAt", e.addedAt)
            })
        }
        prefs.edit { putString(key, arr.toString()) }
        setCachedList(key, normalized)
    }
}

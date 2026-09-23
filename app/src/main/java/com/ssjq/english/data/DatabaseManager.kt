package com.ssjq.english.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/** 通用查询结果（用于浏览表 / 自定义 SQL） */
data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String?>>,
)

/** 词汇列表项（轻量，用于列表展示） */
data class WordListItem(
    val wordId: String,
    val headWord: String,
    val wordRank: Int,
    val star: Int,
    val tranCn: String?,
)

/** 单词详情（关联 words 及其衍生表） */
data class WordDetail(
    val wordId: String,
    val headWord: String,
    val usPhone: String?,
    val ukPhone: String?,
    val star: Int,
    val remMethod: String?,
    val trans: List<TransItem>,
    val phrases: List<PhraseItem>,
    val sentences: List<SentenceItem>,
    val relWords: List<RelWordItem>,
    val synos: List<SynoItem>,
    val antos: List<String>,
)

data class TransItem(val pos: String?, val tranCn: String?)
data class PhraseItem(val content: String?, val cn: String?)
data class SentenceItem(val content: String?, val cn: String?)
data class RelWordItem(val pos: String?, val hwd: String?, val tran: String?)
data class SynoItem(val pos: String?, val tran: String?)

/** 全局搜索结果项（轻量，跨词库搜索用） */
data class SearchResultItem(
    val wordId: String,
    val headWord: String,
    val tranCn: String?,
    val dbName: String,
)

/**
 * 数据库管理器：将 assets 中的 db 文件按需复制到内部缓存目录，
 * 再以只读方式打开查询。所有查询均用 SELECT * + 按列名容错取值，
 * 兼容不同词库间表结构差异（如 rem_method_val / rem_method）。
 *
 * ## 连接管理（重要）
 * 旧实现是「全局单连接 + 切换到别的词库就 close() 旧的」。
 * 这在并发场景下会把别人正在用的句柄关掉 —— 典型路径：单词详情页正在
 * 加载详情，同时用户触发跨库搜索，搜索循环打开其它词库时 close() 掉详情
 * 协程手里的连接，下一句 rawQuery 抛
 * `IllegalStateException: attempt to re-open an already-closed object`。
 *
 * 现改为**按词库分连接 + 引用计数**：
 * - 同一词库复用同一连接，切换词库不再关闭任何连接；
 * - 只有引用计数归零、或长时间闲置的连接才会被回收，绝不误关在用句柄；
 * - 配套 [release] 与 [withDatabase]（自动归还）。
 */
object DatabaseManager {

    private const val DB_DIR = "query_dbs"

    /** 软上限：超过后开始回收「引用计数为 0」的连接 */
    private const val MAX_IDLE_OPEN = 12
    /** 硬上限：所有连接都在使用时的兜底阈值 */
    private const val MAX_TOTAL_OPEN = 32
    /** 兜底回收的闲置时长。正常查询都是毫秒级，120s 足以避免误伤 */
    private const val IDLE_RECLAIM_NANOS = 120_000_000_000L

    /** 合法词库名：仅字母数字、下划线、连字符，且以 .db 结尾（防路径穿越） */
    private val SAFE_DB_NAME = Regex("^[A-Za-z0-9_-]+\\.db$")
    /** 合法表名（防 SQL 注入，表名来自被打开的第三方 db 的 sqlite_master） */
    private val SAFE_TABLE_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    private class Conn(val db: SQLiteDatabase) {
        var refs: Int = 0
        var lastUsedNanos: Long = System.nanoTime()
    }

    /** 按词库缓存的只读连接。所有访问都在同步方法内进行 */
    private val pool = LinkedHashMap<String, Conn>()

    /** 每个词库一把拷贝锁，避免并发写同一文件造成词库损坏 */
    private val copyLocks = ConcurrentHashMap<String, Any>()

    /** assets 下 .db 列表缓存（assets.list 是较重的 JNI 调用，且结果不会变） */
    @Volatile
    private var assetDbNames: List<String>? = null

    private fun dbDir(context: Context): File {
        val dir = File(context.cacheDir, DB_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 列出 assets 下所有 .db 文件。
     * 结果会被缓存：assets 内容运行期不会变化，没必要每次都走 JNI 遍历。
     */
    fun listAssetDatabases(context: Context): List<String> {
        assetDbNames?.let { return it }
        val names = (context.assets.list("")?.filter { it.endsWith(".db") } ?: emptyList()).sorted()
        assetDbNames = names
        return names
    }

    /** 归一化词库名：去掉路径分隔符，缺 .db 后缀时补上 */
    private fun normalizeDbName(raw: String): String {
        val n = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        return if (n.endsWith(".db", ignoreCase = true)) n else "$n.db"
    }

    /** 在 assets 中定位词库文件，找不到返回 null */
    private fun resolveAssetName(context: Context, dbName: String): String? {
        val names = listAssetDatabases(context)
        return dbName.takeIf { it in names }
    }

    /**
     * 把指定 db 从 assets 复制到缓存目录，返回可打开的文件路径。
     *
     * 三个关键加固：
     * 1. **按词库加锁**：并发的首屏词数统计与跨库搜索会同时触发同一文件的拷贝，
     *    无锁时两个 FileOutputStream 交错写同一文件，词库永久损坏且无法自愈；
     * 2. **原子替换**：先写 .tmp 再 rename，中断只会留下 .tmp 垃圾，
     *    不会留下「长度非 0 的半成品」被误判为已拷贝完成；
     * 3. **异常清理**：失败时删除残留文件，下次进入重新拷贝。
     */
    private fun ensureCopied(context: Context, rawName: String): String {
        val dbName = normalizeDbName(rawName)
        if (!SAFE_DB_NAME.matches(dbName)) {
            throw IllegalArgumentException("非法词库名: $rawName")
        }
        val target = File(dbDir(context), dbName)
        if (target.exists() && target.length() > 0L) return target.absolutePath

        val lock = copyLocks.getOrPut(dbName) { Any() }
        return synchronized(lock) {
            // 双检：拿到锁后可能已被其它线程拷好
            if (target.exists() && target.length() > 0L) {
                target.absolutePath
            } else {
                val tmp = File(target.parentFile, "$dbName.tmp")
                try {
                    val assetName = resolveAssetName(context, dbName)
                        ?: throw FileNotFoundException("assets 中不存在词库: $dbName")
                    context.assets.open(assetName).use { input ->
                        tmp.outputStream().use { output ->
                            input.copyTo(output)
                            output.flush()
                            output.fd.sync()
                        }
                    }
                    if (tmp.renameTo(target)) {
                        target.absolutePath
                    } else {
                        // 极少数文件系统 rename 失败：直接用 tmp 路径打开，至少可用
                        tmp.absolutePath
                    }
                } catch (e: Exception) {
                    tmp.delete()
                    throw e
                }
            }
        }
    }

    /**
     * 获取词库的只读连接（引用计数 +1）。
     *
     * 同一词库始终复用同一连接，**切换词库不会关闭任何连接**，
     * 因此不会再把并发协程手里正在用的句柄关掉。
     * 使用完毕请调用 [release]；无法配对时连接会在闲置后被自动回收，不会泄漏。
     */
    @Synchronized
    fun openDatabase(context: Context, dbName: String): SQLiteDatabase {
        val key = normalizeDbName(dbName)
        val existing = pool[key]
        if (existing != null && existing.db.isOpen) {
            existing.refs++
            existing.lastUsedNanos = System.nanoTime()
            return existing.db
        }
        if (existing != null) {
            // 连接已被外部关闭，丢弃后重建
            pool.remove(key)
        }

        reclaimIfNeeded()

        val db = SQLiteDatabase.openDatabase(
            ensureCopied(context, key), null, SQLiteDatabase.OPEN_READONLY
        )
        pool[key] = Conn(db).also { it.refs = 1 }
        return db
    }

    /** 归还连接，与 [openDatabase] 成对调用 */
    @Synchronized
    fun release(db: SQLiteDatabase?) {
        if (db == null) return
        val key = pool.entries.firstOrNull { it.value.db === db }?.key ?: return
        val conn = pool[key] ?: return
        conn.refs = (conn.refs - 1).coerceAtLeast(0)
        conn.lastUsedNanos = System.nanoTime()
        if (conn.refs == 0) reclaimIfNeeded()
    }

    /** 带租借的查询：自动归还连接，推荐所有新代码使用 */
    fun <T> withDatabase(context: Context, dbName: String, block: (SQLiteDatabase) -> T): T {
        val db = openDatabase(context, dbName)
        return try {
            block(db)
        } finally {
            release(db)
        }
    }

    /** 关闭全部连接（退出应用时调用） */
    @Synchronized
    fun closeDatabase() {
        pool.values.forEach { runCatching { it.db.close() } }
        pool.clear()
    }

    /**
     * 按需回收连接：优先回收引用计数为 0 的；
     * 全部都在使用时，仅回收闲置超过 [IDLE_RECLAIM_NANOS] 的作为兜底。
     */
    private fun reclaimIfNeeded() {
        if (pool.size <= MAX_IDLE_OPEN) return

        val idle = pool.entries.filter { it.value.refs == 0 }
            .sortedBy { it.value.lastUsedNanos }
        for (e in idle) {
            if (pool.size <= MAX_IDLE_OPEN) break
            runCatching { e.value.db.close() }
            pool.remove(e.key)
        }

        if (pool.size <= MAX_TOTAL_OPEN) return
        val now = System.nanoTime()
        val stale = pool.entries
            .filter { now - it.value.lastUsedNanos > IDLE_RECLAIM_NANOS }
            .sortedBy { it.value.lastUsedNanos }
        for (e in stale) {
            if (pool.size <= MAX_TOTAL_OPEN) break
            runCatching { e.value.db.close() }
            pool.remove(e.key)
        }
    }

    /**
     * 查询指定词库的单词总数。
     * 独立打开 db（不影响缓存的 current db），查询后立即关闭。
     * 用于首页展示词库规模，不影响正在使用的词库连接。
     */
    fun getWordCount(context: Context, dbName: String): Int {
        // 词库损坏 / assets 缺失 / 表结构异常一律按 0 处理，绝不让首屏统计把 App 带崩
        return try {
            val path = ensureCopied(context, dbName)
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                if (!tableExists(db, "words")) return 0
                db.rawQuery(
                    "SELECT COUNT(*) FROM words WHERE head_word IS NOT NULL", null
                ).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * 在所有词库中搜索单词。
     * 逐个打开 db 查询（每个 db 最多 limitPerDb 条），合并后按单词排序返回。
     * 只查询轻量字段（word_id, head_word + 中文释义），避免加载详情拖慢速度。
     */
    fun searchAllDbs(
        context: Context,
        allDbs: List<String>,
        keyword: String,
        limitPerDb: Int = 10,
    ): List<SearchResultItem> {
        val q = keyword.trim()
        if (q.isEmpty()) return emptyList()
        val like = "%$q%"
        val results = mutableListOf<SearchResultItem>()
        for (dbName in allDbs) {
            val path = ensureCopied(context, dbName)
            try {
                SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    // 检查 words 和 trans 表是否存在
                    if (!tableExists(db, "words")) return@use
                    val hasTrans = tableExists(db, "trans")

                    // 先取出本库命中的单词，再用一次 IN 批量取释义，
                    // 避免旧实现「每个单词一次 rawQuery」的 N+1 查询
                    val heads = mutableListOf<Pair<String, String>>()
                    db.rawQuery(
                        "SELECT word_id, head_word FROM words WHERE head_word LIKE ? " +
                            "ORDER BY word_rank LIMIT ?",
                        arrayOf(like, limitPerDb.toString())
                    ).use { wc ->
                        while (wc.moveToNext()) {
                            val wordId = wc.getString(0) ?: continue
                            val headWord = wc.getString(1) ?: continue
                            heads.add(wordId to headWord)
                        }
                    }
                    if (heads.isEmpty()) return@use

                    val tranMap = mutableMapOf<String, String?>()
                    if (hasTrans) {
                        val placeholders = heads.joinToString(",") { "?" }
                        val ids = heads.map { it.first }.toTypedArray()
                        db.rawQuery(
                            "SELECT word_id, tran_cn FROM trans WHERE word_id IN ($placeholders)",
                            ids
                        ).use { tc ->
                            while (tc.moveToNext()) {
                                val wid = tc.getString(0) ?: continue
                                if (wid !in tranMap) tranMap[wid] = tc.getString(1)
                            }
                        }
                    }
                    heads.forEach { (wordId, headWord) ->
                        results.add(
                            SearchResultItem(
                                wordId = wordId,
                                headWord = headWord,
                                tranCn = tranMap[wordId],
                                dbName = dbName.removeSuffix(".db"),
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // 单个 db 出错不影响其他
            }
        }
        // 按单词字母排序
        return results.sortedBy { it.headWord.lowercase() }
    }


    /** 获取所有用户表 */
    fun getTables(db: SQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null
        ).use { c -> while (c.moveToNext()) tables.add(c.getString(0)) }
        return tables
    }

    /** 执行任意 SELECT，返回结果。SQL 非法时返回空结果而不是抛异常 */
    fun runQuery(db: SQLiteDatabase, sql: String): QueryResult = runQuery(db, sql, null)

    /** 执行参数化 SELECT，返回结果。SQL 非法时返回空结果而不是抛异常 */
    fun runQuery(db: SQLiteDatabase, sql: String, args: Array<String>?): QueryResult {
        return try {
            db.rawQuery(sql, args).use { cursor ->
                val columns = cursor.columnNames.toList()
                val rows = ArrayList<List<String?>>(cursor.count.coerceAtLeast(0))
                while (cursor.moveToNext()) {
                    rows.add((0 until cursor.columnCount).map { i ->
                        // 列可能是 BLOB / 超长文本，取值失败按 null 处理
                        try {
                            cursor.getString(i)
                        } catch (_: Exception) {
                            null
                        }
                    })
                }
                QueryResult(columns, rows)
            }
        } catch (_: Exception) {
            QueryResult(emptyList(), emptyList())
        }
    }

    /**
     * 浏览某张表。
     *
     * 表名来自被打开的第三方 db 的 sqlite_master，属于不可信输入，
     * 因此做白名单校验（旧实现用反引号包裹，表名里再带反引号即可注入），
     * LIMIT / OFFSET 统一用参数绑定。
     */
    fun browseTable(
        db: SQLiteDatabase,
        table: String,
        limit: Int = 100,
        offset: Int = 0,
    ): QueryResult {
        if (!SAFE_TABLE_NAME.matches(table)) return QueryResult(emptyList(), emptyList())
        val safeLimit = limit.coerceIn(1, 1000)
        val safeOffset = offset.coerceAtLeast(0)
        return runQuery(
            db,
            "SELECT * FROM \"$table\" LIMIT ? OFFSET ?",
            arrayOf(safeLimit.toString(), safeOffset.toString()),
        )
    }

    /** 获取词汇列表（按 word_rank 排序，关联第一条释义） */
    fun getWordList(db: SQLiteDatabase): List<WordListItem> {
        if (!tableExists(db, "words")) return emptyList()
        // 一次性取所有 word_id -> 第一条中文释义，避免 N+1 查询
        val transMap = mutableMapOf<String, String>()
        if (tableExists(db, "trans")) {
            db.rawQuery("SELECT word_id, tran_cn FROM trans", null).use { c ->
                while (c.moveToNext()) {
                    val wid = c.stringCol("word_id") ?: continue
                    val cn = c.stringCol("tran_cn")
                    if (!transMap.containsKey(wid) && !cn.isNullOrBlank()) transMap[wid] = cn
                }
            }
        }
        val list = mutableListOf<WordListItem>()
        db.rawQuery("SELECT * FROM words ORDER BY word_rank", null).use { c ->
            while (c.moveToNext()) {
                val wordId = c.stringCol("word_id") ?: ""
                list.add(
                    WordListItem(
                        wordId = wordId,
                        headWord = c.stringCol("head_word") ?: "",
                        wordRank = c.intCol("word_rank").let { if (it > 0) it else list.size + 1 },
                        star = c.intCol("star"),
                        tranCn = transMap[wordId],
                    )
                )
            }
        }
        return list
    }

    /** 获取单个单词的完整详情 */
    fun getWordDetail(db: SQLiteDatabase, wordId: String): WordDetail? {
        if (!tableExists(db, "words")) return null
        db.rawQuery("SELECT * FROM words WHERE word_id=?", arrayOf(wordId)).use { wc ->
            if (wc.moveToNext()) {
                return WordDetail(
                    wordId = wordId,
                    headWord = wc.stringCol("head_word") ?: "",
                    usPhone = wc.stringCol("us_phone"),
                    ukPhone = wc.stringCol("uk_phone"),
                    star = wc.intCol("star"),
                    remMethod = (wc.stringCol("rem_method_val") ?: wc.stringCol("rem_method"))
                        ?.takeIf { it.isNotBlank() },
                    trans = queryTrans(db, wordId),
                    phrases = queryPhrases(db, wordId),
                    sentences = querySentences(db, wordId),
                    relWords = queryRelWords(db, wordId),
                    synos = querySynos(db, wordId),
                    antos = queryAntos(db, wordId),
                )
            }
        }
        return null
    }

    /** 按单词（片段）模糊搜索，关联查询翻译/短语/例句等 */
    fun searchWords(db: SQLiteDatabase, keyword: String, limit: Int = 50): List<WordDetail> {
        if (!tableExists(db, "words")) return emptyList()
        val results = mutableListOf<WordDetail>()
        val like = "%$keyword%"
        db.rawQuery(
            "SELECT * FROM words WHERE head_word LIKE ? ORDER BY word_rank LIMIT ?",
            arrayOf(like, limit.toString())
        ).use { wc ->
            while (wc.moveToNext()) {
                val wordId = wc.stringCol("word_id") ?: ""
                results.add(
                    WordDetail(
                        wordId = wordId,
                        headWord = wc.stringCol("head_word") ?: "",
                        usPhone = wc.stringCol("us_phone"),
                        ukPhone = wc.stringCol("uk_phone"),
                        star = wc.intCol("star"),
                        remMethod = (wc.stringCol("rem_method_val") ?: wc.stringCol("rem_method"))
                            ?.takeIf { it.isNotBlank() },
                        trans = queryTrans(db, wordId),
                        phrases = queryPhrases(db, wordId),
                        sentences = querySentences(db, wordId),
                        relWords = queryRelWords(db, wordId),
                        synos = querySynos(db, wordId),
                        antos = queryAntos(db, wordId),
                    )
                )
            }
        }
        return results
    }

    // 表结构在连接生命周期内不变，缓存探测结果可避免每条查询都打 sqlite_master。
    // WeakHashMap：连接被回收后缓存条目自动清理，不会泄漏。
    private val tableCache = java.util.WeakHashMap<SQLiteDatabase, MutableMap<String, Boolean>>()

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        synchronized(tableCache) {
            val cache = tableCache.getOrPut(db) { mutableMapOf() }
            cache[table]?.let { return it }
            val exists = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(table)
            ).use { it.moveToFirst() }
            cache[table] = exists
            return exists
        }
    }

    private fun queryTrans(db: SQLiteDatabase, wordId: String): List<TransItem> {
        if (!tableExists(db, "trans")) return emptyList()
        val list = mutableListOf<TransItem>()
        db.rawQuery("SELECT * FROM trans WHERE word_id=?", arrayOf(wordId)).use { c ->
            while (c.moveToNext()) list.add(TransItem(c.stringCol("pos"), c.stringCol("tran_cn")))
        }
        return list
    }

    private fun queryPhrases(db: SQLiteDatabase, wordId: String): List<PhraseItem> {
        if (!tableExists(db, "phrases")) return emptyList()
        val list = mutableListOf<PhraseItem>()
        db.rawQuery("SELECT * FROM phrases WHERE word_id=?", arrayOf(wordId)).use { c ->
            while (c.moveToNext()) list.add(PhraseItem(c.stringCol("p_content"), c.stringCol("p_cn")))
        }
        return list
    }

    private fun querySentences(db: SQLiteDatabase, wordId: String): List<SentenceItem> {
        if (!tableExists(db, "sentences")) return emptyList()
        val list = mutableListOf<SentenceItem>()
        db.rawQuery("SELECT * FROM sentences WHERE word_id=?", arrayOf(wordId)).use { c ->
            while (c.moveToNext()) list.add(SentenceItem(c.stringCol("s_content"), c.stringCol("s_cn")))
        }
        return list
    }

    private fun queryRelWords(db: SQLiteDatabase, wordId: String): List<RelWordItem> {
        if (!tableExists(db, "rel_words")) return emptyList()
        val list = mutableListOf<RelWordItem>()
        db.rawQuery("SELECT * FROM rel_words WHERE word_id=?", arrayOf(wordId)).use { c ->
            while (c.moveToNext())
                list.add(RelWordItem(c.stringCol("pos"), c.stringCol("hwd"), c.stringCol("tran")))
        }
        return list
    }

    private fun querySynos(db: SQLiteDatabase, wordId: String): List<SynoItem> {
        if (!tableExists(db, "synos")) return emptyList()
        val list = mutableListOf<SynoItem>()
        db.rawQuery("SELECT * FROM synos WHERE word_id=?", arrayOf(wordId)).use { c ->
            while (c.moveToNext()) list.add(SynoItem(c.stringCol("pos"), c.stringCol("tran")))
        }
        return list
    }

    private fun queryAntos(db: SQLiteDatabase, wordId: String): List<String> {
        if (!tableExists(db, "antos")) return emptyList()
        val list = mutableListOf<String>()
        db.rawQuery("SELECT * FROM antos WHERE word_id=?", arrayOf(wordId)).use { c ->
            while (c.moveToNext()) list.add(c.stringCol("hwd") ?: "")
        }
        return list
    }

    /** 按列名取字符串，列不存在返回 null */
    private fun Cursor.stringCol(name: String): String? {
        val i = getColumnIndex(name)
        return if (i >= 0) getString(i) else null
    }

    /** 按列名取整数，列不存在返回默认值 */
    private fun Cursor.intCol(name: String, default: Int = 0): Int {
        val i = getColumnIndex(name)
        return if (i >= 0) getInt(i) else default
    }
}

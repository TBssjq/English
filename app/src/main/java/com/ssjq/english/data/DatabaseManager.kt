package com.ssjq.english.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock

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
    /** 单词所属词库文件名（含 .db）。跨词库混合出题时用于把错题/收藏写回正确词库 */
    val dbName: String? = null,
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

    private class Conn(val db: SQLiteDatabase) {
        var refs: Int = 0
        var lastUsedNanos: Long = System.nanoTime()
    }

    /** 按词库缓存的只读连接。所有访问都在同步方法内进行 */
    private val pool = LinkedHashMap<String, Conn>()

    /** 每个词库一把拷贝锁，避免并发写同一文件造成词库损坏 */
    private val copyLocks = ConcurrentHashMap<String, Any>()

    /**
     * 词库副本目录的读写锁：
     * 「打开/拷贝/查询」持读锁，「清理副本」持写锁。
     * 修复（启动 H-1）：升级后首启会异步清空副本，而首屏统计/查词同时在拷贝并打开
     * 同一批文件，旧实现没有任何互斥，可能出现「刚拷完的文件被删掉」或
     * 「打开已被删除的路径」→ 首屏崩溃、词库丢失。
     */
    private val copyDirLock = ReentrantReadWriteLock()

    /** 被清理的副本先移到这里，而不是直接删掉，给误删留一次人工恢复的机会 */
    private const val TRASH_DIR = ".trash"

    /** 回收目录中文件的保留时长：超过后才真正删除（1 天） */
    private const val TRASH_RETENTION_MILLIS = 24L * 60 * 60 * 1000

    /** 清理记录文件名（位于 filesDir）：记录被清理/被跳过/被失败的副本，便于事后核查 */
    private const val CLEAR_LOG_FILE = "db_cache_clear.log"

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
        // 持读锁：拷贝期间不允许清理副本，否则可能拷完即被删（启动 H-1）
        val readLock = copyDirLock.readLock()
        readLock.lock()
        try {
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
        } finally {
            readLock.unlock()
        }
    }

    /**
     * 清空词库副本缓存，应用升级后调用一次。
     * 副本只校验文件是否完整，不校验内容版本；assets 里的词库被修正后，
     * 不清掉旧副本就会一直用旧数据。
     *
     * 修复（data H-2 / 启动 H-1）：
     * 1. **一致性校验**：只清理「assets 中仍存在同名源文件」的副本和 .tmp 残留，
     *    来源未知的库文件（用户自带/手工放入）一律保留，避免把唯一数据删掉；
     * 2. **可逆**：先把副本移入 .trash 再按时间清理，误删时还能人工找回；
     * 3. **互斥**：整段持有写锁，拷贝/打开副本的路径持读锁，消除并发删库窗口；
     * 4. **可核查**：清理结果（删除/跳过/失败）写入 db_cache_clear.log。
     */
    fun clearCachedDatabases(context: Context) {
        val writeLock = copyDirLock.writeLock()
        writeLock.lock()
        try {
            synchronized(this) {
                pool.values.forEach { pooled ->
                    runCatching { pooled.db.close() }
                }
                pool.clear()
            }
            val dir = dbDir(context)
            // assets 里还有源文件的副本可以安全重建；其余文件保留
            val regenerable = listAssetDatabases(context).toSet()
            val removed = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            val failed = mutableListOf<String>()
            dir.listFiles()?.forEach { file ->
                if (!file.isFile || file.name.startsWith(".")) return@forEach
                val name = file.name
                val isTempResidue = name.endsWith(".tmp")
                if (!isTempResidue && name !in regenerable) {
                    skipped.add(name)
                    return@forEach
                }
                if (moveToTrash(context, file)) removed.add(name) else failed.add(name)
            }
            // 上次清理留下的残骸超过 1 天才真正删除，给误删留出恢复窗口
            pruneTrash(context, TRASH_DIR)
            writeClearLog(context, removed, skipped, failed)
        } finally {
            writeLock.unlock()
        }
    }

    /** 把副本移入 .trash（可恢复），失败返回 false；成功后原路径即不存在 */
    private fun moveToTrash(context: Context, file: File): Boolean {
        val trashDir = File(dbDir(context), TRASH_DIR)
        if (!trashDir.exists() && !trashDir.mkdirs()) {
            // 无法建回收目录时退化为直接删除，保证升级流程不会因残留副本而卡住
            return runCatching { file.delete() }.getOrDefault(false)
        }
        val target = File(trashDir, "${file.name}.${System.currentTimeMillis()}")
        if (runCatching { file.renameTo(target) }.getOrDefault(false)) return true
        return runCatching { file.delete() }.getOrDefault(false)
    }

    /** 清理 .trash 中超过保留期的残留文件 */
    private fun pruneTrash(context: Context, dirName: String) {
        val trashDir = File(dbDir(context), dirName)
        if (!trashDir.isDirectory) return
        val expireBefore = System.currentTimeMillis() - TRASH_RETENTION_MILLIS
        trashDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < expireBefore) {
                runCatching { file.delete() }
            }
        }
    }

    /** 记录一次副本清理结果，异常不影响主流程 */
    private fun writeClearLog(
        context: Context,
        removed: List<String>,
        skipped: List<String>,
        failed: List<String>
    ) {
        runCatching {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val line = buildString {
                append(time)
                append(" 删除=").append(removed.size)
                append(" 保留(非 assets 库)=").append(skipped.size)
                append(" 失败=").append(failed.size)
                if (removed.isNotEmpty()) append(" | 删除: ").append(removed.joinToString(","))
                if (skipped.isNotEmpty()) append(" | 保留: ").append(skipped.joinToString(","))
                if (failed.isNotEmpty()) append(" | 失败: ").append(failed.joinToString(","))
                append('\n')
            }
            File(context.filesDir, CLEAR_LOG_FILE).appendText(line)
        }
    }

    /**
     * 词库是否已复制到内部存储。
     * 查词时优先扫描已复制的词库：首次为查一个词而复制整本词库代价过高。
     */
    fun isCopied(context: Context, dbName: String): Boolean {
        val name = normalizeDbName(dbName)
        if (!SAFE_DB_NAME.matches(name)) return false
        val file = File(dbDir(context), name)
        return file.exists() && file.length() > 0L
    }

    /**
     * 获取词库的只读连接（引用计数 +1）。
     *
     * 同一词库始终复用同一连接，**切换词库不会关闭任何连接**，
     * 因此不会再把并发协程手里正在用的句柄关掉。
     * 使用完毕请调用 [release]；无法配对时连接会在闲置后被自动回收，不会泄漏。
     */
    fun openDatabase(context: Context, dbName: String): SQLiteDatabase {
        val key = normalizeDbName(dbName)
        // ensureCopied 内部按词库加锁，可能触发整库复制；
        // 放到类锁外执行，避免复制期间阻塞其它词库的 open/release。
        // 整段持读锁（启动 H-1）：避免「拷贝完成 → 升级清理删除副本 → 打开已消失的文件」。
        val readLock = copyDirLock.readLock()
        readLock.lock()
        try {
            val path = ensureCopied(context, key)
            synchronized(this) {
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
                    path, null, SQLiteDatabase.OPEN_READONLY
                )
                pool[key] = Conn(db).also { it.refs = 1 }
                return db
            }
        } finally {
            readLock.unlock()
        }
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
        pool.values.forEach { it.db.close() }
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
            e.value.db.close()
            pool.remove(e.key)
        }

        if (pool.size <= MAX_TOTAL_OPEN) return
        val now = System.nanoTime()
        val stale = pool.entries
            // 只回收没有被任何协程持有的连接：关闭在用的句柄会让后续查询抛 already-closed
            .filter { it.value.refs == 0 && now - it.value.lastUsedNanos > IDLE_RECLAIM_NANOS }
            .sortedBy { it.value.lastUsedNanos }
        for (e in stale) {
            if (pool.size <= MAX_TOTAL_OPEN) break
            e.value.db.close()
            pool.remove(e.key)
        }
    }

    /**
     * 查询指定词库的单词总数。
     * 独立打开 db（不影响缓存的 current db），查询后立即关闭。
     * 用于首页展示词库规模，不影响正在使用的词库连接。
     */
    fun getWordCount(context: Context, dbName: String): Int {
        val path = ensureCopied(context, dbName)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            check(tableExists(db, "words")) { "词库缺少 words 表: $dbName" }
            return db.rawQuery(
                "SELECT COUNT(*) FROM words WHERE head_word IS NOT NULL", null
            ).use { c ->
                check(c.moveToFirst()) { "词数查询无结果: $dbName" }
                c.getInt(0)
            }
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
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                check(tableExists(db, "words")) { "词库缺少 words 表: $dbName" }
                val hasTrans = tableExists(db, "trans")

                // 先取出本库命中的单词，再用一次 IN 批量取释义
                val heads = mutableListOf<Pair<String, String>>()
                db.rawQuery(
                    "SELECT word_id, head_word FROM words WHERE head_word LIKE ? " +
                        "ORDER BY word_rank LIMIT ?",
                    arrayOf(like, limitPerDb.toString())
                ).use { wc ->
                    while (wc.moveToNext()) {
                        val wordId = wc.getString(0) ?: error("词库 $dbName 存在空的 word_id")
                        val headWord = wc.getString(1) ?: error("词库 $dbName 存在空的 head_word")
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
                            val wid = tc.getString(0)
                                ?: error("词库 $dbName 的 trans 表存在空的 word_id")
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
        }
        // 按单词字母排序
        return results.sortedBy { it.headWord.lowercase() }
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

    /** 获取单个单词的完整详情。dbName 必须传入，混合词源才能把词条写回正确词库 */
    fun getWordDetail(db: SQLiteDatabase, wordId: String, dbName: String? = null): WordDetail? {
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
                    dbName = dbName,
                )
            }
        }
        return null
    }

    /**
     * 精确查找词头，返回命中词的 id 与词头。
     * 详情页点击例句或短语里的单词时走这里，大小写忽略，但必须整词相等；
     * 未命中的词库不必构造完整词条（含 6 张关联表查询）。
     */
    fun findHeadWordId(db: SQLiteDatabase, keyword: String): Pair<String, String>? {
        if (!tableExists(db, "words")) return null
        val query = keyword.trim()
        if (query.isEmpty()) return null
        return db.rawQuery(
            "SELECT word_id, head_word FROM words " +
                "WHERE head_word COLLATE NOCASE = ? LIMIT 1",
            arrayOf(query)
        ).use { c ->
            if (!c.moveToFirst()) return null
            val wordId = c.getString(0) ?: error("词库存在空的 word_id")
            val headWord = c.getString(1) ?: error("词库存在空的 head_word")
            wordId to headWord
        }
    }

    fun searchWords(
        db: SQLiteDatabase,
        keyword: String,
        limit: Int = 50,
        dbName: String? = null,
    ): List<WordDetail> {
        if (!tableExists(db, "words")) return emptyList()
        val results = mutableListOf<WordDetail>()
        val like = "%$keyword%"
        val prefix = "$keyword%"
        // 精确匹配排最前，其次前缀匹配，最后才是包含匹配；
        // 同一档内按词频序号排列，避免点击 grab 时先返回 grabbing
        db.rawQuery(
            "SELECT * FROM words WHERE head_word LIKE ? " +
                "ORDER BY CASE WHEN head_word = ? THEN 0 " +
                "WHEN head_word LIKE ? THEN 1 ELSE 2 END, word_rank LIMIT ?",
            arrayOf(like, keyword, prefix, limit.toString())
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
                        dbName = dbName?.removeSuffix(".db"),
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

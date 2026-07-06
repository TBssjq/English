package com.ssjq.english.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

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
 */
object DatabaseManager {

    private const val DB_DIR = "query_dbs"

    @Volatile
    private var current: SQLiteDatabase? = null
    private var currentName: String? = null

    private fun dbDir(context: Context): File {
        val dir = File(context.cacheDir, DB_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 列出 assets 下所有 .db 文件 */
    fun listAssetDatabases(context: Context): List<String> =
        context.assets.list("")?.filter { it.endsWith(".db") }?.sorted() ?: emptyList()

    /** 把指定 db 从 assets 复制到缓存目录（已存在则跳过），返回可打开的文件路径 */
    private fun ensureCopied(context: Context, dbName: String): String {
        val target = File(dbDir(context), dbName)
        if (!target.exists() || target.length() == 0L) {
            context.assets.open(dbName).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target.absolutePath
    }

    @Synchronized
    fun openDatabase(context: Context, dbName: String): SQLiteDatabase {
        current?.let {
            if (currentName == dbName) {
                if (!it.isOpen) {
                    current = null
                    currentName = null
                } else {
                    return it
                }
            } else {
                it.close()
                current = null
                currentName = null
            }
        }
        val db = SQLiteDatabase.openDatabase(
            ensureCopied(context, dbName), null, SQLiteDatabase.OPEN_READONLY
        )
        current = db
        currentName = dbName
        return db
    }

    @Synchronized
    fun closeDatabase() {
        current?.close()
        current = null
        currentName = null
    }

    /**
     * 查询指定词库的单词总数。
     * 独立打开 db（不影响缓存的 current db），查询后立即关闭。
     * 用于首页展示词库规模，不影响正在使用的词库连接。
     */
    fun getWordCount(context: Context, dbName: String): Int {
        val path = ensureCopied(context, dbName)
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT COUNT(*) FROM words WHERE head_word IS NOT NULL", null
            ).use { c ->
                if (c.moveToFirst()) return c.getInt(0)
            }
        }
        return 0
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
                    val hasWords = tableExistsInternal(db, "words")
                    if (!hasWords) return@use
                    val hasTrans = tableExistsInternal(db, "trans")

                    db.rawQuery(
                        "SELECT word_id, head_word FROM words WHERE head_word LIKE ? " +
                            "ORDER BY word_rank LIMIT ?",
                        arrayOf(like, limitPerDb.toString())
                    ).use { wc ->
                        while (wc.moveToNext()) {
                            val wordId = wc.getString(0) ?: continue
                            val headWord = wc.getString(1) ?: continue
                            val tranCn = if (hasTrans) {
                                db.rawQuery(
                                    "SELECT tran_cn FROM trans WHERE word_id=? LIMIT 1",
                                    arrayOf(wordId)
                                ).use { tc ->
                                    if (tc.moveToFirst()) tc.getString(0) else null
                                }
                            } else null
                            results.add(
                                SearchResultItem(
                                    wordId = wordId,
                                    headWord = headWord,
                                    tranCn = tranCn,
                                    dbName = dbName.removeSuffix(".db"),
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // 单个 db 出错不影响其他
            }
        }
        // 按单词字母排序
        return results.sortedBy { it.headWord.lowercase() }
    }

    private fun tableExistsInternal(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { it.moveToFirst() }

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

    /** 执行任意 SELECT，返回结果 */
    fun runQuery(db: SQLiteDatabase, sql: String): QueryResult {
        db.rawQuery(sql, null).use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = ArrayList<List<String?>>(cursor.count)
            while (cursor.moveToNext()) {
                rows.add((0 until cursor.columnCount).map { cursor.getString(it) })
            }
            return QueryResult(columns, rows)
        }
    }

    /** 浏览某张表 */
    fun browseTable(db: SQLiteDatabase, table: String, limit: Int = 100, offset: Int = 0): QueryResult =
        runQuery(db, "SELECT * FROM `$table` LIMIT $limit OFFSET $offset")

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

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { it.moveToFirst() }

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

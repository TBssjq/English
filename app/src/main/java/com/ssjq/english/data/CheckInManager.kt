package com.ssjq.english.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 单日打卡记录。
 * @param date 日期键（yyyy-MM-dd），全应用唯一
 * @param studyMinutes 学习时长（分钟）
 * @param wordsLearned 学习过的单词数（翻过的卡片）
 * @param wordsMastered 标记为「认识」的单词数
 * @param quizCorrect 测验答对题数
 * @param quizTotal 测验总题数
 * @param checkedAt 首次打卡时间戳
 * @param updatedAt 最后更新时间戳
 */
data class CheckInRecord(
    val date: String,
    val studyMinutes: Int = 0,
    val wordsLearned: Int = 0,
    val wordsMastered: Int = 0,
    val quizCorrect: Int = 0,
    val quizTotal: Int = 0,
    val checkedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 打卡汇总统计。
 */
data class CheckInStats(
    val todayRecord: CheckInRecord?,
    val isCheckedInToday: Boolean,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalDays: Int,
    val totalMinutes: Int,
    val totalWordsLearned: Int,
    val totalWordsMastered: Int,
    val totalQuizCorrect: Int,
    val totalQuizTotal: Int,
)

/**
 * 打卡系统：每日学习记录 + 连续天数 + 累计统计。
 * 单例 + SharedPreferences，通过 [init] 初始化。
 *
 * 设计要点：
 * - 自动打卡：学习/测验时调用 [accumulate]，自动累加当天数据，无需手动点击
 * - 连续天数：根据相邻日期计算，断签后归零
 * - 跨天容错：以本地日期（yyyy-MM-dd）为唯一键
 */
object CheckInManager {

    private const val PREF_NAME = "check_in"
    private const val KEY_RECORDS = "check_in_records"

    private lateinit var prefs: SharedPreferences
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 必须在 Application / Activity 启动时调用一次 */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun ensureInit() {
        check(::prefs.isInitialized) { "CheckInManager 未初始化，请先调用 init(context)" }
    }

    /** 今日日期键 */
    fun todayKey(): String = dateFormat.format(Date())

    /** 任意时间戳对应的日期键 */
    fun dateKey(timestamp: Long): String = dateFormat.format(Date(timestamp))

    // ---------- 读写 ----------

    /** 读取全部打卡记录（按日期升序） */
    fun allRecords(): List<CheckInRecord> {
        ensureInit()
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CheckInRecord(
                    date = o.getString("date"),
                    studyMinutes = o.optInt("studyMinutes", 0),
                    wordsLearned = o.optInt("wordsLearned", 0),
                    wordsMastered = o.optInt("wordsMastered", 0),
                    quizCorrect = o.optInt("quizCorrect", 0),
                    quizTotal = o.optInt("quizTotal", 0),
                    checkedAt = o.optLong("checkedAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                )
            }.sortedBy { it.date }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 获取某天的记录，不存在返回 null */
    fun recordOf(date: String): CheckInRecord? = allRecords().find { it.date == date }

    /** 今日记录 */
    fun todayRecord(): CheckInRecord? = recordOf(todayKey())

    /** 今日是否已打卡 */
    fun isCheckedInToday(): Boolean = todayRecord() != null

    // ---------- 累加（自动打卡核心） ----------

    /**
     * 累加今日学习数据。若今日无记录则创建（即「打卡」）。
     * 所有学习行为（翻卡片、标记认识/不认识、完成测验）都调用此方法。
     */
    fun accumulate(
        addMinutes: Int = 0,
        addWordsLearned: Int = 0,
        addWordsMastered: Int = 0,
        addQuizCorrect: Int = 0,
        addQuizTotal: Int = 0,
    ) {
        ensureInit()
        val today = todayKey()
        val list = allRecords().toMutableList()
        val now = System.currentTimeMillis()
        val idx = list.indexOfFirst { it.date == today }
        if (idx >= 0) {
            val cur = list[idx]
            list[idx] = cur.copy(
                studyMinutes = cur.studyMinutes + addMinutes,
                wordsLearned = cur.wordsLearned + addWordsLearned,
                wordsMastered = cur.wordsMastered + addWordsMastered,
                quizCorrect = cur.quizCorrect + addQuizCorrect,
                quizTotal = cur.quizTotal + addQuizTotal,
                updatedAt = now,
            )
        } else {
            list.add(
                CheckInRecord(
                    date = today,
                    studyMinutes = addMinutes,
                    wordsLearned = addWordsLearned,
                    wordsMastered = addWordsMastered,
                    quizCorrect = addQuizCorrect,
                    quizTotal = addQuizTotal,
                    checkedAt = now,
                    updatedAt = now,
                )
            )
        }
        saveList(list.sortedBy { it.date })
    }

    /** 清空所有打卡记录（设置页使用） */
    fun clearAll() {
        ensureInit()
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    // ---------- 统计 ----------

    /** 计算完整统计 */
    fun stats(): CheckInStats {
        val records = allRecords()
        val today = todayKey()
        val todayRec = records.find { it.date == today }
        val streak = calcCurrentStreak(records)
        val longest = calcLongestStreak(records)
        return CheckInStats(
            todayRecord = todayRec,
            isCheckedInToday = todayRec != null,
            currentStreak = streak,
            longestStreak = longest,
            totalDays = records.size,
            totalMinutes = records.sumOf { it.studyMinutes },
            totalWordsLearned = records.sumOf { it.wordsLearned },
            totalWordsMastered = records.sumOf { it.wordsMastered },
            totalQuizCorrect = records.sumOf { it.quizCorrect },
            totalQuizTotal = records.sumOf { it.quizTotal },
        )
    }

    /** 当前连续打卡天数（从今天往回数，今天没打卡则从昨天开始数） */
    private fun calcCurrentStreak(records: List<CheckInRecord>): Int {
        if (records.isEmpty()) return 0
        val dates = records.map { it.date }.toSet()
        val cal = Calendar.getInstance()
        // 如果今天没打卡，从昨天开始算（保持连续性，不因今天还没学而断签）
        if (todayKey() !in dates) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        var streak = 0
        while (dateFormat.format(cal.time) in dates) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    /** 历史最长连续打卡天数 */
    private fun calcLongestStreak(records: List<CheckInRecord>): Int {
        if (records.isEmpty()) return 0
        val sorted = records.sortedBy { it.date }
        var longest = 1
        var cur = 1
        val cal = Calendar.getInstance()
        for (i in 1 until sorted.size) {
            val prev = parseDate(sorted[i - 1].date)
            val now = parseDate(sorted[i].date)
            cal.time = prev
            cal.add(Calendar.DAY_OF_YEAR, 1)
            if (dateFormat.format(cal.time) == dateFormat.format(now)) {
                cur++
                if (cur > longest) longest = cur
            } else {
                cur = 1
            }
        }
        return longest
    }

    private fun parseDate(key: String): Date = try {
        dateFormat.parse(key) ?: Date()
    } catch (_: Exception) {
        Date()
    }

    /** 获取最近 N 天的记录（含今天，按日期升序），缺失日期补空 */
    fun recentDays(n: Int): List<CheckInRecord?> {
        val records = allRecords()
        val map = records.associateBy { it.date }
        val result = mutableListOf<CheckInRecord?>()
        val cal = Calendar.getInstance()
        repeat(n) {
            val key = dateFormat.format(cal.time)
            result.add(0, map[key])
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return result
    }

    private fun saveList(list: List<CheckInRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("date", r.date)
                put("studyMinutes", r.studyMinutes)
                put("wordsLearned", r.wordsLearned)
                put("wordsMastered", r.wordsMastered)
                put("quizCorrect", r.quizCorrect)
                put("quizTotal", r.quizTotal)
                put("checkedAt", r.checkedAt)
                put("updatedAt", r.updatedAt)
            })
        }
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }
}

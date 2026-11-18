package com.ssjq.english.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 间隔重复复习调度。
 * 借鉴开源项目的「答对升级、答错降级、间隔按 2^level 天递增」思路自行实现。
 * 状态保存在独立 SharedPreferences，写入统一经过 [save]，缓存随之更新。
 */
object ReviewScheduler {

    private const val PREF_NAME = "review_schedule"
    private const val KEY_STATES = "states"
    private const val MAX_LEVEL = 8

    data class ReviewState(
        val dbName: String,
        val wordId: String,
        val level: Int,
        val nextReviewDate: String,
        val lastReviewedDate: String?,
        val totalReviews: Int,
        val correctReviews: Int,
    )

    @Volatile
    private var cache: List<ReviewState>? = null

    private fun prefs() = UserLibrary.applicationContext()
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** grade: 0 不认识 / 1 模糊 / 2 认识 */
    fun nextLevel(current: Int, grade: Int): Int {
        require(grade in 0..2) { "复习评分必须是 0..2" }
        require(current >= 0) { "复习等级不能为负数" }
        return when (grade) {
            0 -> 0
            1 -> (current - 1).coerceAtLeast(0)
            else -> (current + 1).coerceAtMost(MAX_LEVEL)
        }
    }

    /** 「模糊」比「认识」短一档，保证同一天内能再见到 */
    fun intervalDays(level: Int, grade: Int): Long {
        require(grade in 0..2) { "复习评分必须是 0..2" }
        require(level >= 0) { "复习等级不能为负数" }
        return when (grade) {
            0 -> 1L
            1 -> 1L shl (level - 1).coerceAtLeast(0)
            else -> 1L shl level.coerceAtMost(MAX_LEVEL)
        }
    }

    /**
     * 词库名统一去掉 .db 后缀。
     * 路由里既有 "CET4_1" 也有 "CET4_1.db" 两种写法，不统一会让同一词库分裂出两套记录。
     */
    private fun canonicalDb(name: String): String = name.removeSuffix(".db")

    private fun allStates(): List<ReviewState> {
        cache?.let { return it }
        val raw = prefs().getString(KEY_STATES, null)
        if (raw == null) {
            cache = emptyList()
            return emptyList()
        }
        val arr = JSONArray(raw)
        val list = ArrayList<ReviewState>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val dbName = canonicalDb(o.optString("dbName"))
            val wordId = o.optString("wordId")
            val next = o.optString("nextReviewDate")
            // 主键或到期日缺失的记录跳过：空到期日会被判成「永远到期」
            if (wordId.isBlank() || dbName.isBlank() || next.isBlank()) continue
            list.add(
                ReviewState(
                    dbName = dbName,
                    wordId = wordId,
                    level = o.optInt("level", 0),
                    nextReviewDate = next,
                    lastReviewedDate = o.optString("lastReviewedDate").takeIf { it.isNotBlank() },
                    totalReviews = o.optInt("totalReviews", 0),
                    correctReviews = o.optInt("correctReviews", 0),
                )
            )
        }
        return list.also { cache = it }
    }

    private fun save(list: List<ReviewState>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("dbName", s.dbName)
                    put("wordId", s.wordId)
                    put("level", s.level)
                    put("nextReviewDate", s.nextReviewDate)
                    put("lastReviewedDate", s.lastReviewedDate ?: "")
                    put("totalReviews", s.totalReviews)
                    put("correctReviews", s.correctReviews)
                }
            )
        }
        prefs().edit { putString(KEY_STATES, arr.toString()) }
        cache = list
    }

    @Synchronized
    fun record(dbName: String, wordId: String, grade: Int) {
        val db = canonicalDb(dbName)
        val today = LocalDate.now().toString()
        val states = allStates().toMutableList()
        val idx = states.indexOfFirst { it.dbName == db && it.wordId == wordId }
        val previous = if (idx >= 0) states[idx] else null
        val level = nextLevel(previous?.level ?: 0, grade)
        val updated = ReviewState(
            dbName = db,
            wordId = wordId,
            level = level,
            nextReviewDate = LocalDate.now().plusDays(intervalDays(level, grade)).toString(),
            lastReviewedDate = today,
            totalReviews = (previous?.totalReviews ?: 0) + 1,
            correctReviews = (previous?.correctReviews ?: 0) + if (grade == 2) 1 else 0,
        )
        if (idx >= 0) states[idx] = updated else states.add(updated)
        save(states)
    }

    /** 今日到期且今天尚未复习过的单词，按到期日升序 */
    fun dueWordIds(dbName: String): List<String> {
        val db = canonicalDb(dbName)
        val today = LocalDate.now().toString()
        return allStates()
            .filter { it.dbName == db && it.nextReviewDate <= today && it.lastReviewedDate != today }
            .sortedBy { it.nextReviewDate }
            .map { it.wordId }
    }

    fun dueCount(dbName: String): Int = dueWordIds(dbName).size

    fun stateOf(dbName: String, wordId: String): ReviewState? {
        val db = canonicalDb(dbName)
        return allStates().firstOrNull { it.dbName == db && it.wordId == wordId }
    }
}

package com.ssjq.english.data

import android.content.Context
import androidx.core.content.edit
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UserManager {

    private const val PREF_NAME = "user_manager"
    private const val KEY_USERNAME = "username"
    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_GLASS_EFFECT = "glass_effect"
    private const val KEY_LAST_STUDY_DB = "last_study_db"
    private const val KEY_CURRENT_BOOK = "current_book"
    private const val KEY_ANNOUNCEMENT_SEEN = "announcement_seen_key"

    private const val KEY_QUIZ_RECORDS = "quiz_records"
    private const val KEY_QUIZ_PROGRESS = "quiz_progress"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_DAILY_GOAL = "daily_goal"

    /** 每日学习目标可选项（单词数） */
    val DAILY_GOAL_OPTIONS = listOf(10, 20, 30, 50, 100)

    const val DEFAULT_DAILY_GOAL = 20

    @Volatile
    private var appContext: Context? = null
    private val _darkThemeFlow = MutableStateFlow<Boolean?>(null)
    val darkThemeFlow: StateFlow<Boolean?> = _darkThemeFlow.asStateFlow()
    private val _glassEffectFlow = MutableStateFlow(true)
    val glassEffectFlow: StateFlow<Boolean> = _glassEffectFlow.asStateFlow()

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        _darkThemeFlow.value = darkThemeMode()
        _glassEffectFlow.value = glassEffectEnabled()
    }

    /**
     * 惰性取 SharedPreferences。
     * 旧实现用 `lateinit var prefs`，init 之前调用任一方法都会抛
     * UninitializedPropertyAccessException 直接崩溃。
     */
    private val prefs: SharedPreferences
        get() = appContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            ?: error("UserManager 未初始化，请先调用 init(context)")

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun markLaunched() {
        prefs.edit { putBoolean(KEY_FIRST_LAUNCH, false) }
    }

    /**
     * 上一次已经展示过的公告标识（[com.ssjq.english.data.NoticeData.seenKey]）。
     * 用标识而不是布尔值：作者换一条公告后，老用户仍然会看到新公告。
     */
    fun announcementSeenKey(): String? = prefs.getString(KEY_ANNOUNCEMENT_SEEN, null)

    fun setAnnouncementSeenKey(key: String) {
        prefs.edit { putString(KEY_ANNOUNCEMENT_SEEN, key) }
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun setUsername(username: String) {
        prefs.edit { putString(KEY_USERNAME, username.trim()) }
    }

    fun hasUsername(): Boolean {
        return getUsername()?.isNotBlank() == true
    }

    fun isRegistered(): Boolean {
        return !isFirstLaunch() && hasUsername()
    }

    /**
     * 暗色主题偏好：
     * - null / 未设置：跟随系统 + 日出日落自动切换
     * - true：强制暗色
     * - false：强制亮色
     */
    fun darkThemeMode(): Boolean? {
        if (!prefs.contains(KEY_DARK_THEME)) return null
        return prefs.getBoolean(KEY_DARK_THEME, false)
    }

    fun setDarkThemeMode(enabled: Boolean?) {
        if (enabled == null) {
            prefs.edit { remove(KEY_DARK_THEME) }
        } else {
            prefs.edit { putBoolean(KEY_DARK_THEME, enabled) }
        }
        _darkThemeFlow.value = enabled
    }

    /**
     * 玻璃特效开关。关闭后不再启用折射、饱和度增强与色散，只保留较弱模糊，
     * 供低端设备或用户主动省电时使用。默认开启。
     */
    fun glassEffectEnabled(): Boolean = prefs.getBoolean(KEY_GLASS_EFFECT, true)

    fun setGlassEffectEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_GLASS_EFFECT, enabled) }
        _glassEffectFlow.value = enabled
    }

    /**
     * 记录当前运行的版本号，返回 true 表示这是升级后的第一次启动。
     * 调用方据此刷新由 assets 派生、但不受版本约束的缓存。
     */
    fun markVersionChecked(versionCode: Long): Boolean {
        val last = prefs.getLong(KEY_LAST_VERSION_CODE, 0L)
        if (last == versionCode) return false
        prefs.edit { putLong(KEY_LAST_VERSION_CODE, versionCode) }
        return true
    }

    /** 每日学习目标（单词数），默认 20 */
    fun getDailyGoal(): Int {
        val goal = prefs.getInt(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL)
        return if (goal > 0) goal else DEFAULT_DAILY_GOAL
    }

    fun setDailyGoal(goal: Int) {
        require(goal > 0) { "每日目标必须大于 0" }
        prefs.edit { putInt(KEY_DAILY_GOAL, goal) }
    }

    fun getLastStudyDb(): String? = prefs.getString(KEY_LAST_STUDY_DB, null)

    fun setLastStudyDb(dbName: String) {
        prefs.edit { putString(KEY_LAST_STUDY_DB, dbName) }
    }

    /**
     * 当前选定的学习词库（注册时选择，之后可随时更换）。
     * 返回 null 表示尚未选择（老用户升级后即为该情况，此时沿用旧的完整列表）。
     */
    fun getCurrentBook(): String? = prefs.getString(KEY_CURRENT_BOOK, null)

    fun setCurrentBook(dbName: String) {
        prefs.edit { putString(KEY_CURRENT_BOOK, dbName) }
    }

    fun clearCurrentBook() {
        prefs.edit { remove(KEY_CURRENT_BOOK) }
    }

    /** 解析刷题记录数组；损坏内容按空数组处理 */
    private fun parseQuizRecords(): org.json.JSONArray {
        val raw = prefs.getString(KEY_QUIZ_RECORDS, null) ?: return org.json.JSONArray()
        return runCatching { org.json.JSONArray(raw) }.getOrDefault(org.json.JSONArray())
    }

    /**
     * 保存刷题记录
     * @param record JSON 格式的记录字符串
     */
    @Synchronized
    fun saveQuizRecord(record: String) {
        val records = parseQuizRecords()
        val obj = org.json.JSONObject(record)
        records.put(obj)

        // 只保留最近 100 条记录
        val trimmed = org.json.JSONArray()
        val from = (records.length() - 100).coerceAtLeast(0)
        for (i in from until records.length()) {
            trimmed.put(records.get(i))
        }
        prefs.edit { putString(KEY_QUIZ_RECORDS, trimmed.toString()) }
    }

    /** 读取全部刷题记录（JSON 数组字符串），无记录或损坏时返回 "[]" */
    fun getQuizRecords(): String = parseQuizRecords().toString()

    /** 保存测验断点；同参数下次进入时用它重建同一套题并回到原题号 */
    fun saveQuizProgress(json: String) {
        prefs.edit { putString(KEY_QUIZ_PROGRESS, json) }
    }

    fun getQuizProgress(): String? = prefs.getString(KEY_QUIZ_PROGRESS, null)

    fun clearQuizProgress() {
        prefs.edit { remove(KEY_QUIZ_PROGRESS) }
    }

    /** 最近一次测验记录，用于结果页回顾与对比 */
    fun getLastQuizRecord(): QuizRecordSummary? {
        val arr = parseQuizRecords()
        if (arr.length() == 0) return null
        val o = arr.optJSONObject(arr.length() - 1) ?: return null
        return QuizRecordSummary(
            timestamp = o.optLong("timestamp", 0L),
            accuracy = o.optInt("accuracy", 0),
            total = o.optInt("totalQuestions", 0),
            correct = o.optInt("correctCount", 0),
        )
    }
}

/** 测验记录摘要，避免界面层直接解析 JSON */
data class QuizRecordSummary(
    val timestamp: Long,
    val accuracy: Int,
    val total: Int,
    val correct: Int,
)

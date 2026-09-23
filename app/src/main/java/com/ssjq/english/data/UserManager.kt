package com.ssjq.english.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UserManager {

    private const val PREF_NAME = "user_manager"
    private const val KEY_USERNAME = "username"
    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_LAST_STUDY_DB = "last_study_db"
    private const val KEY_CURRENT_BOOK = "current_book"
    private const val KEY_ANNOUNCEMENT_SEEN = "announcement_seen"

    private const val KEY_QUIZ_RECORDS = "quiz_records"

    @Volatile
    private var appContext: Context? = null
    private val _darkThemeFlow = MutableStateFlow<Boolean?>(null)
    val darkThemeFlow: StateFlow<Boolean?> = _darkThemeFlow.asStateFlow()

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        _darkThemeFlow.value = darkThemeMode()
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
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    /** 是否已展示过首启公告（用于首次进入强制拉取一次） */
    fun hasSeenAnnouncement(): Boolean = prefs.getBoolean(KEY_ANNOUNCEMENT_SEEN, false)

    fun markAnnouncementSeen() {
        prefs.edit().putBoolean(KEY_ANNOUNCEMENT_SEEN, true).apply()
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun setUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username.trim()).apply()
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
            prefs.edit().remove(KEY_DARK_THEME).apply()
        } else {
            prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
        }
        _darkThemeFlow.value = enabled
    }

    fun getLastStudyDb(): String? = prefs.getString(KEY_LAST_STUDY_DB, null)

    fun setLastStudyDb(dbName: String) {
        prefs.edit().putString(KEY_LAST_STUDY_DB, dbName).apply()
    }

    /**
     * 当前选定的学习词库（注册时选择，之后可随时更换）。
     * 返回 null 表示尚未选择（老用户升级后即为该情况，此时沿用旧的完整列表）。
     */
    fun getCurrentBook(): String? = prefs.getString(KEY_CURRENT_BOOK, null)

    fun setCurrentBook(dbName: String) {
        prefs.edit().putString(KEY_CURRENT_BOOK, dbName).apply()
    }

    fun clearCurrentBook() {
        prefs.edit().remove(KEY_CURRENT_BOOK).apply()
    }

    /**
     * 保存刷题记录
     * @param record JSON 格式的记录字符串
     */
    @Synchronized
    fun saveQuizRecord(record: String) {
        try {
            // 已存数据损坏时重置为空数组；旧实现无 try/catch，
            // 一旦 quiz_records 变成非法 JSON，之后每次答题结束都会抛
            // JSONException 崩溃，刷题记录功能永久不可用且无法自愈。
            val records = try {
                org.json.JSONArray(prefs.getString(KEY_QUIZ_RECORDS, null) ?: "[]")
            } catch (_: Exception) {
                org.json.JSONArray()
            }
            // 记录本身非法则跳过写入，不影响答题主流程
            val obj = try {
                org.json.JSONObject(record)
            } catch (_: Exception) {
                return
            }
            records.put(obj)

            // 只保留最近 100 条记录
            val trimmed = org.json.JSONArray()
            val from = (records.length() - 100).coerceAtLeast(0)
            for (i in from until records.length()) {
                trimmed.put(records.get(i))
            }
            prefs.edit().putString(KEY_QUIZ_RECORDS, trimmed.toString()).apply()
        } catch (_: Exception) {
            // 持久化失败不应打断答题流程
        }
    }

    /** 读取全部刷题记录（JSON 数组字符串），无记录或损坏时返回 "[]" */
    fun getQuizRecords(): String {
        val raw = prefs.getString(KEY_QUIZ_RECORDS, null) ?: return "[]"
        // raw 本身就是合法 JSON 数组字符串，直接返回即可，
        // 旧实现 JSONArray(raw).toString() 会白白解析再序列化一遍
        return try {
            org.json.JSONArray(raw)
            raw
        } catch (_: Exception) {
            "[]"
        }
    }
}

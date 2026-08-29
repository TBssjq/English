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

    private lateinit var prefs: SharedPreferences
    private val _darkThemeFlow = MutableStateFlow<Boolean?>(null)
    val darkThemeFlow: StateFlow<Boolean?> = _darkThemeFlow.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _darkThemeFlow.value = darkThemeMode()
    }

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
    fun saveQuizRecord(record: String) {
        val existing = prefs.getString("quiz_records", "[]") ?: "[]"
        val records = org.json.JSONArray(existing)
        records.put(org.json.JSONObject(record))
        // 只保留最近 100 条记录
        val trimmed = if (records.length() > 100) {
            val newRecords = org.json.JSONArray()
            for (i in records.length() - 100 until records.length()) {
                newRecords.put(records.get(i))
            }
            newRecords.toString()
        } else {
            records.toString()
        }
        prefs.edit().putString("quiz_records", trimmed).apply()
    }
}

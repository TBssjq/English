package com.ssjq.english.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.ssjq.english.data.DatabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * 学习状态前台服务：在后台维持学习状态并实时更新通知。
 *
 * 核心职责：
 * 1. 启动时加载词库，进入复习流
 * 2. 每次切换单词，通过 NotificationManager.notify() 更新自定义通知
 * 3. 接收 NotificationActionReceiver 转发的按钮事件，执行对应操作
 * 4. 退出时清除通知并停止服务
 *
 * Android 14 (API 34) 适配：
 * - 必须声明 FOREGROUND_SERVICE_TYPE，启动时用 startForeground(id, notif, type)
 * - dataSync 类型适用于"学习进度同步"，6 小时上限
 * - mediaPlayback 适用于"听力精听"场景（需播放音频），但要求声明 media 權限
 *   本服务以 dataSync 为主，播放发音用独立 MediaPlayer
 */
class LearningForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    private var dbName: String = ""
    private var words: List<LearningWord> = emptyList()
    private var currentIndex = 0
    private val total get() = words.size

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动前台服务：必须立即调用 startForeground，否则 Android 12+ 会抛出异常
        startForegroundWithType(buildCurrentNotification())

        when (intent?.action) {
            ACTION_START -> {
                val dbName = intent.getStringExtra(EXTRA_DB_NAME) ?: return START_NOT_STICKY
                loadWordsAndStart(dbName)
            }
            ACTION_HANDLE_BUTTON -> {
                // 由 NotificationActionReceiver 转发来的按钮事件
                val buttonAction = intent.getStringExtra(EXTRA_BUTTON_ACTION) ?: return START_NOT_STICKY
                handleButtonAction(buttonAction)
            }
            ACTION_STOP -> stopLearning()
        }
        return START_NOT_STICKY // 被杀后不自动重启，避免幽灵服务
    }

    /**
     * 加载词库并开始复习流。
     * 异步读取数据库，加载完成后显示第一词。
     */
    private fun loadWordsAndStart(dbName: String) {
        this.dbName = dbName
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val db = DatabaseManager.openDatabase(this@LearningForegroundService, dbName)
                val wordList = DatabaseManager.getWordList(db)
                val transMap = mutableMapOf<String, String>()
                if (db.rawQuery("SELECT word_id, pos, tran_cn FROM trans", null).use { c ->
                    while (c.moveToNext()) {
                        val wid = c.getString(c.getColumnIndex("word_id")) ?: continue
                        val pos = c.getString(c.getColumnIndex("pos"))
                        val cn = c.getString(c.getColumnIndex("tran_cn"))
                        if (!transMap.containsKey(wid) && !cn.isNullOrBlank()) {
                            transMap[wid] = listOfNotNull(pos, cn).joinToString("  ")
                        }
                    }
                    true
                }) {
                    wordList.map { item ->
                        LearningWord(
                            wordId = item.wordId,
                            headWord = item.headWord,
                            phonetic = db.rawQuery("SELECT uk_phone, us_phone FROM words WHERE word_id=?", arrayOf(item.wordId)).use { c ->
                                if (c.moveToNext()) {
                                    c.getString(c.getColumnIndex("uk_phone")) ?: c.getString(c.getColumnIndex("us_phone")) ?: ""
                                } else ""
                            },
                            translation = transMap[item.wordId] ?: item.tranCn ?: "",
                        )
                    }
                } else emptyList()
            }.onSuccess { list ->
                words = list
                currentIndex = com.ssjq.english.data.UserLibrary.studyIndex(dbName).coerceAtMost(list.size - 1)
                if (list.isEmpty()) {
                    stopLearning()
                } else {
                    updateNotification()
                }
            }.onFailure {
                Log.e(TAG, "加载词库失败", it)
                stopLearning()
            }
        }
    }

    private fun handleButtonAction(action: String) {
        when (action) {
            NotificationHelper.ACTION_SPEAK -> {
                words.getOrNull(currentIndex)?.let { speak(it.headWord) }
            }
            NotificationHelper.ACTION_WRONG -> {
                markWrong()
                moveToNext()
            }
            NotificationHelper.ACTION_KNOWN -> {
                moveToNext()
            }
            NotificationHelper.ACTION_NEXT -> moveToNext()
            NotificationHelper.ACTION_STOP -> stopLearning()
        }
    }

    private fun markWrong() {
        val word = words.getOrNull(currentIndex) ?: return
        val entry = com.ssjq.english.data.WordEntry(
            wordId = word.wordId,
            headWord = word.headWord,
            dbName = dbName,
            tranCn = word.translation.takeIf { it.isNotBlank() },
        )
        com.ssjq.english.data.UserLibrary.addWrong(entry)
        if (com.ssjq.english.data.UserLibrary.autoFavoriteEnabled(dbName)) {
            com.ssjq.english.data.UserLibrary.addFavorite(entry)
        }
    }

    private fun moveToNext() {
        if (currentIndex < total - 1) {
            currentIndex++
            com.ssjq.english.data.UserLibrary.saveStudyIndex(dbName, currentIndex)
            updateNotification()
        } else {
            stopLearning()
        }
    }

    private fun updateNotification() {
        val word = words.getOrNull(currentIndex) ?: return stopLearning()
        val notification = NotificationHelper.buildLearningNotification(
            context = this,
            word = word.headWord,
            phonetic = word.phonetic,
            translation = word.translation,
            index = currentIndex,
            total = total,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun buildCurrentNotification(): android.app.Notification {
        return if (words.isEmpty()) {
            NotificationHelper.buildLearningNotification(
                context = this,
                word = "准备中…",
                phonetic = "",
                translation = "",
                index = 0,
                total = 1,
            )
        } else {
            val word = words[currentIndex]
            NotificationHelper.buildLearningNotification(
                context = this,
                word = word.headWord,
                phonetic = word.phonetic,
                translation = word.translation,
                index = currentIndex,
                total = total,
            )
        }
    }

    /**
     * 启动前台服务，Android 14+ 必须指定 FOREGROUND_SERVICE_TYPE。
     * - dataSync：适用于"学习进度同步"场景，6 小时上限，无需额外权限
     * - mediaPlayback：仅当持续播放音频时用，需 android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
     */
    private fun startForegroundWithType(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+：必须传 type 参数
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    /**
     * 发音：复用有道 TTS API。
     * 独立 MediaPlayer，播放完自动 release。
     */
    private fun speak(word: String, type: Int = 2) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                mediaPlayer?.release()
                val url = "https://dict.youdao.com/dictvoice?audio=" +
                    URLEncoder.encode(word, "UTF-8") + "&type=$type"
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(url)
                    prepareAsync()
                    setOnPreparedListener { start() }
                    setOnCompletionListener { release(); mediaPlayer = null }
                    setOnErrorListener { _, _, _ -> release(); mediaPlayer = null; true }
                }
            }.onFailure { Log.e(TAG, "发音失败: $word", it) }
        }
    }

    private fun stopLearning() {
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(this, "开始后台背诵啦", android.widget.Toast.LENGTH_SHORT).show()
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        scope.cancel()
    }

    companion object {
        private const val TAG = "LearningService"
        const val ACTION_START = "com.ssjq.english.action.START_LEARNING"
        const val ACTION_HANDLE_BUTTON = "com.ssjq.english.action.HANDLE_BUTTON"
        const val ACTION_STOP = "com.ssjq.english.action.STOP_LEARNING"
        const val EXTRA_DB_NAME = "extra_db_name"
        const val EXTRA_BUTTON_ACTION = "extra_button_action"

        /** 启动学习服务 */
        fun start(context: Context, dbName: String) {
            val intent = Intent(context, LearningForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DB_NAME, dbName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LearningForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        private data class LearningWord(
            val wordId: String,
            val headWord: String,
            val phonetic: String,
            val translation: String,
        )
    }
}

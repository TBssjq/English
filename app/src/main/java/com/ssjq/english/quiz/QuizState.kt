package com.ssjq.english.quiz

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.WordDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * 测验状态容器（MVI 风格）：
 * 所有状态用 mutableStateOf 托管，UI 自动重组。
 * 通过 dispatch() 触发意图，内部修改 uiState。
 */
class QuizState(
    private val context: Context,
    private val dbName: String,
    private val sourceWords: List<WordDetail>,
    private val questionCount: Int,
    /** null = 4 种模式随机；非 null = 固定该模式 */
    private val fixedMode: QuizMode? = null,
    /** 是否自动播放发音 */
    autoPlayAudio: Boolean = false,
) {
    var uiState by mutableStateOf(QuizUiState(isLoading = true, selectedMode = fixedMode))
        private set

    private var _autoPlayAudio by mutableStateOf(autoPlayAudio)
    var autoPlayAudio: Boolean
        get() = _autoPlayAudio
        private set(value) { _autoPlayAudio = value }

    fun setAutoPlayAudioEnabled(enabled: Boolean) {
        _autoPlayAudio = enabled
    }

    private var questions: List<QuizQuestion> = emptyList()
    private val modeStats = mutableMapOf<QuizMode, Pair<Int, Int>>()
    private var loadedWords: List<WordDetail> = emptyList()

    /** 初始词源。「再来一轮」必须回到它，否则会被错题回顾永久替换成错题集合 */
    private var originalWords: List<WordDetail> = emptyList()

    /** 完成上报是否已消费过：防止同一轮结果被重复累加打卡与刷题记录 */
    private var reported: Boolean = false

    /** 当前是否处于「错题回顾」轮：回顾轮不参与打卡统计 */
    private var isReviewRound: Boolean = false

    /**
     * 复用单个 MediaPlayer。
     * 旧实现每次发音都 `new MediaPlayer()` 且不保存引用，只有播放完成/出错才回收；
     * 连点发音或答题中途退出会不断堆积实例，最终突破进程上限抛异常并静默失效。
     */
    private var mediaPlayer: MediaPlayer? = null

    /** 加载题目（异步） */
    suspend fun load() {
        uiState = uiState.copy(isLoading = true)
        val words = if (sourceWords.isNotEmpty()) {
            sourceWords
        } else {
            // 词库缺失 / 损坏 / 表结构异常都要吞掉，交给下面的空态处理，
            // 否则异常会从 LaunchedEffect 一路抛出去直接崩溃
            try {
                withContext(Dispatchers.IO) {
                    val db = DatabaseManager.openDatabase(context, dbName)
                    try {
                        val list = DatabaseManager.getWordList(db)
                        list.take(80).mapNotNull { DatabaseManager.getWordDetail(db, it.wordId) }
                    } finally {
                        DatabaseManager.release(db)
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        loadedWords = words
        originalWords = words
        isReviewRound = false
        reported = false
        regenerateQuestions()
    }

    /** 根据 fixedMode 生成题目 */
    private fun regenerateQuestions() {
        val words = loadedWords
        if (words.isEmpty()) {
            uiState = QuizUiState(isLoading = false, isFinished = true, selectedMode = fixedMode)
            return
        }
        val count = questionCount.coerceAtMost(words.size).coerceAtLeast(1)
        questions = QuizEngine.generateQuestions(words, count, fixedMode = fixedMode)
        modeStats.clear()
        questions.groupBy { it.mode }.forEach { (mode, qs) ->
            modeStats[mode] = 0 to qs.size
        }
        uiState = if (questions.isEmpty()) {
            QuizUiState(isLoading = false, isFinished = true, selectedMode = fixedMode)
        } else {
            QuizUiState(
                isLoading = false,
                currentIndex = 0,
                totalQuestions = questions.size,
                currentQuestion = questions[0],
                modeStats = modeStats.toMap(),
                selectedMode = fixedMode,
            )
        }
        // 听音类题目必须自动发音，否则题目无解
        if (questions.isNotEmpty() && shouldAutoSpeak(questions[0].mode)) {
            speak(questions[0].word.headWord, type = 2)
        }
    }

    /**
     * 是否需要自动发音。
     *
     * 旧实现漏掉了 AudioSpelling（听音拼写）：题干写着「请听发音拼写单词」，
     * 却从不自动播放，而该题又没有音标提示，重播按钮在无音标时也不显示 ——
     * 抽到这道题直接无解，只能瞎填。
     */
    private fun shouldAutoSpeak(mode: QuizMode): Boolean =
        autoPlayAudio || mode is QuizMode.AudioSelect || mode is QuizMode.AudioSpelling

    /** 处理意图 */
    fun dispatch(intent: QuizIntent) {
        when (intent) {
            is QuizIntent.SelectOption -> handleSelectOption(intent.option)
            is QuizIntent.UpdateSpelling -> handleUpdateSpelling(intent.input)
            is QuizIntent.SubmitSpelling -> handleSubmitSpelling()
            is QuizIntent.NextQuestion -> handleNextQuestion()
            is QuizIntent.ReplayAudio -> handleReplayAudio()
            is QuizIntent.Restart -> handleRestart()
            is QuizIntent.ReviewWrong -> handleReviewWrong()
        }
    }

    private fun handleSelectOption(option: String) {
        val q = uiState.currentQuestion ?: return
        if (uiState.answerResult !is AnswerResult.Idle) return
        val isCorrect = option == q.correctAnswer
        val result = if (isCorrect) AnswerResult.Correct
        else AnswerResult.Wrong(userAnswer = option, correct = q.correctAnswer)
        updateStats(q.mode, isCorrect)
        uiState = uiState.copy(
            answerResult = result,
            selectedOption = option,
            correctCount = uiState.correctCount + if (isCorrect) 1 else 0,
            wrongCount = uiState.wrongCount + if (isCorrect) 0 else 1,
            wrongWords = if (isCorrect) uiState.wrongWords else uiState.wrongWords + q.word,
        )
    }

    private fun handleUpdateSpelling(input: String) {
        if (uiState.answerResult !is AnswerResult.Idle) return
        uiState = uiState.copy(spellingInput = input)
    }

    private fun handleSubmitSpelling() {
        val q = uiState.currentQuestion ?: return
        if (uiState.answerResult !is AnswerResult.Idle) return
        val result = QuizEngine.checkSpelling(uiState.spellingInput, q.correctAnswer)
        val isCorrect = result is AnswerResult.Correct || result is AnswerResult.AlmostCorrect
        updateStats(q.mode, isCorrect)
        uiState = uiState.copy(
            answerResult = result,
            selectedOption = uiState.spellingInput,
            correctCount = uiState.correctCount + if (isCorrect) 1 else 0,
            wrongCount = uiState.wrongCount + if (isCorrect) 0 else 1,
            wrongWords = if (isCorrect) uiState.wrongWords else uiState.wrongWords + q.word,
        )
    }

    private fun handleNextQuestion() {
        // 防重复提交：反馈卡的 AnimatedContent 在退出过渡期间旧按钮仍可点击，
        // 若此时再次触发会让 currentIndex 连跳、跳过整道题并使统计错乱。
        // 只有「已作答」（answerResult != Idle）时才允许切到下一题。
        if (uiState.isFinished || uiState.answerResult is AnswerResult.Idle) return
        val next = uiState.currentIndex + 1
        if (next >= uiState.totalQuestions) {
            uiState = uiState.copy(isFinished = true)
            return
        }
        val nextQ = questions[next]
        uiState = uiState.copy(
            currentIndex = next,
            currentQuestion = nextQ,
            answerResult = AnswerResult.Idle,
            spellingInput = "",
            selectedOption = null,
        )
        if (shouldAutoSpeak(nextQ.mode)) {
            speak(nextQ.word.headWord, type = 2)
        }
    }

    private fun handleReplayAudio() {
        val q = uiState.currentQuestion ?: return
        // 传英文单词（correctAnswer 在 AudioSelect 模式下是中文释义，有道 API 不支持）
        speak(q.word.headWord, type = 2)
    }

    /**
     * 再来一轮：回到**初始词源**重新出题。
     *
     * 旧实现把「当前这一轮的题目词」当成新词源，于是走完错题回顾之后再点
     * 再来一轮，词库就被永久替换成那几道错题，再也回不到完整词库。
     */
    private fun handleRestart() {
        loadedWords = originalWords
        isReviewRound = false
        reported = false
        regenerateQuestions()
    }

    private fun handleReviewWrong() {
        val wrong = uiState.wrongWords.distinct()
        if (wrong.isEmpty()) {
            uiState = uiState.copy(isFinished = true)
            return
        }
        val count = wrong.size.coerceAtLeast(1)
        questions = QuizEngine.generateQuestions(wrong, count, fixedMode = fixedMode)
        loadedWords = questions.map { it.word }.distinct().ifEmpty { wrong }
        // 回顾轮不参与打卡统计，也不允许再次上报
        isReviewRound = true
        modeStats.clear()
        questions.groupBy { it.mode }.forEach { (mode, qs) ->
            modeStats[mode] = 0 to qs.size
        }
        uiState = QuizUiState(
            isLoading = false,
            currentIndex = 0,
            totalQuestions = questions.size,
            // 生成器理论上不会返回空，但空了也不能越界取 questions[0]
            currentQuestion = questions.firstOrNull(),
            modeStats = modeStats.toMap(),
            selectedMode = fixedMode,
        )
        if (questions.isEmpty()) {
            uiState = uiState.copy(isFinished = true)
            return
        }
        if (shouldAutoSpeak(questions[0].mode)) {
            speak(questions[0].word.headWord, type = 2)
        }
    }

    private fun updateStats(mode: QuizMode, correct: Boolean) {
        val (correctCount, total) = modeStats[mode] ?: (0 to 0)
        modeStats[mode] = (correctCount + if (correct) 1 else 0) to total
        uiState = uiState.copy(modeStats = modeStats.toMap())
    }

    /**
     * 有道词典发音 API：
     * https://dict.youdao.com/dictvoice?audio={word}&type={type}
     * type=1 英音，type=2 美音。
     *
     * 复用同一个 MediaPlayer：新的发音会先停掉上一个，
     * 避免连点发音时多个音频叠加，也避免实例堆积突破进程上限。
     */
    fun speak(word: String, type: Int = 2) {
        val encoded = try {
            URLEncoder.encode(word, "UTF-8")
        } catch (_: Exception) {
            return
        }
        val url = "https://dict.youdao.com/dictvoice?audio=$encoded&type=$type"
        releasePlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { runCatching { start() } }
                setOnCompletionListener { releasePlayer() }
                setOnErrorListener { _, _, _ -> releasePlayer(); true }
                prepareAsync()
            }
        } catch (_: Exception) {
            // 播放失败不影响答题
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
                player.reset()
                player.release()
            } catch (_: Exception) {
                // 释放失败没有补救手段，忽略即可
            }
        }
        mediaPlayer = null
    }

    /** 释放播放器。必须在页面销毁时调用，否则 MediaPlayer 会随 Activity 泄漏 */
    fun release() {
        releasePlayer()
    }

    /** 一轮测验的结果快照，供完成时上报打卡与刷题记录 */
    data class FinishReport(
        val correctCount: Int,
        val totalQuestions: Int,
        val wrongCount: Int,
    )

    /** 去重后的错题数（小题库会循环出词，同一词可能被错多次） */
    fun distinctWrongCount(): Int = uiState.wrongWords.distinct().size

    /**
     * 取走「完成上报」许可，同一轮只会成功一次。
     *
     * 旧实现在 UI 侧用 `LaunchedEffect(uiState.isFinished)` 上报，而 isFinished
     * 会在「进入错题回顾」「空词库直接结束」等路径上被反复置为 true，
     * 于是打卡数据与刷题记录被重复累加 —— CheckInManager.accumulate 是纯累加，
     * 无法区分主轮与回顾轮，统计结果虚高且不可恢复。
     *
     * @return 需要上报时返回结果快照，否则返回 null
     */
    fun consumeFinishReport(): FinishReport? {
        if (reported) return null
        if (!uiState.isFinished) return null
        if (isReviewRound) return null
        val total = uiState.totalQuestions
        if (total <= 0) return null      // 空词库不产生打卡记录
        reported = true
        return FinishReport(
            correctCount = uiState.correctCount,
            totalQuestions = total,
            wrongCount = uiState.wrongWords.distinct().size,
        )
    }
}

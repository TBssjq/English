package com.ssjq.english.quiz

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.QuizRecordSummary
import com.ssjq.english.data.UserManager
import com.ssjq.english.data.WordDetail
import com.ssjq.english.service.PronunciationPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    /** 题源标识，疯狂刷题退出后靠它判断能否继续同一批题 */
    private val sourceKey: String? = null,
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

    /** 已加载标记：从单词详情返回时复用同一状态，不能重新出题把用户送回第 1 题 */
    private var loaded: Boolean = false

    /** 本轮开始前的上一次测验记录，跟随状态一起复用 */
    var previousRecord: QuizRecordSummary? = null
        private set

    /** 当前题量上限，测验中可调整；调整后重新出题 */
    private var _questionCountLimit by mutableStateOf(questionCount)
    val questionCountLimit: Int get() = _questionCountLimit

    /** 本轮题目的随机种子：断点续做靠它重建出同一套题 */
    private var currentSeed: Long = 0L

    /** 当前是否处于「错题回顾」轮：回顾轮不参与打卡统计 */
    private var isReviewRound: Boolean = false

    /** 发音播放器 */
    private val pronunciationPlayer = PronunciationPlayer()

    /** 加载题目（异步）。已经加载过则保持当前进度 */
    suspend fun load() {
        if (loaded) return
        uiState = uiState.copy(isLoading = true)
        previousRecord = withContext(Dispatchers.IO) { UserManager.getLastQuizRecord() }
        val words = if (sourceWords.isNotEmpty()) {
            sourceWords
        } else {
            withContext(Dispatchers.IO) {
                val db = DatabaseManager.openDatabase(context, dbName)
                try {
                    val list = DatabaseManager.getWordList(db)
                    list.take(80).mapNotNull { DatabaseManager.getWordDetail(db, it.wordId, dbName) }
                } finally {
                    DatabaseManager.release(db)
                }
            }
        }
        loadedWords = words
        originalWords = words
        isReviewRound = false
        reported = false
        // 有可用断点就回到上次的题号，否则重新出题
        if (!tryRestore()) regenerateQuestions()
        loaded = true
    }

    /** 根据 fixedMode 生成题目。seed 决定题序，断点恢复时传入同一个值 */
    private fun regenerateQuestions(seed: Long = System.nanoTime()) {
        val words = loadedWords
        if (words.isEmpty()) {
            uiState = QuizUiState(isLoading = false, isFinished = true, selectedMode = fixedMode)
            return
        }
        val count = _questionCountLimit.coerceAtMost(words.size).coerceAtLeast(1)
        questions = QuizEngine.generateQuestions(words, count, fixedMode = fixedMode, seed = seed)
        currentSeed = seed
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
     * 用上次保存的断点恢复：同词库、同模式、同 seed 才能重建出同一套题。
     * 数据损坏或参数不匹配时返回 false，由调用方重新出题。
     */
    private fun tryRestore(): Boolean {
        val raw = UserManager.getQuizProgress() ?: return false
        val obj = try {
            org.json.JSONObject(raw)
        } catch (e: Exception) {
            // 存档损坏时按重新开始处理，不能让解析异常冒到界面
            return false
        }
        if (obj.optString("dbName") != dbName) return false
        if (obj.optString("mode") != (fixedMode?.label ?: "")) return false
        if (obj.optString("sourceKey") != (sourceKey ?: "")) return false
        if (obj.optInt("requestedCount", 0) != _questionCountLimit) return false
        val seed = obj.optLong("seed", 0L)
        val count = obj.optInt("count", 0)
        val index = obj.optInt("index", 0)
        if (seed == 0L || count <= 0) return false
        val rebuilt = QuizEngine.generateQuestions(loadedWords, count, fixedMode, seed = seed)
        if (rebuilt.isEmpty() || index !in rebuilt.indices) return false
        questions = rebuilt
        currentSeed = seed
        _questionCountLimit = count
        modeStats.clear()
        rebuilt.groupBy { it.mode }.forEach { (mode, qs) ->
            modeStats[mode] = 0 to qs.size
        }
        val wrongIds = obj.optJSONArray("wrongIds")
            ?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }
            ?: emptyList()
        val wrongDetails = rebuilt.map { it.word }.filter { it.wordId in wrongIds }.distinct()
        uiState = QuizUiState(
            isLoading = false,
            currentIndex = index,
            totalQuestions = rebuilt.size,
            currentQuestion = rebuilt[index],
            correctCount = obj.optInt("correct", 0),
            wrongCount = obj.optInt("wrong", 0),
            wrongWords = wrongDetails,
            modeStats = modeStats.toMap(),
            selectedMode = fixedMode,
        )
        return true
    }

    /**
     * 保存断点：答题与切题后调用，进程被杀也能接着做。
     * 答完还没点下一题时把题号前进一位，退出再进入不会重复答同一题。
     */
    fun persistProgress(answered: Boolean = false) {
        if (!loaded || isReviewRound || questions.isEmpty() || uiState.isFinished) return
        val savedIndex = (uiState.currentIndex + if (answered) 1 else 0)
            .coerceIn(0, questions.lastIndex)
        val json = org.json.JSONObject().apply {
            put("dbName", dbName)
            put("mode", fixedMode?.label ?: "")
            put("sourceKey", sourceKey ?: "")
            put("requestedCount", _questionCountLimit)
            put("count", questions.size)
            put("seed", currentSeed)
            put("index", savedIndex)
            put("correct", uiState.correctCount)
            put("wrong", uiState.wrongCount)
            put("wrongIds", org.json.JSONArray(uiState.wrongWords.map { it.wordId }))
            put("sourceIds", org.json.JSONArray(loadedWords.map { it.wordId }))
        }.toString()
        UserManager.saveQuizProgress(json)
    }

    /** 调整题量并重新出题；旧断点同时作废 */
    fun setQuestionCount(count: Int) {
        require(count > 0) { "题量必须大于 0" }
        if (count == _questionCountLimit) return
        _questionCountLimit = count
        reported = false
        UserManager.clearQuizProgress()
        regenerateQuestions()
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
            is QuizIntent.SkipQuestion -> handleSkip(intent.markWrong)
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
            streak = if (isCorrect) uiState.streak + 1 else 0,
            wrongWords = if (isCorrect) uiState.wrongWords else uiState.wrongWords + q.word,
        )
        persistProgress(answered = true)
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
            streak = if (isCorrect) uiState.streak + 1 else 0,
            wrongWords = if (isCorrect) uiState.wrongWords else uiState.wrongWords + q.word,
        )
        persistProgress(answered = true)
    }

    private fun handleNextQuestion() {
        // 防重复提交：反馈卡的 AnimatedContent 在退出过渡期间旧按钮仍可点击，
        // 若此时再次触发会让 currentIndex 连跳、跳过整道题并使统计错乱。
        // 只有「已作答」（answerResult != Idle）时才允许切到下一题。
        if (uiState.isFinished || uiState.answerResult is AnswerResult.Idle) return
        val next = uiState.currentIndex + 1
        if (next >= uiState.totalQuestions) {
            uiState = uiState.copy(isFinished = true)
            // 本轮已完成，断点不再有意义
            UserManager.clearQuizProgress()
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
        persistProgress()
    }

    private fun handleReplayAudio() {
        val q = uiState.currentQuestion ?: return
        // 传英文单词（correctAnswer 在 AudioSelect 模式下是中文释义，有道 API 不支持）
        speak(q.word.headWord, type = 2)
    }

    /** 主动跳过本题；markWrong 为真时同时把单词记入错题本 */
    private fun handleSkip(markWrong: Boolean) {
        val q = uiState.currentQuestion ?: return
        if (uiState.answerResult !is AnswerResult.Idle) return
        updateStats(q.mode, false)
        uiState = uiState.copy(
            answerResult = AnswerResult.Skipped(markedWrong = markWrong),
            streak = 0,
            wrongCount = uiState.wrongCount + if (markWrong) 1 else 0,
            wrongWords = if (markWrong) uiState.wrongWords + q.word else uiState.wrongWords,
        )
        persistProgress(answered = true)
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
        // 新一轮从头开始，旧断点作废
        UserManager.clearQuizProgress()
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

    fun speak(word: String, type: Int = 2) = pronunciationPlayer.speak(word, type)

    /** 释放播放器。必须在页面销毁时调用 */
    fun release() = pronunciationPlayer.release()

    /** 一轮测验的结果快照，供完成时上报打卡与刷题记录 */
    data class FinishReport(
        val correctCount: Int,
        val totalQuestions: Int,
        val wrongCount: Int,
        /** 本轮答错的单词，用于写入错题本与结果页快捷入口 */
        val wrongWords: List<WordDetail>,
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
            wrongWords = uiState.wrongWords.distinct(),
        )
    }
}

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
) {
    var uiState by mutableStateOf(QuizUiState(isLoading = true, selectedMode = fixedMode))
        private set

    private var questions: List<QuizQuestion> = emptyList()
    private val modeStats = mutableMapOf<QuizMode, Pair<Int, Int>>()
    private var loadedWords: List<WordDetail> = emptyList()

    /** 加载题目（异步） */
    suspend fun load() {
        uiState = uiState.copy(isLoading = true)
        val words = if (sourceWords.isNotEmpty()) {
            sourceWords
        } else {
            withContext(Dispatchers.IO) {
                val db = DatabaseManager.openDatabase(context, dbName)
                val list = DatabaseManager.getWordList(db)
                list.take(80).mapNotNull { DatabaseManager.getWordDetail(db, it.wordId) }
            }
        }
        loadedWords = words
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
        // 第一题如果是 AudioSelect，自动播放（注意：传英文单词而非 correctAnswer，因为后者在 AudioSelect 模式下是中文释义）
        if (questions.isNotEmpty() && questions[0].mode is QuizMode.AudioSelect) {
            speak(questions[0].word.headWord, type = 2)
        }
    }

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
        // 自动播放音频（仅 AudioSelect 模式，传英文单词）
        if (nextQ.mode is QuizMode.AudioSelect) {
            speak(nextQ.word.headWord, type = 2)
        }
    }

    private fun handleReplayAudio() {
        val q = uiState.currentQuestion ?: return
        // 传英文单词（correctAnswer 在 AudioSelect 模式下是中文释义，有道 API 不支持）
        speak(q.word.headWord, type = 2)
    }

    private fun handleRestart() {
        val all = questions.map { it.word }.distinct()
        loadedWords = all
        regenerateQuestions()
    }

    private fun handleReviewWrong() {
        val wrong = uiState.wrongWords.distinct()
        if (wrong.isEmpty()) {
            uiState = uiState.copy(isFinished = true)
            return
        }
        loadedWords = wrong
        val count = wrong.size.coerceAtLeast(1)
        questions = QuizEngine.generateQuestions(wrong, count, fixedMode = fixedMode)
        modeStats.clear()
        questions.groupBy { it.mode }.forEach { (mode, qs) ->
            modeStats[mode] = 0 to qs.size
        }
        uiState = QuizUiState(
            isLoading = false,
            currentIndex = 0,
            totalQuestions = questions.size,
            currentQuestion = questions[0],
            modeStats = modeStats.toMap(),
            selectedMode = fixedMode,
        )
        if (questions.isNotEmpty() && questions[0].mode is QuizMode.AudioSelect) {
            speak(questions[0].word.headWord, type = 2)
        }
        loadedWords = questions.map { it.word }.distinct().ifEmpty { wrong }
    }

    private fun updateStats(mode: QuizMode, correct: Boolean) {
        val (correctCount, total) = modeStats[mode] ?: (0 to 0)
        modeStats[mode] = (correctCount + if (correct) 1 else 0) to total
        uiState = uiState.copy(modeStats = modeStats.toMap())
    }

    /**
     * 有道词典发音 API（与 WordDetailScreen 完全一致的实现）：
     * https://dict.youdao.com/dictvoice?audio={word}&type={type}
     * type=1 英音，type=2 美音。每次新建 MediaPlayer，播放完自动 release。
     */
    fun speak(word: String, type: Int = 2) {
        try {
            val url = "https://dict.youdao.com/dictvoice?audio=" +
                URLEncoder.encode(word, "UTF-8") + "&type=$type"
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { start() }
                setOnCompletionListener { release() }
                setOnErrorListener { _, _, _ -> release(); true }
                prepareAsync()
            }
        } catch (_: Exception) {
            // 忽略播放失败
        }
    }

    fun release() {
        // 无类成员 MediaPlayer，每次播放都新建并自动 release
    }
}

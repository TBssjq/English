package com.ssjq.english.quiz

import com.ssjq.english.data.WordDetail

/** 4 种随机考察模式，weight 为加权随机权重（概率） */
sealed class QuizMode(val label: String, val weight: Float) {
    data object EnSelectCn : QuizMode("英选中", 0.35f)
    data object CnSelectEn : QuizMode("中选英", 0.25f)
    data object AudioSelect : QuizMode("听音辨意", 0.25f)
    data object Spelling : QuizMode("拼写测试", 0.15f)

    companion object {
        val all: List<QuizMode> = listOf(EnSelectCn, CnSelectEn, AudioSelect, Spelling)
    }
}

/** 一道题：单词 + 模式 + 选项 + 正确答案 */
data class QuizQuestion(
    val id: Int,
    val word: WordDetail,
    val mode: QuizMode,
    /** 选项内容（英选中时是中文，中选英/听音时是英文，拼写模式无选项用空列表） */
    val options: List<String>,
    /** 正确答案（EnSelectCn 是中文释义拼接；CnSelectEn/Audio 是英文；Spelling 是英文） */
    val correctAnswer: String,
    /** 题目展示文本：EnSelectCn 是英文，CnSelectEn 是中文，Audio 是提示语，Spelling 是中文+音标 */
    val promptText: String,
    /** 音标（用于拼写模式和音频模式的补充提示） */
    val phonetic: String,
)

/** 答题结果：Idle / Correct / AlmostCorrect / Wrong */
sealed class AnswerResult {
    data object Idle : AnswerResult()
    data object Correct : AnswerResult()
    data class AlmostCorrect(val tip: String) : AnswerResult()
    data class Wrong(val userAnswer: String, val correct: String) : AnswerResult()
}

/** UI 状态 */
data class QuizUiState(
    val isLoading: Boolean = true,
    val currentIndex: Int = 0,
    val totalQuestions: Int = 0,
    val currentQuestion: QuizQuestion? = null,
    val answerResult: AnswerResult = AnswerResult.Idle,
    val spellingInput: String = "",
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val isFinished: Boolean = false,
    val selectedOption: String? = null,
    /** null = 随机模式；非 null = 固定模式 */
    val selectedMode: QuizMode? = null,
    /** 错题集合（收集给回顾用） */
    val wrongWords: List<WordDetail> = emptyList(),
    /** 各模式分布统计（结果页用） */
    val modeStats: Map<QuizMode, Pair<Int, Int>> = emptyMap(),
)

/** 意图 / 事件 */
sealed class QuizIntent {
    data class SelectOption(val option: String) : QuizIntent()
    data class UpdateSpelling(val input: String) : QuizIntent()
    data object SubmitSpelling : QuizIntent()
    data object NextQuestion : QuizIntent()
    data object ReplayAudio : QuizIntent()
    data object Restart : QuizIntent()
    data object ReviewWrong : QuizIntent()
}

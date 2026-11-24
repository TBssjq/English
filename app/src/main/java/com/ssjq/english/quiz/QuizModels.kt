package com.ssjq.english.quiz

import com.ssjq.english.data.WordDetail

/** 4 种随机考察模式，weight 为加权随机权重（概率） */
sealed class QuizMode(val label: String, val weight: Float) {
    data object EnSelectCn : QuizMode("英选中", 0.35f)
    data object CnSelectEn : QuizMode("中选英", 0.25f)
    data object AudioSelect : QuizMode("听音辨意", 0.25f)
    data object Spelling : QuizMode("拼写测试", 0.15f)
    data object AudioSpelling : QuizMode("听音拼写", 0.10f)

    /**
     * 题型说明：疯狂刷题选题型时展示给用户，
     * 避免只看「英选中 / 中选英」两个缩写分不清题目长什么样。
     */
    val description: String
        get() = when (this) {
            is EnSelectCn -> "看英文单词，从 4 个中文释义里选"
            is CnSelectEn -> "看中文释义，从 4 个英文单词里选"
            is AudioSelect -> "听发音，从 4 个中文释义里选"
            is Spelling -> "看中文释义，手动拼写英文单词"
            is AudioSpelling -> "听发音，手动拼写英文单词"
        }

    companion object {
        val all: List<QuizMode> = listOf(EnSelectCn, CnSelectEn, AudioSelect, Spelling, AudioSpelling)

        /** 按 label 反查模式，用于把路由里传的字串还原成模式集合 */
        fun fromLabel(label: String): QuizMode? = all.firstOrNull { it.label == label }
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
    data class AlmostCorrect(
        val tip: String,
        /** 字母级差异：仅有一处拼写偏差时也要标出是哪几个字母 */
        val diffInfo: List<CharDiff> = emptyList(),
    ) : AnswerResult()
    data class Wrong(val userAnswer: String, val correct: String, val diffInfo: List<CharDiff> = emptyList()) : AnswerResult()
    /** 主动跳过；markedWrong 表示同时把当前单词记入错题本 */
    data class Skipped(val markedWrong: Boolean) : AnswerResult()
}

data class CharDiff(
    val char: Char,
    val isCorrect: Boolean,
)

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
    /** 本轮连续答对数，用于即时正反馈；答错或跳过归零 */
    val streak: Int = 0,
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
    data class SkipQuestion(val markWrong: Boolean) : QuizIntent()
    data object Restart : QuizIntent()
    data object ReviewWrong : QuizIntent()
}

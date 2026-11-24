package com.ssjq.english.quiz

import com.ssjq.english.data.WordDetail
import kotlin.random.Random

/**
 * 测验引擎：
 * - 加权随机模式（连续 3 题不重复模式）
 * - 干扰项生成（4选1，优先相似词）
 * - 拼写容错校验（编辑距离 ≤ 1 → AlmostCorrect）
 */
object QuizEngine {

    private const val OPTION_COUNT = 4
    private const val MAX_SAME_MODE_STREAK = 2 // 连续相同模式最多 2 题（第 3 题必须切换）

    /**
     * 从词库生成一组测验题。
     * @param words 全部候选单词（用于抽取题目 + 生成干扰项）
     * @param count 题目数量
     * @param fixedMode 非 null 时所有题目使用此模式；null 时按权重随机
     * @param allowedModes 随机模式下的题型白名单（空集合 = 全部题型参与），
     *        用于疯狂刷题里用户自选题型后按勾选结果出题
     * @param seed 随机种子（可传 null 用默认）
     */
    fun generateQuestions(
        words: List<WordDetail>,
        count: Int = 20,
        fixedMode: QuizMode? = null,
        allowedModes: Set<QuizMode> = emptySet(),
        seed: Long? = null,
    ): List<QuizQuestion> {
        if (words.isEmpty()) return emptyList()
        val random = if (seed != null) Random(seed) else Random
        val questionWords = pickWordsForQuestions(words, count, random)
        // 题型白名单：用户勾掉的题型不再参与随机，保证题目池与所选一致；
        // 传空集合时回退到全部题型，避免上游漏传导致无题可出
        val modePool = if (allowedModes.isEmpty()) QuizMode.all else QuizMode.all.filter { it in allowedModes }
        val result = mutableListOf<QuizQuestion>()
        var lastMode1: QuizMode? = null
        var lastMode2: QuizMode? = null

        for ((index, word) in questionWords.withIndex()) {
            // 固定模式时直接用 fixedMode；否则在所选题型里按权重随机（连续 3 题不重复）
            val mode = fixedMode ?: pickMode(modePool, lastMode1, lastMode2, random)
            val question = buildQuestion(
                id = index,
                word = word,
                mode = mode,
                allWords = words,
                random = random,
            )
            result.add(question)
            lastMode2 = lastMode1
            lastMode1 = mode
        }
        return result
    }

    /** 在所选题型内加权随机选择模式，保证连续 3 题不重复 */
    private fun pickMode(
        modePool: List<QuizMode>,
        last1: QuizMode?,
        last2: QuizMode?,
        random: Random,
    ): QuizMode {
        val candidates = modePool.toMutableList()
        // 连续 2 题相同则第 3 题必须切换（只勾了 1 种题型时无从切换，保持原样）
        if (last1 != null && last2 != null && last1 == last2 && candidates.size > 1) {
            candidates.remove(last1)
        }
        val totalWeight = candidates.sumOf { it.weight.toDouble() }
        var r = random.nextDouble() * totalWeight
        for (mode in candidates) {
            r -= mode.weight.toDouble()
            if (r <= 0) return mode
        }
        return candidates.last()
    }

    /** 为题目抽取单词：如果词库足够则随机抽，不够则循环使用 */
    private fun pickWordsForQuestions(
        words: List<WordDetail>,
        count: Int,
        random: Random,
    ): List<WordDetail> {
        if (words.size <= count) {
            // 词数不足：打乱后循环填充
            val shuffled = words.shuffled(random)
            return List(count) { shuffled[it % shuffled.size] }
        }
        return words.shuffled(random).take(count)
    }

    /** 根据模式构建题目 */
    private fun buildQuestion(
        id: Int,
        word: WordDetail,
        mode: QuizMode,
        allWords: List<WordDetail>,
        random: Random,
    ): QuizQuestion {
        return when (mode) {
            is QuizMode.EnSelectCn -> {
                val correctCn = word.chineseText()
                val distractors = pickDistractorsCn(word, allWords, random)
                val options = (distractors + correctCn).shuffled(random)
                QuizQuestion(
                    id = id,
                    word = word,
                    mode = mode,
                    options = options,
                    correctAnswer = correctCn,
                    promptText = word.headWord,
                    phonetic = word.usPhone ?: word.ukPhone ?: "",
                )
            }
            is QuizMode.CnSelectEn -> {
                val correctEn = word.headWord
                val distractors = pickDistractorsEn(word, allWords, random)
                val options = (distractors + correctEn).shuffled(random)
                QuizQuestion(
                    id = id,
                    word = word,
                    mode = mode,
                    options = options,
                    correctAnswer = correctEn,
                    promptText = word.chineseText(),
                    phonetic = word.usPhone ?: word.ukPhone ?: "",
                )
            }
            is QuizMode.AudioSelect -> {
                val correctCn = word.chineseText()
                val distractors = pickDistractorsCn(word, allWords, random)
                val options = (distractors + correctCn).shuffled(random)
                QuizQuestion(
                    id = id,
                    word = word,
                    mode = mode,
                    options = options,
                    correctAnswer = correctCn,
                    promptText = "请听发音选择释义",
                    phonetic = word.usPhone ?: word.ukPhone ?: "",
                )
            }
            is QuizMode.Spelling -> {
                QuizQuestion(
                    id = id,
                    word = word,
                    mode = mode,
                    options = emptyList(),
                    correctAnswer = word.headWord,
                    promptText = word.chineseText(),
                    phonetic = word.usPhone ?: word.ukPhone ?: "",
                )
            }
            is QuizMode.AudioSpelling -> {
                QuizQuestion(
                    id = id,
                    word = word,
                    mode = mode,
                    options = emptyList(),
                    correctAnswer = word.headWord,
                    promptText = "请听发音拼写单词",
                    phonetic = word.usPhone ?: word.ukPhone ?: "",
                )
            }
        }
    }

    /** 中文干扰项：优先选词性/难度相近的词，不足 3 个用 "—" 占位 */
    private fun pickDistractorsCn(
        correct: WordDetail,
        allWords: List<WordDetail>,
        random: Random,
    ): List<String> {
        // 必须按「释义文本」排除正确答案：不同单词可能有完全相同的中文释义，
        // 只按 wordId 去重会让选项里出现两份正确答案 —— 用户选中"正确答案的副本"
        // 会被判错，且判定后两处同时高亮，题目事实上无解。
        val correctCn = correct.chineseText()
        val pool = allWords.filter {
            it.wordId != correct.wordId &&
                it.chineseText().isNotBlank() &&
                it.chineseText() != correctCn
        }
        // 按难度差排序，越近越优先
        val sorted = pool.sortedBy { Math.abs((it.star - correct.star)) }
        // 从前半区随机抽
        val candidates = sorted.take(minOf(sorted.size, 20)).shuffled(random)
        val result = candidates
            .map { it.chineseText() }
            .filter { it.isNotBlank() && it != correctCn }
            .distinct()
            .take(OPTION_COUNT - 1)
            .toMutableList()
        while (result.size < OPTION_COUNT - 1) {
            result.add("—")
        }
        return result
    }

    /** 英文干扰项：优先选长度/难度相近的词 */
    private fun pickDistractorsEn(
        correct: WordDetail,
        allWords: List<WordDetail>,
        random: Random,
    ): List<String> {
        // 同样按文本排除正确答案（大小写不敏感），避免选项重复
        val correctEn = correct.headWord.trim()
        val pool = allWords.filter {
            it.wordId != correct.wordId &&
                it.headWord.isNotBlank() &&
                !it.headWord.trim().equals(correctEn, ignoreCase = true)
        }
        val targetLen = correct.headWord.length
        val sorted = pool.sortedBy { Math.abs(it.headWord.length - targetLen) }
        val candidates = sorted.take(minOf(sorted.size, 20)).shuffled(random)
        val result = candidates
            .map { it.headWord }
            .filter { it.isNotBlank() && !it.trim().equals(correctEn, ignoreCase = true) }
            .distinct()
            .take(OPTION_COUNT - 1)
            .toMutableList()
        while (result.size < OPTION_COUNT - 1) {
            result.add("—")
        }
        return result
    }

    /** 提取单词中文释义（多个释义用 "；" 拼接） */
    fun WordDetail.chineseText(): String {
        return trans.mapNotNull { t ->
            listOfNotNull(t.pos, t.tranCn).joinToString(" ")
                .takeIf { it.isNotBlank() }
        }.joinToString("；")
            .ifBlank { this.headWord }
    }

    /**
     * 拼写模式校验答案：
     * - 忽略大小写、首尾空格
     * - 忽略连字符差异
     * - 完全一致 → Correct
     * - 编辑距离 ≤ 1 → AlmostCorrect
     * - 否则 → Wrong（包含字母级差异信息）
     */
    fun checkSpelling(userInput: String, correct: String): AnswerResult {
        val normalizedUser = normalizeForCompare(userInput)
        val normalizedCorrect = normalizeForCompare(correct)
        if (normalizedUser == normalizedCorrect) return AnswerResult.Correct
        val dist = editDistance(normalizedUser, normalizedCorrect)
        val diffInfo = computeCharDiff(userInput, correct)
        return if (dist <= 1) {
            AnswerResult.AlmostCorrect("差一点就对了！正确拼写：$correct", diffInfo)
        } else {
            AnswerResult.Wrong(userAnswer = userInput, correct = correct, diffInfo = diffInfo)
        }
    }

    /**
     * 计算字母级差异：按最长公共子序列匹配。
     *
     * 逐位比较在多写或少写一个字母时会把插入点之后的字母全部判错
     * （`dissipatte` 对 `dissipate` 会标出一长串红字），
     * 按公共子序列匹配后只标出真正多写、写错的那个字母。
     */
    private fun computeCharDiff(user: String, correct: String): List<CharDiff> {
        val n = user.length
        val m = correct.length
        // dp[i][j] = user[i..] 与 correct[j..] 的最长公共子序列长度
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (user[i].equals(correct[j], ignoreCase = true)) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }
        val result = ArrayList<CharDiff>(n)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                user[i].equals(correct[j], ignoreCase = true) -> {
                    result.add(CharDiff(user[i], true))
                    i++
                    j++
                }
                // 用户多写（或写错）一个字母：标红，继续用同一个正确字母比对
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    result.add(CharDiff(user[i], false))
                    i++
                }
                // 用户漏写一个字母：跳过正确词中的该字母
                else -> j++
            }
        }
        while (i < n) {
            result.add(CharDiff(user[i], false))
            i++
        }
        return result
    }

    /** 标准化：去首尾空格、转小写、去连字符 */
    private fun normalizeForCompare(s: String): String =
        s.trim().lowercase().replace("-", "").replace(" ", "")

    /** 最小编辑距离（Levenshtein） */
    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[a.length][b.length]
    }
}

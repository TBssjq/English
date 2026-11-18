package com.ssjq.english.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.ssjq.english.ui.glass.LocalAppBackdrop
import com.ssjq.english.ui.glass.glassTopBar
import com.ssjq.english.ui.glass.glassTopBarColors
import com.ssjq.english.data.CheckInManager
import com.ssjq.english.data.WordDetail
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.WordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun modeFromString(mode: String?): QuizMode? = when (mode) {
    "EnSelectCn" -> QuizMode.EnSelectCn
    "CnSelectEn" -> QuizMode.CnSelectEn
    "AudioSelect" -> QuizMode.AudioSelect
    "Spelling" -> QuizMode.Spelling
    "AudioSpelling" -> QuizMode.AudioSpelling
    else -> null
}

/**
 * 测验页入口：
 * @param dbName 词库名
 * @param questionCount 题目数量
 * @param sourceWords 可选：自定义词源（为空时从词库取前 80 词）
 * @param onBack 返回
 */
/**
 * 同一组出题参数对应的测验状态缓存。
 * 从测验页点「查看单词」再返回时 Compose 会重建页面，不复用状态就会重新出题，
 * 把用户送回第 1 题。最多保留 8 轮，避免长会话持续累积。
 */
private object QuizStateCache {
    // 只保留当前与上一轮：一轮含上百个完整词条，留太多会明显占内存
    private val states = object : LinkedHashMap<String, QuizState>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QuizState>?) = size > 2
    }

    fun get(key: String): QuizState? = states[key]

    fun put(key: String, state: QuizState) {
        states[key] = state
    }

    fun remove(key: String) {
        states.remove(key)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    dbName: String,
    questionCount: Int = 20,
    /** 模式名：null/空=随机，"EnSelectCn"/"CnSelectEn"/"AudioSelect"/"Spelling"=固定 */
    mode: String? = null,
    sourceWords: List<WordDetail> = emptyList(),
    /** 疯狂刷题题源标识，用于断点续做 */
    sourceKey: String? = null,
    onBack: () -> Unit,
    /** 从结果页打开某个单词的卡片：参数为词库名与 wordId */
    onOpenWord: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val routeSourceKey = sourceKey
    val fixedMode = remember(mode) { modeFromString(mode) }
    // key 必须带上出题参数：旧实现无 key，同一路由复用换词库/换题量时
    // 会继续沿用上一轮的 QuizState，出的还是旧词库的题
    // 不要用整个 sourceWords 列表做 key：List.equals 会逐元素深度比较，
    // 疯狂刷题最多 100 个深嵌套 WordDetail，每次重组都要跑一次 O(n·depth) 比较。
    // 用「数量 + 首尾 wordId」作为轻量且足够唯一的 key。
    val stateKey = remember(
        dbName,
        questionCount,
        sourceWords.size,
        sourceWords.firstOrNull()?.wordId,
        sourceWords.lastOrNull()?.wordId,
        fixedMode,
        routeSourceKey,
    ) {
        listOf(
            routeSourceKey.orEmpty(),
            dbName,
            questionCount.toString(),
            sourceWords.size.toString(),
            sourceWords.firstOrNull()?.wordId.orEmpty(),
            sourceWords.lastOrNull()?.wordId.orEmpty(),
            fixedMode?.label ?: "random",
        ).joinToString("|")
    }
    val state = remember(stateKey) {
        QuizStateCache.get(stateKey) ?: QuizState(
            context = context,
            dbName = dbName,
            sourceWords = sourceWords,
            questionCount = questionCount,
            fixedMode = fixedMode,
            // 自动发音是用户偏好，跨轮次保留
            autoPlayAudio = UserLibrary.autoPlayAudioEnabled(),
            sourceKey = routeSourceKey,
        ).also { QuizStateCache.put(stateKey, it) }
    }

    /**
     * 打开单词详情。疯狂刷题传入的 dbName 是占位符，必须使用词条自带的真实词库名，
     * 否则详情页会去打开不存在的词库而闪退。
     */
    fun openWordDetail(word: WordDetail) {
        val target = word.dbName?.takeIf { it.isNotBlank() && it != "crazy_quiz" }
        if (target == null) {
            android.widget.Toast.makeText(
                context,
                "该单词缺少词库信息，暂时无法打开",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        onOpenWord(target, word.wordId)
    }

    LaunchedEffect(state) { state.load() }

    // 离开页面时释放播放器，否则 MediaPlayer 会随 Activity 一起泄漏
    DisposableEffect(state) {
        onDispose { state.release() }
    }

    val uiState = state.uiState
    var favoriteToggle by remember { mutableStateOf(0) }
    // 答过题后退出需要确认，避免误触丢失本轮进度
    var showExitConfirm by remember { mutableStateOf(false) }

    /** 退出测验：先落盘断点，再丢掉内存缓存，下次进入只能从持久化进度恢复 */
    fun exitQuiz() {
        state.persistProgress(answered = uiState.answerResult !is AnswerResult.Idle)
        QuizStateCache.remove(stateKey)
        onBack()
    }
    // 全应用共享的背景采样层
    val liquidBackdrop = LocalAppBackdrop.current

    // 每次答错就地写入错题本：整轮结束才批量写入的旧做法，遇到中途退出、
    // 结果页之前杀进程，这一轮的错题就全丢了
    LaunchedEffect(uiState.answerResult, uiState.currentIndex) {
        val shouldMark = when (val result = uiState.answerResult) {
            is AnswerResult.Wrong -> true
            is AnswerResult.Skipped -> result.markedWrong
            else -> false
        }
        if (!shouldMark) return@LaunchedEffect
        val word = uiState.currentQuestion?.word ?: return@LaunchedEffect
        val entry = WordEntry(
            wordId = word.wordId,
            headWord = word.headWord,
            dbName = word.dbName ?: dbName,
            tranCn = word.trans.firstOrNull()?.tranCn?.takeIf { it.isNotBlank() },
        )
        withContext(Dispatchers.IO) { UserLibrary.addWrong(entry) }
    }

    // 测验完成时自动打卡：答对数 + 总题数 + 学过的单词数。
    // consumeFinishReport() 是一次性哨兵，保证同一轮结果只上报一次
    LaunchedEffect(uiState.isFinished, uiState.totalQuestions) {
        if (!uiState.isFinished) return@LaunchedEffect
        val report = state.consumeFinishReport() ?: return@LaunchedEffect
        // 本轮已结束，清掉缓存：否则同参数再进测验会直接看到上一轮的结果页
        QuizStateCache.remove(stateKey)
        // 整段放到 IO：accumulate() 会把全部打卡记录 JSON 反序列化再写回，
        // saveQuizRecord() 也会解析/序列化刷题记录，记录较多时在主线程会造成卡顿
        withContext(Dispatchers.IO) {
            CheckInManager.accumulate(
                addQuizCorrect = report.correctCount,
                addQuizTotal = report.totalQuestions,
                addWordsLearned = report.totalQuestions,
            )
            // 保存刷题记录
            val accuracy = if (report.totalQuestions > 0) {
                (report.correctCount.toDouble() / report.totalQuestions * 100).toInt()
            } else {
                0
            }
            val record = org.json.JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("dbName", dbName)
                put("totalQuestions", report.totalQuestions)
                put("correctCount", report.correctCount)
                put("accuracy", accuracy)
            }.toString()
            com.ssjq.english.data.UserManager.saveQuizRecord(record)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.glassTopBar(liquidBackdrop),
                colors = glassTopBarColors(),
                title = { Text("快速测验", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            val hasProgress = uiState.currentIndex > 0 ||
                                uiState.answerResult !is AnswerResult.Idle
                            if (!uiState.isFinished && hasProgress) {
                                showExitConfirm = true
                            } else {
                                exitQuiz()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 题目数量：切换后按新题量重新出题
                    var showCountMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { showCountMenu = true }) {
                            Text(
                                "${state.questionCountLimit} 题",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = showCountMenu,
                            onDismissRequest = { showCountMenu = false },
                        ) {
                            listOf(10, 20, 30, 50, 100).forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (option == state.questionCountLimit) "✓ $option 题" else "$option 题",
                                        )
                                    },
                                    onClick = {
                                        showCountMenu = false
                                        state.setQuestionCount(option)
                                    },
                                )
                            }
                        }
                    }
                    // 自动播放发音开关
                    IconButton(
                        onClick = {
                            val enabled = !state.autoPlayAudio
                            state.setAutoPlayAudioEnabled(enabled)
                            UserLibrary.setAutoPlayAudio(enabled)
                        },
                    ) {
                        Icon(
                            Icons.Filled.Headphones,
                            if (state.autoPlayAudio) "已开启自动发音" else "开启自动发音",
                            tint = if (state.autoPlayAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 收藏当前单词
                    IconButton(
                        onClick = {
                            val word = uiState.currentQuestion?.word ?: return@IconButton
                            // 疯狂刷题会混合多个词库出题，路由里的 dbName 只是占位符；
                            // 错题/收藏必须写回单词真实所属词库
                            val ownerDb = word.dbName ?: dbName
                            val entry = WordEntry(
                                wordId = word.wordId,
                                headWord = word.headWord,
                                dbName = ownerDb,
                                tranCn = word.trans.firstOrNull()?.tranCn?.takeIf { it.isNotBlank() },
                            )
                            if (UserLibrary.isFavorite(ownerDb, word.wordId)) {
                                UserLibrary.removeFavorite(ownerDb, word.wordId)
                            } else {
                                UserLibrary.addFavorite(entry)
                            }
                            favoriteToggle++
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        val isFav = remember(favoriteToggle, uiState.currentQuestion) {
                            uiState.currentQuestion?.word?.let {
                                UserLibrary.isFavorite(it.dbName ?: dbName, it.wordId)
                            } ?: false
                        }
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (isFav) "已收藏" else "收藏",
                            tint = if (isFav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 顶部统计：✅ ❌
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(Icons.Filled.Check, "正确",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${uiState.correctCount}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Close, "错误",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${uiState.wrongCount}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.isFinished) {
            ResultScreen(
                correctCount = uiState.correctCount,
                totalCount = uiState.totalQuestions,
                // 用去重后的数量：小题库会循环出词，同一词可能被错多次，
                // 否则结果页「错题回顾(N)」的数字会大于实际回顾题量
                wrongWords = state.distinctWrongCount(),
                wrongWordList = uiState.wrongWords.distinct(),
                onOpenWord = { word -> openWordDetail(word) },
                modeStats = uiState.modeStats,
                onRestart = { state.dispatch(QuizIntent.Restart) },
                onReviewWrong = { state.dispatch(QuizIntent.ReviewWrong) },
                modifier = Modifier.fillMaxSize().padding(padding),
                previousRecord = state.previousRecord,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                // 顶部进度条
                val progress = if (uiState.totalQuestions > 0)
                    (uiState.currentIndex + 1).toFloat() / uiState.totalQuestions else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
                // 题号
                Text(
                    "第 ${uiState.currentIndex + 1} / ${uiState.totalQuestions} 题",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )

                // 题目区：左右滑动切题动画
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    uiState.currentQuestion?.let { question ->
                        AnimatedContent(
                            targetState = question,
                            transitionSpec = {
                                slideInHorizontally(
                                    initialOffsetX = { it / 3 },
                                    animationSpec = tween(250),
                                ) + fadeIn(tween(250)) togetherWith
                                    slideOutHorizontally(
                                        targetOffsetX = { -it / 3 },
                                        animationSpec = tween(250),
                                    ) + fadeOut(tween(250))
                            },
                            label = "questionSlide",
                            modifier = Modifier.fillMaxSize(),
                        ) { targetQuestion ->
                            // 使用 targetState 快照：过渡期间旧槽位必须显示旧题，
                            // 否则退出动画里内容会被新题覆盖
                            QuizModeContent(
                                question = targetQuestion,
                                answerResult = uiState.answerResult,
                                spellingInput = uiState.spellingInput,
                                onSelectOption = { state.dispatch(QuizIntent.SelectOption(it)) },
                                onSpellingChange = { state.dispatch(QuizIntent.UpdateSpelling(it)) },
                                onSpellingSubmit = { state.dispatch(QuizIntent.SubmitSpelling) },
                                onReplayAudio = { state.dispatch(QuizIntent.ReplayAudio) },
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                // 底部反馈卡片
                AnswerFeedbackCard(
                    result = uiState.answerResult,
                    correctAnswer = uiState.currentQuestion?.correctAnswer ?: "",
                    exampleSentence = uiState.currentQuestion?.word?.sentences?.firstOrNull()?.content,
                    onNext = { state.dispatch(QuizIntent.NextQuestion) },
                    onSkip = { state.dispatch(QuizIntent.SkipQuestion(it)) },
                    streak = uiState.streak,
                    word = uiState.currentQuestion?.word,
                    // 答错时就地进入该单词的卡片，不必等到结果页
                    onOpenWord = {
                        uiState.currentQuestion?.word?.let { openWordDetail(it) }
                    },
                    backdrop = liquidBackdrop,
                )
            }
        }
    }
        if (showExitConfirm) {
            AlertDialog(
                onDismissRequest = { showExitConfirm = false },
                title = { Text("退出测验") },
                text = { Text("当前进度已保存，下次选择同一题源和题量可以继续，确定退出？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitConfirm = false
                            exitQuiz()
                        }
                    ) {
                        Text("退出并保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirm = false }) {
                        Text("继续答题")
                    }
                },
            )
        }
    } // end of Box (liquid glass background)
}

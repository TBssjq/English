package com.ssjq.english.quiz

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssjq.english.data.CheckInManager
import com.ssjq.english.data.WordDetail
import com.ssjq.english.ui.common.FancyToast

/**
 * 测验页入口：
 * @param dbName 词库名
 * @param questionCount 题目数量
 * @param sourceWords 可选：自定义词源（为空时从词库取前 80 词）
 * @param onBack 返回
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    dbName: String,
    questionCount: Int = 20,
    /** 模式名：null/空=随机，"EnSelectCn"/"CnSelectEn"/"AudioSelect"/"Spelling"=固定 */
    mode: String? = null,
    sourceWords: List<WordDetail> = emptyList(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val fixedMode = remember(mode) { modeFromString(mode) }
    val state = remember {
        QuizState(
            context = context,
            dbName = dbName,
            sourceWords = sourceWords,
            questionCount = questionCount,
            fixedMode = fixedMode,
        )
    }

    // 通知权限申请：切到后台继续需要（Android 13+）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            com.ssjq.english.service.LearningForegroundService.start(context, dbName)
            onBack()
        }
    }

    LaunchedEffect(Unit) { state.load() }

    val uiState = state.uiState
    val showFancyToast = remember { mutableStateOf(false) }

    // 测验完成时自动打卡：答对数 + 总题数 + 学过的单词数
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) {
            CheckInManager.accumulate(
                addQuizCorrect = uiState.correctCount,
                addQuizTotal = uiState.totalQuestions,
                addWordsLearned = uiState.totalQuestions,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快速测验", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (com.ssjq.english.service.NotificationPermission.hasPermission(context)) {
                                com.ssjq.english.service.LearningForegroundService.start(context, dbName)
                                showFancyToast.value = true
                            } else {
                                notificationPermissionLauncher.launch(
                                    com.ssjq.english.service.NotificationPermission.permission
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Headphones, "切到后台继续")
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
                wrongWords = uiState.wrongWords.size,
                modeStats = uiState.modeStats,
                onRestart = { state.dispatch(QuizIntent.Restart) },
                onReviewWrong = { state.dispatch(QuizIntent.ReviewWrong) },
                modifier = Modifier.fillMaxSize().padding(padding),
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
                            targetState = question.id,
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
                        ) { _ ->
                            QuizModeContent(
                                question = question,
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
                )
            }
        }
    }

    FancyToast(
        message = "已切换到后台继续复习",
        visible = showFancyToast.value,
        onDismiss = { showFancyToast.value = false },
    )
}

/** 模式字符串 → QuizMode；null/空/未知 → null（随机） */
private fun modeFromString(mode: String?): QuizMode? = when (mode) {
    "EnSelectCn" -> QuizMode.EnSelectCn
    "CnSelectEn" -> QuizMode.CnSelectEn
    "AudioSelect" -> QuizMode.AudioSelect
    "Spelling" -> QuizMode.Spelling
    else -> null
}

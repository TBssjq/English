package com.ssjq.english.quiz

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssjq.english.R

/** 选项按钮状态 */
enum class OptionState { Idle, Correct, Wrong, WrongHighlight }

/**
 * 通用选项按钮：
 * - Idle：白底灰边
 * - Correct：绿底白字 + ✓
 * - Wrong：红底白字 + ✗（用户选错的那个）
 * - WrongHighlight：浅绿边（正确答案，在用户选错时高亮）
 */
@Composable
fun OptionButton(
    text: String,
    state: OptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = when (state) {
            OptionState.Idle -> MaterialTheme.colorScheme.surface
            OptionState.Correct -> MaterialTheme.colorScheme.primary
            OptionState.Wrong -> MaterialTheme.colorScheme.error
            OptionState.WrongHighlight -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(200),
        label = "optColor",
    )
    val contentColor by animateColorAsState(
        targetValue = when (state) {
            OptionState.Idle -> MaterialTheme.colorScheme.onSurface
            OptionState.Correct -> MaterialTheme.colorScheme.onPrimary
            OptionState.Wrong -> MaterialTheme.colorScheme.onError
            OptionState.WrongHighlight -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(200),
        label = "optText",
    )
    val borderColor by animateColorAsState(
        targetValue = when (state) {
            OptionState.Idle -> MaterialTheme.colorScheme.outlineVariant
            OptionState.Correct -> MaterialTheme.colorScheme.primary
            OptionState.Wrong -> MaterialTheme.colorScheme.error
            OptionState.WrongHighlight -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(200),
        label = "optBorder",
    )

    Card(
        onClick = onClick,
        enabled = state == OptionState.Idle,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (isEnglishText(text)) FontFamily.Serif else FontFamily.Default,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                color = contentColor,
            )
            if (state == OptionState.Correct) {
                Icon(Icons.Filled.Check, "正确", tint = contentColor, modifier = Modifier.size(20.dp))
            } else if (state == OptionState.Wrong) {
                Icon(Icons.Filled.Close, "错误", tint = contentColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 简单判断文本是否为英文 */
private fun isEnglishText(text: String): Boolean {
    return text.all { it.isLetter() && it.code < 256 || it in " -'" } && text.any { it.isLetter() }
}

/**
 * 4 种模式的题目区 UI，按 mode 分发。
 */
@Composable
fun QuizModeContent(
    question: QuizQuestion,
    answerResult: AnswerResult,
    spellingInput: String,
    onSelectOption: (String) -> Unit,
    onSpellingChange: (String) -> Unit,
    onSpellingSubmit: () -> Unit,
    onReplayAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (question.mode) {
        is QuizMode.EnSelectCn -> EnSelectCnContent(
            question = question,
            answerResult = answerResult,
            onSelect = onSelectOption,
            modifier = modifier,
        )
        is QuizMode.CnSelectEn -> CnSelectEnContent(
            question = question,
            answerResult = answerResult,
            onSelect = onSelectOption,
            onReplayAudio = onReplayAudio,
            modifier = modifier,
        )
        is QuizMode.AudioSelect -> AudioSelectContent(
            question = question,
            answerResult = answerResult,
            onSelect = onSelectOption,
            onReplay = onReplayAudio,
            modifier = modifier,
        )
        is QuizMode.Spelling -> SpellingContent(
            question = question,
            answerResult = answerResult,
            input = spellingInput,
            onInputChange = onSpellingChange,
            onSubmit = onSpellingSubmit,
            onReplayAudio = onReplayAudio,
            modifier = modifier,
        )
    }
}

/** 英选中：英文大标题 + 音标 + 4个中文选项 */
@Composable
private fun EnSelectCnContent(
    question: QuizQuestion,
    answerResult: AnswerResult,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = question.promptText,
            style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (question.phonetic.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "/${question.phonetic}/",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
        OptionsList(
            options = question.options,
            correctAnswer = question.correctAnswer,
            answerResult = answerResult,
            onSelect = onSelect,
        )
    }
}

/** 中选英：中文大标题 + 4个英文选项 */
@Composable
private fun CnSelectEnContent(
    question: QuizQuestion,
    answerResult: AnswerResult,
    onSelect: (String) -> Unit,
    onReplayAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = question.promptText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (question.phonetic.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "/${question.phonetic}/",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
        OptionsList(
            options = question.options,
            correctAnswer = question.correctAnswer,
            answerResult = answerResult,
            onSelect = onSelect,
        )
    }
}

/** 听音辨意：中央播放按钮 + 4个中文选项 */
@Composable
private fun AudioSelectContent(
    question: QuizQuestion,
    answerResult: AnswerResult,
    onSelect: (String) -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 自动播放由 QuizState 统一控制（避免与 ViewModel 的 speak() 重复触发导致 MediaPlayer 竞争）
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "请听发音，选择正确释义",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        // 大播放按钮
        IconButton(
            onClick = onReplay,
            modifier = Modifier.size(96.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Image(
                painter = painterResource(R.drawable.phonograph),
                contentDescription = "播放发音",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "点击重播",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OptionsList(
            options = question.options,
            correctAnswer = question.correctAnswer,
            answerResult = answerResult,
            onSelect = onSelect,
        )
    }
}

/** 拼写测试：中文提示 + 输入框 + 提交按钮 */
@Composable
private fun SpellingContent(
    question: QuizQuestion,
    answerResult: AnswerResult,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReplayAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val idle = answerResult is AnswerResult.Idle

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            question.promptText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (question.phonetic.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "/${question.phonetic}/",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { onReplayAudio() }, modifier = Modifier.size(32.dp)) {
                    Image(
                        painter = painterResource(R.drawable.phonograph),
                        contentDescription = "播放发音",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text("输入英文单词…") },
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            singleLine = true,
            enabled = idle,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (idle && input.isNotBlank()) onSubmit() }),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "已输入 ${input.length} / 目标 ${question.correctAnswer.length} 字符",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = idle && input.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("提交答案", fontWeight = FontWeight.SemiBold)
        }
    }

    LaunchedEffect(Unit) {
        if (idle) {
            try { focusRequester.requestFocus() } catch (_: Throwable) {}
        }
    }
}

/** 选项列表公共组件 */
@Composable
private fun OptionsList(
    options: List<String>,
    correctAnswer: String,
    answerResult: AnswerResult,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            val state = when (answerResult) {
                is AnswerResult.Idle -> OptionState.Idle
                is AnswerResult.Correct -> {
                    if (option == correctAnswer) OptionState.Correct else OptionState.Idle
                }
                is AnswerResult.AlmostCorrect -> {
                    if (option == correctAnswer) OptionState.Correct else OptionState.Idle
                }
                is AnswerResult.Wrong -> {
                    when {
                        option == correctAnswer -> OptionState.WrongHighlight
                        option == answerResult.userAnswer -> OptionState.Wrong
                        else -> OptionState.Idle
                    }
                }
            }
            OptionButton(
                text = option,
                state = state,
                onClick = { onSelect(option) },
            )
        }
    }
}

package com.ssjq.english.quiz

import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow
import com.ssjq.english.ui.glass.GlassQuality

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ssjq.english.data.WordDetail
import com.ssjq.english.ui.common.LiquidGlassCard

/**
 * 答题反馈卡片：底部滑入，显示正确/错误/差一点。
 * @param result 当前答题结果
 * @param correctAnswer 正确答案
 * @param exampleSentence 例句（可选）
 * @param onNext 下一题按钮点击
 * @param onOpenWord 打开当前单词的卡片（仅在答错时出现）
 */
@Composable
fun AnswerFeedbackCard(
    result: AnswerResult,
    correctAnswer: String,
    modifier: Modifier = Modifier,
    exampleSentence: String? = null,
    onNext: () -> Unit,
    /** true = 标记错题并跳过，false = 仅跳过 */
    onSkip: (Boolean) -> Unit,
    onOpenWord: (() -> Unit)? = null,
    backdrop: Backdrop,
    /** 本轮连续答对数，>=2 时给递进式鼓励 */
    streak: Int = 0,
    /** 当前词条：答对时也要给出英文词形与中文释义 */
    word: WordDetail? = null,
) {
    AnimatedContent(
        // 使用完整结果作为 targetState：退出动画期间旧槽位保留旧结果，
        // 避免刚答完的反馈内容被下一次结果覆盖
        targetState = result,
        transitionSpec = {
            slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) +
                fadeIn(tween(250)) togetherWith
                slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) +
                fadeOut(tween(200))
        },
        label = "feedback",
        modifier = modifier.fillMaxWidth(),
    ) { targetResult ->
        when (targetResult) {
            is AnswerResult.Correct -> CorrectFeedback(
                onNext, backdrop, streak, word, correctAnswer, exampleSentence, onOpenWord
            )
            is AnswerResult.AlmostCorrect -> AlmostCorrectFeedback(
                targetResult.tip, onNext, backdrop, word, correctAnswer, exampleSentence, onOpenWord
            )
            is AnswerResult.Wrong -> WrongFeedback(
                userAnswer = targetResult.userAnswer,
                correctAnswer = correctAnswer,
                exampleSentence = exampleSentence,
                onNext = onNext,
                onOpenWord = onOpenWord,
                backdrop = backdrop,
            )
            is AnswerResult.Skipped -> SkippedFeedback(
                marked = targetResult.markedWrong,
                onNext = onNext,
                backdrop = backdrop,
                word = word,
                correctAnswer = correctAnswer,
                exampleSentence = exampleSentence,
                onOpenWord = onOpenWord,
            )
            is AnswerResult.Idle -> IdleActions(onSkip = onSkip, backdrop = backdrop)
        }
    }
}

/** 未作答时的操作条：主动标记不会，或直接跳过本题 */
@Composable
private fun IdleActions(
    onSkip: (Boolean) -> Unit,
    backdrop: Backdrop,
) {
    LiquidGlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        blurRadius = GlassQuality.adaptiveBlur(6.dp, 0.85f),
        lensHeight = 10.dp,
        lensAmount = 18.dp,
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        shadow = glassShadow(14.dp, 0.16f),
        highlight = Highlight.Default.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { onSkip(false) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("跳过", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = { onSkip(true) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("我不会", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 跳过后的反馈：模糊更清晰，突出下一步动作 */
@Composable
private fun SkippedFeedback(
    marked: Boolean,
    onNext: () -> Unit,
    backdrop: Backdrop,
    word: WordDetail?,
    correctAnswer: String,
    exampleSentence: String?,
    onOpenWord: (() -> Unit)?,
) {
    LiquidGlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        blurRadius = GlassQuality.adaptiveBlur(7.dp, 0.75f),
        lensHeight = 10.dp,
        lensAmount = 18.dp,
        surfaceColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shadow = glassShadow(16.dp, 0.18f),
        highlight = Highlight.Default.copy(alpha = 0.6f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (marked) "已跳过，并记入错题本" else "已跳过本题",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            AnswerDetail(
                word, correctAnswer, exampleSentence,
                MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(12.dp))
            OpenWordButton(onOpenWord, MaterialTheme.colorScheme.onSecondaryContainer)
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("下一题", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 所有反馈共用：先给正确选项，再给词形与中文释义、例句 */
@Composable
private fun AnswerDetail(
    word: WordDetail?,
    correctAnswer: String,
    exampleSentence: String?,
    contentColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "正确答案：",
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor.copy(alpha = 0.72f),
        )
        Text(
            correctAnswer,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
    Spacer(Modifier.height(8.dp))
    WordInsight(word, correctAnswer, contentColor)
    if (!exampleSentence.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            exampleSentence,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = contentColor.copy(alpha = 0.75f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 任何操作后都能跳到正确单词的卡片 */
@Composable
private fun OpenWordButton(
    onOpenWord: (() -> Unit)?,
    contentColor: Color,
) {
    if (onOpenWord != null) {
        OutlinedButton(
            onClick = onOpenWord,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        ) {
            Text("查看单词卡片", fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 答对与差一点也要看词形与中文释义，让反馈本身成为一次复习 */
@Composable
private fun WordInsight(
    word: WordDetail?,
    fallback: String,
    contentColor: Color,
) {
    Text(
        word?.headWord?.takeIf { it.isNotBlank() } ?: fallback,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = contentColor,
    )
    word?.trans?.firstOrNull()?.let { tran ->
        val meaning = listOfNotNull(tran.pos, tran.tranCn).joinToString("  ")
        if (meaning.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun CorrectFeedback(
    onNext: () -> Unit,
    backdrop: Backdrop,
    streak: Int,
    word: WordDetail?,
    correctAnswer: String,
    exampleSentence: String?,
    onOpenWord: (() -> Unit)?,
) {
    LiquidGlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        blurRadius = GlassQuality.adaptiveBlur(8.dp, 1.15f),
        lensHeight = 12.dp,
        lensAmount = 20.dp,
        surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        shadow = glassShadow(16.dp, 0.2f),
        highlight = Highlight.Default.copy(alpha = 0.7f),
        innerShadow = glassInnerShadow(6.dp, 0.06f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle, "正确",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    // 连续答对时给出具体数字，让正强化随投入递增
                    when {
                        streak >= 5 -> "连对 $streak 题，状态绝佳！"
                        streak >= 3 -> "连对 $streak 题，继续保持！"
                        streak >= 2 -> "连对 2 题！"
                        else -> "答对了！真棒！"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            AnswerDetail(
                word, correctAnswer, exampleSentence,
                MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            OpenWordButton(onOpenWord, MaterialTheme.colorScheme.onPrimaryContainer)
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("下一题", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AlmostCorrectFeedback(
    tip: String,
    onNext: () -> Unit,
    backdrop: Backdrop,
    word: WordDetail?,
    correctAnswer: String,
    exampleSentence: String?,
    onOpenWord: (() -> Unit)?,
) {
    LiquidGlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        blurRadius = GlassQuality.adaptiveBlur(8.dp, 1f),
        lensHeight = 12.dp,
        lensAmount = 20.dp,
        surfaceColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        shadow = glassShadow(16.dp, 0.2f),
        highlight = Highlight.Default.copy(alpha = 0.7f),
        innerShadow = glassInnerShadow(6.dp, 0.06f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lightbulb, "差一点",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "差一点就对了！",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tip,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            AnswerDetail(
                word, correctAnswer, exampleSentence,
                MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(12.dp))
            OpenWordButton(onOpenWord, MaterialTheme.colorScheme.onTertiaryContainer)
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Text("下一题", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WrongFeedback(
    userAnswer: String,
    correctAnswer: String,
    exampleSentence: String?,
    onNext: () -> Unit,
    onOpenWord: (() -> Unit)?,
    backdrop: Backdrop,
) {
    LiquidGlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        blurRadius = GlassQuality.adaptiveBlur(8.dp, 0.75f),
        lensHeight = 12.dp,
        lensAmount = 20.dp,
        surfaceColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        shadow = glassShadow(16.dp, 0.2f),
        highlight = Highlight.Default.copy(alpha = 0.7f),
        innerShadow = glassInnerShadow(6.dp, 0.06f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Error, "错误",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "答错了",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "你的答案：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                )
                Text(
                    userAnswer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "正确答案：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                )
                Text(
                    correctAnswer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!exampleSentence.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "例句：$exampleSentence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onOpenWord != null) {
                    OutlinedButton(
                        onClick = onOpenWord,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("查看单词", fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("下一题", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

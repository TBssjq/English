package com.ssjq.english.quiz

import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ssjq.english.ui.common.LiquidGlassCard

/**
 * 答题反馈卡片：底部滑入，显示正确/错误/差一点。
 * @param result 当前答题结果
 * @param correctAnswer 正确答案
 * @param exampleSentence 例句（可选）
 * @param onNext 下一题按钮点击
 */
@Composable
fun AnswerFeedbackCard(
    result: AnswerResult,
    correctAnswer: String,
    exampleSentence: String? = null,
    onNext: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = result !is AnswerResult.Idle,
        transitionSpec = {
            slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) +
                fadeIn(tween(250)) togetherWith
                slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) +
                fadeOut(tween(200))
        },
        label = "feedback",
        modifier = modifier.fillMaxWidth(),
    ) { visible ->
        if (!visible) {
            Box(Modifier.height(0.dp))
        } else {
            when (result) {
                is AnswerResult.Correct -> CorrectFeedback(onNext, backdrop)
                is AnswerResult.AlmostCorrect -> AlmostCorrectFeedback(result.tip, onNext, backdrop)
                is AnswerResult.Wrong -> WrongFeedback(
                    userAnswer = result.userAnswer,
                    correctAnswer = correctAnswer,
                    exampleSentence = exampleSentence,
                    onNext = onNext,
                    backdrop = backdrop,
                )
                is AnswerResult.Idle -> Box(Modifier.height(0.dp))
            }
        }
    }
}

@Composable
private fun CorrectFeedback(onNext: () -> Unit, backdrop: Backdrop) {
    LiquidGlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        blurRadius = 8.dp,
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
                    "答对了！真棒！",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
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
private fun AlmostCorrectFeedback(tip: String, onNext: () -> Unit, backdrop: Backdrop) {
    LiquidGlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        blurRadius = 8.dp,
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
            Spacer(Modifier.height(16.dp))
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
    backdrop: Backdrop,
) {
    LiquidGlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        blurRadius = 8.dp,
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
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(48.dp),
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

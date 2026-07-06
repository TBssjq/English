package com.ssjq.english.quiz

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 结果统计页：环形进度 + 正确率 + 各模式分布 + 错题回顾/再来一轮按钮
 */
@Composable
fun ResultScreen(
    correctCount: Int,
    totalCount: Int,
    wrongWords: Int,
    modeStats: Map<QuizMode, Pair<Int, Int>>,
    onRestart: () -> Unit,
    onReviewWrong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accuracy = if (totalCount > 0) correctCount.toFloat() / totalCount else 0f
    val accuracyPercent = (accuracy * 100).roundToInt()
    val encouragement = when {
        accuracy >= 0.9 -> "太棒了！你已经完全掌握！🎉"
        accuracy >= 0.7 -> "不错！继续保持！💪"
        accuracy >= 0.5 -> "还可以，再接再厉！📚"
        else -> "别灰心，多练习就好！🌱"
    }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            Icons.Outlined.EmojiEvents, "成绩",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "测验完成！",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            encouragement,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        // 环形进度（用 Canvas 绘制太复杂，用两个圆叠加模拟）
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 背景圈
            Box(
                modifier = Modifier.size(180.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            // 进度弧（简化版：用扇形近似 - 这里用一个圆形覆盖在上面做百分比展示）
            Box(
                modifier = Modifier.size(140.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$accuracyPercent%",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        "正确率",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "答对 $correctCount / $totalCount 题",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(32.dp))

        // 各模式统计
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "各模式表现",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                QuizMode.all.forEach { mode ->
                    val (correct, total) = modeStats[mode] ?: (0 to 0)
                    if (total > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                mode.label,
                                modifier = Modifier.width(80.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            // 进度条背景
                            Box(
                                modifier = Modifier.weight(1f).height(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                val pct = if (total > 0) correct.toFloat() / total else 0f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(pct)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "$correct/$total",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(48.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // 操作按钮
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("再来一轮", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onReviewWrong,
                enabled = wrongWords > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.School, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("错题回顾 ($wrongWords)", fontWeight = FontWeight.Medium)
            }
        }
    }
}

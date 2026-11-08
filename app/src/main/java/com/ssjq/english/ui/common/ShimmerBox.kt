package com.ssjq.english.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 骨架屏占位块：带呼吸式 alpha 动画，用于数据加载时的占位，
 * 比空白或 Loading 圈体验更好。
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    ShimmerBox(modifier, rememberShimmerAlpha())
}

/**
 * 多个占位块共享同一个呼吸动画。
 * 每个 InfiniteTransition 都会持续驱动重组，骨架屏里几十个实例各自创建开销明显。
 */
@Composable
fun rememberShimmerAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    return alpha
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, alpha: Float) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

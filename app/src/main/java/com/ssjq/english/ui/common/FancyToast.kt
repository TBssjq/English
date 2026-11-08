package com.ssjq.english.ui.common

import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.highlight.Highlight
import com.ssjq.english.ui.glass.GlassShapes
import kotlinx.coroutines.delay

/**
 * 通用 Toast。
 *
 * @param backdrop 传入页面背景采样源时呈现液态玻璃样式；为 null 时降级为纯色样式，
 *                 保证在尚未接入玻璃的页面同样可用。
 */
@Composable
fun FancyToast(
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null,
) {
    val containerColor = MaterialTheme.colorScheme.inverseSurface
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        Box(
            modifier = Modifier.padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (backdrop != null) {
                LiquidGlassCard(
                    backdrop = backdrop,
                    shape = GlassShapes.Card,
                    blurRadius = 6.dp,
                    lensHeight = 8.dp,
                    lensAmount = 14.dp,
                    surfaceColor = containerColor.copy(alpha = 0.85f),
                    shadow = glassShadow(12.dp, 0.25f),
                    highlight = Highlight.Default.copy(alpha = 0.7f),
                    innerShadow = glassInnerShadow(4.dp, 0.25f),
                ) {
                    // LiquidGlassCard 的内容作用域是 Box，直接放 Icon+Spacer+Text 会重叠，
                    // 必须用 Row 承载（与下方纯色分支保持一致）
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToastContent(message, contentColor)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .background(containerColor, GlassShapes.Card)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToastContent(message, contentColor)
                }
            }
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            delay(2500)
            onDismiss()
        }
    }
}

@Composable
private fun ToastContent(message: String, contentColor: Color) {
    Icon(
        Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = contentColor,
    )
    Spacer(Modifier.width(10.dp))
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = contentColor,
    )
}

package com.ssjq.english.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * 非交互型液态玻璃容器。
 *
 * 与 [LiquidButton] 共用库的同一套能力，只是不带手势与按压高光，
 * 用来承载搜索框、面板、内嵌按钮等本身不可点击的玻璃块。
 */
@Composable
fun LiquidCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.Card,
    tint: Color = Color.Unspecified,
    surfaceColor: Color? = null,
    blurRadius: Dp = 6.dp,
    lensHeight: Dp = 14.dp,
    lensAmount: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalGlassPalette.current
    val resolvedSurfaceColor = surfaceColor ?: palette.surfaceTint
    val highlight = Highlight.Default.copy(alpha = if (palette.isDark) 0.7f else 0.55f)
    val shadow = Shadow(radius = 18f.dp, color = palette.shadowColor)
    val glass = rememberGlassSurfaceModifier(
        backdrop = backdrop,
        shape = shape,
        blurRadius = blurRadius,
        lensHeight = lensHeight,
        lensAmount = lensAmount,
        surfaceColor = resolvedSurfaceColor,
        overlayColor = if (tint.isSpecified) tint.copy(alpha = 0.22f) else Color.Unspecified,
        highlight = highlight,
        shadow = shadow,
        innerShadow = null,
        enableChromaticAberration = false,
        enableVibrancy = false,
    )
    Box(modifier = modifier.then(glass), content = content)
}

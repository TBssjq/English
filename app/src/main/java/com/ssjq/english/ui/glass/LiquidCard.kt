package com.ssjq.english.ui.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * 非交互型液态玻璃容器。
 *
 * 与 [LiquidButton] 共用库的同一套能力（`drawBackdrop` + `blur` / `lens` / `Highlight`），
 * 只是不带手势与按压高光，用来承载搜索框、面板、内嵌按钮等**本身不可点击**的玻璃块。
 * 玻璃效果本身全部由库计算，这里没有任何自绘的模糊 / 折射逻辑。
 *
 * 可点击的玻璃块请直接用 [LiquidButton]。
 */
@Composable
fun LiquidCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = Color.Unspecified,
    surfaceColor: Color = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.30f)
    },
    blurRadius: Dp = 6.dp,
    lensHeight: Dp = 14.dp,
    lensAmount: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(blurRadius.toPx())
                    lens(lensHeight.toPx(), lensAmount.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = if (dark) 0.7f else 0.55f) },
                shadow = {
                    Shadow(
                        radius = 18f.dp,
                        color = Color.Black.copy(alpha = if (dark) 0.30f else 0.14f)
                    )
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint.copy(alpha = 0.22f))
                    }
                    drawRect(surfaceColor)
                },
            ),
        content = content,
    )
}

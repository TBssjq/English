package com.ssjq.english.ui.glass

import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/** 形状 lambda 必须稳定：ShapeProvider 以它为缓存键，每次重组新建会重建整条效果链 */
private val TopBarShapeBlock: () -> Shape = GlassShapes.TopBarBlock

/**
 * M3 顶栏的液态玻璃底板。
 *
 * Material3 的 `TopAppBar` / `LargeTopAppBar` 把调用方传入的 modifier 放在最外层，
 * 状态栏 insets 与滚动折叠偏移都在内层处理，因此在这里叠加 [drawBackdrop] 可以覆盖
 * 整条顶栏（含状态栏区域与折叠动画），同时保留折叠行为、insets 与无障碍语义。
 *
 * 顶栏自身的容器色必须设为透明（配合 [glassTopBarColors]），玻璃才会透出。
 *
 * modifier 用 [remember] 缓存：页面重组时若每次都新建 modifier，
 * DrawBackdropNode 会被判定为变更而重新录制背景采样，输入框逐字输入这类高频重组会明显掉帧。
 */
@Composable
fun Modifier.glassTopBar(backdrop: Backdrop): Modifier {
    val palette = LocalGlassPalette.current
    val surface = palette.surfaceTint
    val highlightAlpha = if (palette.isDark) 0.85f else 0.55f
    val glowColor = palette.glowColor
    return remember(backdrop, surface, highlightAlpha, glowColor) {
        drawBackdrop(
            backdrop = backdrop,
            shape = TopBarShapeBlock,
            effects = {
                if (GlassQuality.vibrancyEnabled) colorControls(saturation = 1.8f)
                blur(14.dp.toPx() * GlassQuality.blurRadiusPxFactor)
                if (GlassQuality.lensEnabled) {
                    lens(
                        refractionHeight = 12.dp.toPx(),
                        refractionAmount = 22.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                }
            },
            highlight = { Highlight.Default.copy(alpha = highlightAlpha) },
            shadow = { Shadow(radius = 20.dp, color = glowColor) },
            onDrawSurface = { drawRect(surface) },
        )
    }
}

/**
 * 与 [glassTopBar] 配套的顶栏配色：容器色透明，内容色沿用 M3 默认值。
 * 小顶栏与大顶栏共用同一份颜色定义。
 */
@Composable
fun glassTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
)

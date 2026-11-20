package com.ssjq.english.ui.glass

import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * M3 顶栏的液态玻璃底板。
 *
 * Material3 的 TopAppBar / LargeTopAppBar 把调用方传入的 modifier 放在最外层，
 * 状态栏 insets 与滚动折叠偏移都在内层处理，因此在这里叠加玻璃可以覆盖
 * 整条顶栏（含状态栏区域与折叠动画），同时保留折叠行为、insets 与无障碍语义。
 *
 * 玻璃 modifier 由 rememberGlassSurfaceModifier 统一缓存，页面重组不会重新录制背景采样。
 */
@Composable
fun Modifier.glassTopBar(backdrop: Backdrop): Modifier {
    val palette = LocalGlassPalette.current
    val surface = palette.surfaceTint
    val highlightAlpha = if (palette.isDark) 0.85f else 0.55f
    val glowColor = palette.glowColor
    val highlight = Highlight.Default.copy(alpha = highlightAlpha)
    val shadow = Shadow(radius = 20.dp, color = glowColor)
    return rememberGlassSurfaceModifier(
        backdrop = backdrop,
        shape = GlassShapes.TopBarBlock(),
        blurRadius = 14.dp,
        lensHeight = 12.dp,
        lensAmount = 22.dp,
        surfaceColor = surface,
        highlight = highlight,
        shadow = shadow,
        innerShadow = null,
    )
}

/**
 * 与 glassTopBar 配套的顶栏配色：容器色透明，内容色沿用 M3 默认值。
 * 小顶栏与大顶栏共用同一份颜色定义。
 */
@Composable
fun glassTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
)

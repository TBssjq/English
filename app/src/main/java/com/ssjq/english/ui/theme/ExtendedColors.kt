package com.ssjq.english.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 自定义主题扩展：演示如何通过 CompositionLocal 扩展 M3 默认未提供的颜色或渐变画刷。
 * 适用于：品牌渐变、半透明遮罩、自定义强调色等场景，确保深色模式有对应值。
 *
 * 使用方式：
 *   val extended = LocalExtendedColors.current
 *   Box(modifier = Modifier.background(extended.heroGradient))
 */
data class ExtendedColors(
    /** Hero 区渐变画刷（亮色：浅蓝→蓝；暗色：深蓝→更深蓝） */
    val heroGradient: Brush,
    /** 统计卡片渐变 */
    val statGradient: Brush,
    /** 半透明遮罩色（用于图片上的文字背景） */
    val scrim: Color,
    /** 自定义强调色（如品牌橙，M3 未提供） */
    val accent: Color,
)

/** 亮色模式扩展值 */
val ExtendedLightColors = ExtendedColors(
    heroGradient = Brush.linearGradient(
        colors = listOf(PrimaryContainerLight, PrimaryLight.copy(alpha = 0.3f))
    ),
    statGradient = Brush.horizontalGradient(
        colors = listOf(PrimaryLight.copy(alpha = 0.15f), TertiaryLight.copy(alpha = 0.15f))
    ),
    scrim = Color(0x66000000),
    accent = Color(0xFFF59E0B), // 琥珀橙
)

/** 暗色模式扩展值 */
val ExtendedDarkColors = ExtendedColors(
    heroGradient = Brush.linearGradient(
        colors = listOf(PrimaryContainerDark, PrimaryDark.copy(alpha = 0.2f))
    ),
    statGradient = Brush.horizontalGradient(
        colors = listOf(PrimaryDark.copy(alpha = 0.25f), TertiaryDark.copy(alpha = 0.25f))
    ),
    scrim = Color(0x99000000),
    accent = Color(0xFFFBBF24), // 暗色下更亮的橙
)

/**
 * CompositionLocal：提供扩展颜色给子树。
 * 用 staticCompositionLocalOf（无订阅追踪，性能更好，适合主题这类很少变化的值）。
 */
val LocalExtendedColors = staticCompositionLocalOf { ExtendedLightColors }

/** 便捷访问扩展：像 MaterialTheme.colorScheme 一样用 ExtendedColors.current */
object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}

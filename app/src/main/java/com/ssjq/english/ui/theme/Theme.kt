package com.ssjq.english.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.ssjq.english.data.UserManager
import com.ssjq.english.ui.glass.LocalGlassPalette
import com.ssjq.english.ui.glass.currentGlassPalette
import java.util.Calendar

/**
 * Fallback 静态配色（Android < 12 或关闭动态取色时使用）
 * 采用现代蓝绿系，替代默认紫粉。
 */
private val FallbackLightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

private val FallbackDarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

/**
 * 根据当前时间判断是否使用暗色主题：
 * 06:00-18:00 使用亮色主题，18:00-次日06:00 使用暗色主题。
 */
fun isNightByTime(): Boolean {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return hour < 6 || hour >= 18
}

@Composable
fun EnglishTheme(
    darkTheme: Boolean? = null,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 响应式观察主题变化
    val userDarkTheme by UserManager.darkThemeFlow.collectAsState()
    // 三态语义：true = 强制暗色，false = 强制亮色，null = 跟随系统
    // 修复：null 分支只允许取系统的实时值（isSystemInDarkTheme 是可观察的 State，
    // 系统切换深浅色会正常驱动重组）。旧实现用 `|| isNightByTime()` 把「按时间」
    // 混进来，导致用户选「跟随系统」且系统为亮色时，18:00 后仍被强制切暗色；
    // 而 isNightByTime() 读的是 Calendar，既不会跨 18:00/06:00 主动触发重组，
    // 又会在每次重组时重新求值，出现「到点不切换、偶发整屏翻色」。
    // 若以后要提供「按时间」模式，应作为独立的第四种模式，并用 produceState
    // 定时到下一个 06:00/18:00 的可观察数据源驱动，不能塞进 null 分支。
    val effectiveDarkTheme = when (darkTheme ?: userDarkTheme) {
        true -> true
        false -> false
        null -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        effectiveDarkTheme -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }

    // 自定义扩展颜色（演示 CompositionLocal）：渐变画刷等 M3 默认未提供的值
    val extendedColors = if (effectiveDarkTheme) ExtendedDarkColors else ExtendedLightColors

    // 系统栏图标颜色适配：浅色模式图标深色，深色模式图标浅色
    // 绝不使用已废弃的 window.statusBarColor，只用 WindowInsetsControllerCompat 控制图标外观
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // 安全解包：view.context 可能是 ContextThemeWrapper 等包装，
            // 直接 `as Activity` 在边缘配置下会抛 ClassCastException
            var ctx: android.content.Context = view.context
            while (ctx !is Activity && ctx is android.content.ContextWrapper) {
                ctx = ctx.baseContext
            }
            val window = (ctx as? Activity)?.window ?: return@SideEffect
            val controller = WindowInsetsControllerCompat(window, view)
            // true = 状态栏图标为深色（适用于浅色背景）；false = 浅色图标（适用于深色背景）
            controller.isAppearanceLightStatusBars = !effectiveDarkTheme
            controller.isAppearanceLightNavigationBars = !effectiveDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        CompositionLocalProvider(
            LocalExtendedColors provides extendedColors,
            LocalGlassPalette provides currentGlassPalette(),
            content = content,
        )
    }
}

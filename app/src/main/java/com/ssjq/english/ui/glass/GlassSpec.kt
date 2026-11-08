package com.ssjq.english.ui.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/** 玻璃表面统一配色。明暗取自 MaterialTheme 的实际配色，跟随用户三态主题设置。 */
@Immutable
data class GlassPalette(
    val isDark: Boolean,
    val surfaceTint: Color,
    val listSurface: Color,
    val listBorder: Color,
    val searchSurface: Color,
    val dialogSurface: Color,
    val highlightColor: Color,
    val shadowColor: Color,
    val glowColor: Color,
    val accent: Color,
    val blobAlphas: List<Float>,
)

private val LightGlassPalette = GlassPalette(
    isDark = false,
    surfaceTint = Color.White.copy(alpha = 0.26f),
    listSurface = Color.White.copy(alpha = 0.46f),
    listBorder = Color.White.copy(alpha = 0.55f),
    searchSurface = Color.White.copy(alpha = 0.30f),
    dialogSurface = Color.White.copy(alpha = 0.62f),
    highlightColor = Color.White,
    shadowColor = Color.Black.copy(alpha = 0.18f),
    glowColor = Color.Black.copy(alpha = 0.14f),
    accent = Color(0xFF2563EB),
    blobAlphas = listOf(0.40f, 0.38f, 0.32f),
)

private val DarkGlassPalette = GlassPalette(
    isDark = true,
    surfaceTint = Color.White.copy(alpha = 0.14f),
    listSurface = Color.White.copy(alpha = 0.09f),
    listBorder = Color.White.copy(alpha = 0.20f),
    searchSurface = Color.White.copy(alpha = 0.14f),
    dialogSurface = Color.White.copy(alpha = 0.18f),
    highlightColor = Color.White.copy(alpha = 0.85f),
    shadowColor = Color.Black.copy(alpha = 0.45f),
    glowColor = Color.White.copy(alpha = 0.16f),
    accent = Color(0xFFB0C6FF),
    blobAlphas = listOf(0.40f, 0.38f, 0.32f),
)

val LocalGlassPalette = staticCompositionLocalOf { LightGlassPalette }

/** 全应用唯一的背景采样层，由 AppNav 提供。所有页面共用，避免每页各持一份全屏 GraphicsLayer。 */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> {
    error("LocalAppBackdrop 必须由 AppNav 提供")
}

@Composable
fun currentGlassPalette(): GlassPalette =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkGlassPalette else LightGlassPalette

/**
 * 设备性能档位与特效开关。
 *
 * 低内存设备关闭 AGSL 折射与饱和度增强，减少离屏缓冲与着色器副本；
 * 用户也可以在首页的设置条里整体关掉玻璃特效。
 *
 * 标志位使用 Compose 状态：`drawBackdrop` 在 observeReads 内执行 effects 块，
 * 值变化会触发对应节点重建效果链，不需要重组整棵页面树。
 */
object GlassQuality {

    @Volatile
    private var lowRam = false

    private var effectsOn by mutableStateOf(true)

    fun configure(isLowRamDevice: Boolean) {
        lowRam = isLowRamDevice
    }

    fun setEffectsEnabled(enabled: Boolean) {
        effectsOn = enabled
    }

    val lensEnabled: Boolean get() = effectsOn && !lowRam

    val vibrancyEnabled: Boolean get() = effectsOn && !lowRam

    val blurRadius: Dp get() = if (lowRam) 4.dp else 8.dp

    /** 底栏是细长条，模糊半径减半即可，避免大面积模糊拖慢切换与拖动 */
    val bottomBarBlurRadius: Dp get() = if (lowRam) 2.dp else 4.dp

    /** 关闭特效后只保留较弱模糊，玻璃上的文字仍然可读 */
    val blurRadiusPxFactor: Float get() = if (effectsOn && !lowRam) 1f else 0.5f
}

/** 形状常量。稳定实例让 ShapeProvider 的 Outline 缓存与 DrawBackdropElement 相等性判定生效。 */
object GlassShapes {

    private val tagShape = RoundedCornerShape(8.dp)
    private val itemShape = RoundedCornerShape(16.dp)
    private val cardShape = RoundedCornerShape(24.dp)
    private val fieldShape = RoundedCornerShape(28.dp)
    private val panelShape = RoundedCornerShape(28.dp)

    val Tag: Shape get() = tagShape
    val Item: Shape get() = itemShape
    val Card: Shape get() = cardShape
    val Field: Shape get() = fieldShape
    val Panel: Shape get() = panelShape

    val CardBlock: () -> Shape = { cardShape }
    val ItemBlock: () -> Shape = { itemShape }
    val FieldBlock: () -> Shape = { fieldShape }
    val PanelBlock: () -> Shape = { panelShape }
    val PillBlock: () -> Shape = { Capsule }
    val CircleBlock: () -> Shape = { CircleShape }

    private val topBarShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    private val bottomBarShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    val BottomBar: Shape get() = bottomBarShape

    val TopBarBlock: () -> Shape = { topBarShape }
}

/** 背景光斑统一参数，避免全局层与各页面层采样底色不一致。 */
object GlassBackdropSpec {

    val blobSizes = listOf(240.dp, 180.dp, 150.dp)

    @Composable
    fun blobAlphas(): List<Float> = currentGlassPalette().blobAlphas
}

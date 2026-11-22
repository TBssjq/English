package com.ssjq.english.ui.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

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
    surfaceTint = Color.White.copy(alpha = 0.18f),
    listSurface = Color.White.copy(alpha = 0.52f),
    listBorder = Color.White.copy(alpha = 0.65f),
    searchSurface = Color.White.copy(alpha = 0.24f),
    dialogSurface = Color.White.copy(alpha = 0.56f),
    highlightColor = Color.White,
    shadowColor = Color.Black.copy(alpha = 0.24f),
    glowColor = Color.Black.copy(alpha = 0.22f),
    accent = Color(0xFF2563EB),
    blobAlphas = listOf(0.62f, 0.56f, 0.50f, 0.42f),
)

private val DarkGlassPalette = GlassPalette(
    isDark = true,
    surfaceTint = Color.White.copy(alpha = 0.10f),
    listSurface = Color.White.copy(alpha = 0.12f),
    listBorder = Color.White.copy(alpha = 0.28f),
    searchSurface = Color.White.copy(alpha = 0.12f),
    dialogSurface = Color.White.copy(alpha = 0.16f),
    highlightColor = Color.White.copy(alpha = 0.95f),
    shadowColor = Color.Black.copy(alpha = 0.52f),
    glowColor = Color.White.copy(alpha = 0.26f),
    accent = Color(0xFFB0C6FF),
    blobAlphas = listOf(0.62f, 0.56f, 0.50f, 0.42f),
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

    private var lowRam by mutableStateOf(false)

    private var effectsOn by mutableStateOf(true)

    fun configure(isLowRamDevice: Boolean) {
        lowRam = isLowRamDevice
    }

    fun setEffectsEnabled(enabled: Boolean) {
        effectsOn = enabled
    }

    val lensEnabled: Boolean get() = effectsOn && !lowRam

    val vibrancyEnabled: Boolean get() = effectsOn && !lowRam

    val chromaticAberrationEnabled: Boolean get() = effectsOn && !lowRam

    /** 关闭特效或低端设备时移除高光、外阴影、内阴影这三类额外 GraphicsLayer */
    val decorationsEnabled: Boolean get() = effectsOn && !lowRam

    val blurRadius: Dp get() = 10.dp

    /**
     * 按当前表现调整模糊强度：quality > 1 更柔，< 1 更清晰。
     * 同时尊重特效总开关与低端设备档位，避免表现降级时被特效拖慢。
     */
    fun adaptiveBlur(base: Dp, quality: Float = 1f): Dp =
        base * blurRadiusPxFactor * quality.coerceIn(0.5f, 1.3f)

    /** 底栏是细长条，模糊半径减半即可，避免大面积模糊拖慢切换与拖动 */
    val bottomBarBlurRadius: Dp get() = 7.dp

    /** 关闭特效后只保留较弱模糊，玻璃上的文字仍然可读 */
    val blurRadiusPxFactor: Float get() = if (effectsOn && !lowRam) 1f else 0.5f
}

/** 折射高度必须落在圆角半径内，否则官方着色器会在边角产生跳变。 */
private fun BackdropEffectScope.effectiveLensHeight(base: Dp): Float {
    val maxRadius = when (val current = shape) {
        is CornerBasedShape -> minOf(
            current.topStart.toPx(size, this),
            current.topEnd.toPx(size, this),
            current.bottomEnd.toPx(size, this),
            current.bottomStart.toPx(size, this),
        ).coerceAtMost(size.minDimension / 2f)
        else -> size.minDimension / 4f
    }
    return minOf((base * 1.35f).toPx(), maxRadius)
}

/**
 * 构造稳定的玻璃 modifier。backdrop 库按 lambda 引用判断节点是否变化，
 * 每次重组新建 effects / highlight / shadow 都会重新录制背景采样。
 * 这里把整条 modifier 链缓存起来，并在低端档位移除装饰层、色散与深度折射。
 */
@Composable
fun rememberGlassSurfaceModifier(
    backdrop: Backdrop,
    shape: Shape,
    blurRadius: Dp,
    lensHeight: Dp,
    lensAmount: Dp,
    surfaceColor: Color,
    overlayColor: Color = Color.Unspecified,
    highlight: Highlight?,
    shadow: Shadow?,
    innerShadow: InnerShadow?,
    enableLens: Boolean = true,
    enableVibrancy: Boolean = true,
    enableChromaticAberration: Boolean = true,
): Modifier {
    val decorations = GlassQuality.decorationsEnabled
    val palette = currentGlassPalette()
    return remember(
        backdrop, shape, blurRadius, lensHeight, lensAmount, surfaceColor, overlayColor,
        highlight, shadow, innerShadow, enableLens, enableVibrancy,
        enableChromaticAberration, decorations, palette,
    ) {
        val shapeBlock: () -> Shape = { shape }
        val highlightBlock: (() -> Highlight?)? =
            if (decorations && highlight != null) { { highlight } } else null
        val shadowBlock: (() -> Shadow?)? =
            if (decorations && shadow != null) { { shadow } } else null
        val innerShadowBlock: (() -> InnerShadow?)? =
            if (decorations && innerShadow != null) { { innerShadow } } else null
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = shapeBlock,
            effects = {
                if (enableVibrancy && GlassQuality.vibrancyEnabled) {
                    colorControls(brightness = 0.01f, contrast = 1.08f, saturation = 1.9f)
                }
                blur(blurRadius.toPx() * GlassQuality.blurRadiusPxFactor * 1.2f)
                if (enableLens && GlassQuality.lensEnabled) {
                    lens(
                        refractionHeight = effectiveLensHeight(lensHeight),
                        refractionAmount = minOf(
                            (lensAmount * 1.65f).toPx(),
                            size.minDimension * 1.2f,
                        ),
                        depthEffect = true,
                        chromaticAberration =
                            enableChromaticAberration && GlassQuality.chromaticAberrationEnabled,
                    )
                }
            },
            highlight = highlightBlock,
            shadow = shadowBlock,
            innerShadow = innerShadowBlock,
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
                if (
                    enableLens &&
                    GlassQuality.lensEnabled &&
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                ) {
                    val band = minOf((lensHeight * 1.8f).toPx(), size.minDimension / 3f)
                    val shift = minOf((lensAmount * 0.42f).toPx(), 20f)
                    clipRect(left = 0f, top = 0f, right = band, bottom = size.height) {
                        translate(shift, 0f) { drawBackdrop() }
                    }
                    clipRect(
                        left = size.width - band,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                    ) {
                        translate(-shift, 0f) { drawBackdrop() }
                    }
                    clipRect(left = 0f, top = 0f, right = size.width, bottom = band) {
                        translate(0f, shift) { drawBackdrop() }
                    }
                    clipRect(
                        left = 0f,
                        top = size.height - band,
                        right = size.width,
                        bottom = size.height,
                    ) {
                        translate(0f, -shift) { drawBackdrop() }
                    }
                }
            },
            onDrawSurface = {
                if (overlayColor.isSpecified) drawRect(overlayColor)
                drawRect(surfaceColor)
                // 与系统版本无关的斜向反光，让低版本设备也能看见玻璃边缘和厚度
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.highlightColor.copy(alpha = if (palette.isDark) 0.24f else 0.42f),
                            Color.Transparent,
                            palette.shadowColor.copy(alpha = if (palette.isDark) 0.28f else 0.16f),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    )
                )
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = palette.listBorder.copy(alpha = if (palette.isDark) 0.32f else 0.55f),
                    style = Stroke(width = (if (palette.isDark) 1.4.dp else 1.1.dp).toPx()),
                )
            },
        )
    }
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

    val blobSizes = listOf(280.dp, 220.dp, 190.dp, 160.dp)

    @Composable
    fun blobAlphas(): List<Float> = currentGlassPalette().blobAlphas
}

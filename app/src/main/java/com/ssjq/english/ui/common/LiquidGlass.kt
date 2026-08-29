package com.ssjq.english.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssjq.english.ui.nav.MainTab
import kotlin.math.roundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * 玻璃表面默认色。
 *
 * 深色模式下背景本身很暗，玻璃需要略高的白色底 + 更强的边缘高光才能看出"玻璃感"，
 * 因此深色取 0.14、浅色取 0.22（比常规毛玻璃更透亮，突出折射效果）。
 */
@Composable
internal fun defaultGlassSurfaceColor(): Color =
    if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.22f)

/**
 * 边缘高光。深色模式下加宽 + 提亮，让玻璃在暗背景中勾出清晰轮廓；
 * 浅色模式下保持细腻，避免边缘发白糊边。
 *
 * @param alpha 高光强度，深色模式下会被放大
 */
@Composable
internal fun glassHighlight(alpha: Float = 1f): Highlight {
    return if (isSystemInDarkTheme()) {
        Highlight(
            width = 1.6.dp,
            blurRadius = 0.9.dp,
            alpha = (alpha * 1.35f).coerceAtMost(1f),
            style = HighlightStyle.Ambient,
        )
    } else {
        Highlight(width = 1.1.dp, blurRadius = 0.55.dp, alpha = alpha)
    }
}

/**
 * 深色模式下玻璃外发光：用高亮色的外阴影模拟"透光"，
 * 让玻璃在夜间看起来是发光的，而不是一块死黑半透明板。
 */
@Composable
internal fun glassGlow(radius: Dp = 24.dp): Shadow? {
    return if (isSystemInDarkTheme()) {
        Shadow(
            radius = radius,
            offset = DpOffset(0.dp, 0.dp),
            color = Color.White.copy(alpha = 0.16f),
        )
    } else {
        Shadow(radius = radius, color = Color.Black.copy(alpha = 0.18f))
    }
}

/**
 * 构造阴影。
 *
 * 注意：Shadow.alpha 是叠加在 color 之上的图层透明度，
 * 直接用 alpha 会与 color 自带的 alpha 相乘导致阴影几乎不可见，
 * 因此统一通过 color 控制浓度，alpha 恒为 1。
 */
internal fun glassShadow(radius: Dp, alpha: Float): Shadow =
    Shadow(radius = radius, color = Color.Black.copy(alpha = alpha))

internal fun glassInnerShadow(radius: Dp, alpha: Float): InnerShadow =
    InnerShadow(radius = radius, color = Color.Black.copy(alpha = alpha))

/**
 * 液态玻璃卡片：毛玻璃模糊 + 折射透镜 + 高光边缘
 *
 * 性能提示：shadow / highlight / innerShadow 每一项都会额外创建一个 GraphicsLayer，
 * 在 LazyColumn 等列表场景中建议传 null 或改用 [LiquidGlassListItem]。
 *
 * @param backdrop 背景采样源（通过 rememberLayerBackdrop 创建）
 * @param modifier 修饰符
 * @param shape 卡片形状
 * @param blurRadius 模糊半径
 * @param lensHeight 折射高度（越大折射越明显）
 * @param lensAmount 折射强度
 * @param surfaceColor 表面覆盖颜色，控制玻璃色调和不透明度
 * @param shadow 外部阴影（null 表示无阴影，可省一个 GraphicsLayer）
 * @param highlight 边缘高光（null 表示无高光）
 * @param innerShadow 内部阴影（null 表示无内阴影）
 * @param enableLens 是否启用折射（AGSL 着色器，列表项建议关闭）
 * @param enableVibrancy 是否提高饱和度
 * @param onClick 点击事件（null 时不可点击）
 * @param content 内容
 */
@Composable
fun LiquidGlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    blurRadius: Dp = 10.dp,
    lensHeight: Dp = 20.dp,
    lensAmount: Dp = 34.dp,
    surfaceColor: Color = defaultGlassSurfaceColor(),
    shadow: Shadow? = glassGlow(24.dp),
    highlight: Highlight? = glassHighlight(),
    innerShadow: InnerShadow? = null,
    enableLens: Boolean = true,
    enableVibrancy: Boolean = true,
    enableChromaticAberration: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onClick
                    )
                } else Modifier
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (enableVibrancy) colorControls(saturation = 1.8f)
                    blur(blurRadius.toPx())
                    if (enableLens) {
                        lens(
                            refractionHeight = lensHeight.toPx(),
                            refractionAmount = lensAmount.toPx(),
                            depthEffect = true,
                            chromaticAberration = enableChromaticAberration,
                        )
                    }
                },
                highlight = if (highlight != null) { { highlight } } else null,
                shadow = if (shadow != null) { { shadow } } else null,
                innerShadow = if (innerShadow != null) { { innerShadow } } else null,
                onDrawSurface = {
                    drawRect(surfaceColor)
                },
            ),
        content = content,
    )
}

/**
 * 列表项玻璃（性能优先）。
 *
 * 重要：这里**不使用 drawBackdrop**。
 * 列表同屏可能有几十项，若每项都走 backdrop 采样，会产生几十个 GraphicsLayer
 * 并不断读写 Compose 状态，实测会把主线程压垮（Activity 无法及时 resume，被系统判定退出）。
 * 因此列表项改用「半透明磨砂 + 高光描边」的等效视觉，开销与普通卡片接近。
 *
 * 真正的液态玻璃（带折射采样）请用在数量可控的元素上：底栏、主卡片、按钮、对话框。
 */
@Composable
fun LiquidGlassListItem(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else Modifier
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                color = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.46f)
            )
            .border(
                width = 1.dp,
                color = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
            ),
        content = content,
    )
}

/**
 * 液态玻璃搜索框容器
 */
@Composable
fun LiquidGlassSearchBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    surfaceColor: Color = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    },
    content: @Composable BoxScope.() -> Unit,
) {
    val highlight = glassHighlight(0.7f)
    val glow = glassGlow(18.dp)
    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    colorControls(saturation = 1.8f)
                    blur(11.dp.toPx())
                    lens(
                        refractionHeight = 14.dp.toPx(),
                        refractionAmount = 24.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = { highlight },
                shadow = { glow },
                onDrawSurface = {
                    drawRect(surfaceColor)
                },
            ),
        content = content,
    )
}

/**
 * 液态玻璃圆形徽章
 */
@Composable
fun LiquidGlassBadge(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    surfaceColor: Color = defaultGlassSurfaceColor(),
    content: @Composable BoxScope.() -> Unit,
) {
    val highlight = glassHighlight()
    val glow = glassGlow(12.dp)
    Box(
        modifier = modifier
            .size(size)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    colorControls(saturation = 1.8f)
                    blur(5.dp.toPx())
                    lens(
                        refractionHeight = 8.dp.toPx(),
                        refractionAmount = 18.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = { highlight },
                shadow = { glow },
                onDrawSurface = {
                    drawRect(surfaceColor)
                },
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * 液态玻璃对话框表面
 */
@Composable
fun LiquidGlassDialogSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    surfaceColor: Color = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    val highlight = glassHighlight()
    val glow = glassGlow(40.dp)
    Column(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    colorControls(saturation = 1.9f)
                    blur(16.dp.toPx())
                    lens(
                        refractionHeight = 22.dp.toPx(),
                        refractionAmount = 36.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = { highlight },
                shadow = { glow },
                innerShadow = { glassInnerShadow(14.dp, 0.16f) },
                onDrawSurface = {
                    drawRect(surfaceColor)
                },
            )
            .padding(24.dp),
        content = content,
    )
}

/**
 * 玻璃对话框：透明背景 + 玻璃表面，用于替换默认的纯色 AlertDialog。
 *
 * @param backdrop 背景采样源（应为对话框下层页面的 backdrop，保证能采样到背后内容）
 */
@Composable
fun LiquidGlassDialog(
    backdrop: Backdrop,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LiquidGlassDialogSurface(
            backdrop = backdrop,
            modifier = modifier.fillMaxWidth(0.92f),
            content = content,
        )
    }
}

/**
 * 玻璃顶部栏：替代 Material3 TopAppBar 的纯色容器，
 * 背景随内容滚动呈现毛玻璃质感。
 */
@Composable
fun LiquidGlassTopAppBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = defaultGlassSurfaceColor()
    val highlight = glassHighlight(0.6f)
    val glow = glassGlow(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(height)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp) },
                effects = {
                    colorControls(saturation = 1.8f)
                    blur(14.dp.toPx())
                    lens(
                        refractionHeight = 12.dp.toPx(),
                        refractionAmount = 22.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = { highlight },
                shadow = { glow },
                onDrawSurface = {
                    drawRect(surface)
                },
            ),
        content = content,
    )
}

/**
 * 玻璃底部操作栏：适合详情页底部按钮区。
 */
@Composable
fun LiquidGlassBottomBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = defaultGlassSurfaceColor()
    val highlight = glassHighlight(0.6f)
    val glow = glassGlow(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) },
                effects = {
                    colorControls(saturation = 1.8f)
                    blur(15.dp.toPx())
                    lens(
                        refractionHeight = 14.dp.toPx(),
                        refractionAmount = 24.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = { highlight },
                shadow = { glow },
                onDrawSurface = {
                    drawRect(surface)
                },
            ),
        content = content,
    )
}

/**
 * 液态玻璃 Toast 提示
 */
@Composable
fun LiquidGlassToast(
    backdrop: Backdrop,
    message: String,
    modifier: Modifier = Modifier,
    surfaceColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    icon: @Composable (() -> Unit)? = null,
) {
    val highlight = glassHighlight()
    val glow = glassGlow(20.dp)
    Row(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
                effects = {
                    colorControls(saturation = 1.7f)
                    blur(9.dp.toPx())
                    lens(
                        refractionHeight = 12.dp.toPx(),
                        refractionAmount = 22.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                },
                highlight = { highlight },
                shadow = { glow },
                onDrawSurface = {
                    drawRect(surfaceColor)
                },
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            it()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.STUDY -> Icons.Filled.MenuBook
    MainTab.ACHIEVEMENT -> Icons.Filled.EmojiEvents
    MainTab.ABOUT -> Icons.Filled.Person
}

private fun MainTab.label(): String = when (this) {
    MainTab.STUDY -> "学习"
    MainTab.ACHIEVEMENT -> "成就"
    MainTab.ABOUT -> "关于作者"
}

/**
 * 液态玻璃底部导航栏（对齐库官方 LiquidBottomTabs 的形态）：
 *
 * 1. 整条底栏是一块胶囊玻璃（毛玻璃 + 折射 + 色散）；
 * 2. 选中项由一块**独立滑动**的玻璃指示器表示，切换时带弹性动画，
 *    而不是在每个 item 内部各嵌一层玻璃——这样整条底栏始终只有 2 个 GraphicsLayer，
 *    避免几十个列表项式玻璃组件把主线程压垮。
 */
@Composable
fun LiquidGlassBottomNavigation(
    backdrop: Backdrop,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = MainTab.entries
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    // 指示器滑动动画
    val indicatorAnim = remember { Animatable(selectedIndex.toFloat()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedIndex) {
        indicatorAnim.animateTo(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        )
    }

    val barHighlight = glassHighlight(0.5f)
    val barGlow = glassGlow(28.dp)
    val barSurface = defaultGlassSurfaceColor()
    // glassHighlight / glassGlow 是 @Composable，需在 Composable 作用域内先求值
    val indicatorHighlight = glassHighlight(0.9f)
    val indicatorGlow = glassGlow(16.dp)
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidthPx = with(density) { (maxWidth / tabs.size).toPx() }

        // 底栏玻璃容器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    // 在底栏上左右滑动即可切换 tab：指示器实时跟随手指，
                    // 松手时吸附到最近 tab（点击仍能精确选中）。
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaTabs = dragAmount / tabWidthPx
                            val maxIndex = (tabs.size - 1).toFloat()
                            val newVal =
                                (indicatorAnim.value - deltaTabs).coerceIn(0f, maxIndex)
                            // Animatable.value 的 setter 是 private，拖动中用 snapTo 跟随手指
                            scope.launch { indicatorAnim.snapTo(newVal) }
                        },
                        onDragEnd = {
                            val target = indicatorAnim.value
                                .roundToInt()
                                .coerceIn(0, tabs.size - 1)
                            onTabSelected(tabs[target])
                            // 松手吸附：即使只是轻微拖动（未跨到新 tab），也平滑回弹到目标
                            scope.launch {
                                indicatorAnim.animateTo(
                                    target.toFloat(),
                                    spring(dampingRatio = 0.72f, stiffness = 420f),
                                )
                            }
                        },
                    )
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(32.dp) },
                    effects = {
                        colorControls(saturation = 1.9f)
                        blur(16.dp.toPx())
                        lens(
                            refractionHeight = 20.dp.toPx(),
                            refractionAmount = 34.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { barHighlight },
                    shadow = { barGlow },
                    onDrawSurface = { drawRect(barSurface) },
                )
                .height(64.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                GlassTabLabel(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 跟随选中项滑动的玻璃指示器
        Box(
            modifier = Modifier
                .offset { IntOffset((indicatorAnim.value * tabWidthPx).roundToInt(), 0) }
                .width(maxWidth / tabs.size)
                .height(56.dp)
                .padding(horizontal = 4.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(percent = 50) },
                    effects = {
                        colorControls(saturation = 2.1f)
                        blur(10.dp.toPx())
                        lens(
                            refractionHeight = 18.dp.toPx(),
                            refractionAmount = 30.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { indicatorHighlight },
                    shadow = { indicatorGlow },
                    onDrawSurface = { drawRect(accent.copy(alpha = 0.32f)) },
                )
        )
    }
}

/**
 * 底栏文字标签：不含玻璃层，玻璃效果由整条底栏与滑动指示器承担。
 */
@Composable
private fun GlassTabLabel(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tab.icon(),
            contentDescription = tab.label(),
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}

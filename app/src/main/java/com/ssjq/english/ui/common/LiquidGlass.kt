package com.ssjq.english.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ssjq.english.ui.glass.GlassShapes
import com.ssjq.english.ui.glass.LocalGlassPalette
import com.ssjq.english.ui.glass.rememberGlassSurfaceModifier
import com.ssjq.english.ui.nav.MainTab
import kotlin.math.roundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** 玻璃表面默认色，明暗取自用户当前主题的实际配色。 */
@Composable
internal fun defaultGlassSurfaceColor(): Color = LocalGlassPalette.current.surfaceTint

/** 边缘高光：深色模式下加宽提亮，在暗背景中勾出清晰轮廓。 */
@Composable
internal fun glassHighlight(alpha: Float = 1f): Highlight {
    return if (LocalGlassPalette.current.isDark) {
        Highlight(
            width = 2.2.dp,
            blurRadius = 1.2.dp,
            alpha = (alpha * 1.5f).coerceAtMost(1f),
            style = HighlightStyle.Ambient,
        )
    } else {
        Highlight(
            width = 1.6.dp,
            blurRadius = 0.8.dp,
            alpha = (alpha * 1.2f).coerceAtMost(1f),
            style = HighlightStyle.Ambient,
        )
    }
}

/** 外阴影：浅色模式用黑色，深色模式用白色形成透光感。 */
@Composable
internal fun glassGlow(radius: Dp = 24.dp): Shadow? =
    Shadow(
        radius = radius,
        offset = DpOffset(0.dp, 0.dp),
        color = LocalGlassPalette.current.glowColor,
    )

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
    shape: Shape = GlassShapes.Card,
    blurRadius: Dp = 14.dp,
    lensHeight: Dp = 22.dp,
    lensAmount: Dp = 42.dp,
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
    val glass = rememberGlassSurfaceModifier(
        backdrop = backdrop,
        shape = shape,
        blurRadius = blurRadius,
        lensHeight = lensHeight,
        lensAmount = lensAmount,
        surfaceColor = surfaceColor,
        highlight = highlight,
        shadow = shadow,
        innerShadow = innerShadow,
        enableLens = enableLens,
        enableVibrancy = enableVibrancy,
        enableChromaticAberration = enableChromaticAberration,
    )
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
            .then(glass),
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
 *
 * @param selected 是否为列表中的当前选中项，选中时以强调色铺底并加粗描边
 */
@Composable
fun LiquidGlassListItem(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalGlassPalette.current
    // 选中态用强调色铺底并加粗描边，与未选中项拉开明度与色相差异，
    // 保证在毛玻璃列表里也能一眼分辨当前选中项。
    val surface = if (selected) {
        palette.accent.copy(alpha = if (palette.isDark) 0.30f else 0.18f)
    } else {
        palette.listSurface
    }
    val borderWidth = if (selected) 2.dp else 1.dp
    val borderColor = if (selected) palette.accent else palette.listBorder
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = if (selected) Role.RadioButton else Role.Button,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .clip(GlassShapes.Item)
            .background(color = surface)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = GlassShapes.Item,
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
    surfaceColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalGlassPalette.current
    val resolvedSurfaceColor = surfaceColor ?: palette.searchSurface
    val highlight = glassHighlight(0.7f)
    val glow = glassGlow(18.dp)
    val glass = rememberGlassSurfaceModifier(
        backdrop = backdrop,
        shape = GlassShapes.FieldBlock(),
        blurRadius = 11.dp,
        lensHeight = 14.dp,
        lensAmount = 24.dp,
        surfaceColor = resolvedSurfaceColor,
        highlight = highlight,
        shadow = glow,
        innerShadow = null,
    )
    Box(modifier = modifier.then(glass), content = content)
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
    val glass = rememberGlassSurfaceModifier(
        backdrop = backdrop,
        shape = GlassShapes.CircleBlock(),
        blurRadius = 7.dp,
        lensHeight = 10.dp,
        lensAmount = 24.dp,
        surfaceColor = surfaceColor,
        highlight = highlight,
        shadow = glow,
        innerShadow = null,
    )
    Box(
        modifier = modifier.size(size).then(glass),
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
    surfaceColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalGlassPalette.current
    val resolvedSurfaceColor = surfaceColor ?: palette.dialogSurface
    val highlight = glassHighlight()
    val glow = glassGlow(40.dp)
    val glass = rememberGlassSurfaceModifier(
        backdrop = backdrop,
        shape = GlassShapes.PanelBlock(),
        blurRadius = 20.dp,
        lensHeight = 24.dp,
        lensAmount = 48.dp,
        surfaceColor = resolvedSurfaceColor,
        highlight = highlight,
        shadow = glow,
        innerShadow = glassInnerShadow(14.dp, 0.16f),
    )
    Column(
        modifier = modifier.then(glass).padding(24.dp),
        content = content,
    )
}

/**
 * 玻璃弹窗正文：文本过长时在固定高度内滚动。
 *
 * 弹窗是居中定高的玻璃面板，正文直接堆在 Column 里时，
 * 更新说明或公告条目一多就会把底部的操作按钮顶出屏幕。
 */
@Composable
fun GlassDialogBody(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 玻璃弹窗可见性（应用级）。
 *
 * 玻璃对话框是「同窗口覆盖层」而不是系统 Dialog，只能覆盖它所在的容器，
 * 悬浮底栏等后绘制的元素会盖在遮罩之上。宿主据此在弹窗可见时隐藏底栏。
 */
object GlassDialogHost {
    private var activeToken by mutableStateOf<Any?>(null)

    val isDialogVisible: Boolean get() = activeToken != null

    /** 同一时刻只允许一个玻璃弹窗显示，后到的弹窗排队等待 */
    fun request(token: Any): Boolean {
        if (activeToken == null) activeToken = token
        return activeToken == token
    }

    fun release(token: Any) {
        if (activeToken == token) activeToken = null
    }

    fun isActive(token: Any): Boolean = activeToken == token

    /** 页面重建时清掉上一轮残留的宿主令牌，避免底栏一直被隐藏 */
    fun reset() {
        activeToken = null
    }
}

/**
 * 玻璃对话框。
 *
 * 关键实现约束：**不使用 `androidx.compose.ui.window.Dialog`。**
 * Dialog 会另起一个 Window（独立 ComposeView / GraphicsContext），
 * 而 backdrop 的 GraphicsLayer 是录制在 Activity 窗口里的，
 * 跨窗口绘制既拿不到正确的采样结果，也属于未定义行为（历史上直接导致
 * 首启拉取公告弹出对话框时闪退）。
 *
 * 因此这里采用与液态玻璃库官方示例 `DialogContent` 一致的方案：
 * 在当前窗口内叠一层半透明遮罩 + 居中玻璃面板，采样与绘制全部在同一窗口完成。
 *
 * @param backdrop 背景采样源（应为对话框下层页面的 backdrop，保证能采样到背后内容）
 */
@Composable
fun LiquidGlassDialog(
    backdrop: Backdrop,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val token = remember { Any() }
    val dialogVisible = GlassDialogHost.isDialogVisible
    val active = GlassDialogHost.isActive(token)
    // 先注册释放，再申请令牌：申请后若父级立刻移除弹窗，令牌仍会被清掉
    DisposableEffect(token) {
        onDispose { GlassDialogHost.release(token) }
    }
    LaunchedEffect(dialogVisible) { GlassDialogHost.request(token) }
    if (!active) return

    BackHandler(onBack = onDismissRequest)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "glassDialogScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "glassDialogAlpha",
    )

    val dimColor = if (LocalGlassPalette.current.isDark) {
        Color.Black.copy(alpha = 0.48f)
    } else {
        Color(0xFF1A1A2E).copy(alpha = 0.26f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dimColor)
            .pointerInput(Unit) {
                detectDragGestures(onDrag = { change, _ -> change.consume() })
            }
            .then(
                if (dismissOnClickOutside) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onDismissRequest,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clickable(
                    enabled = true,
                    interactionSource = null,
                    indication = null,
                    onClick = {},
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
        ) {
            LiquidGlassDialogSurface(
                backdrop = backdrop,
                modifier = modifier.fillMaxWidth(0.92f),
                content = content,
            )
        }
    }
}

/**
 * 玻璃底部操作栏：适合详情页底部按钮区。
 *
 * @param shape 默认贴合屏幕底边（只有上边圆角）；悬浮在页面内部时传 [GlassShapes.Panel]
 */
@Composable
fun LiquidGlassBottomBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.BottomBar,
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = defaultGlassSurfaceColor()
    val highlight = glassHighlight(0.6f)
    val glow = glassGlow(24.dp)
    val glass = rememberGlassSurfaceModifier(
        backdrop = backdrop,
        shape = shape,
        blurRadius = 12.dp,
        lensHeight = 14.dp,
        lensAmount = 24.dp,
        surfaceColor = surface,
        highlight = highlight,
        shadow = glow,
        innerShadow = null,
        enableChromaticAberration = false,
    )
    Box(
        modifier = modifier.fillMaxWidth().then(glass),
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
    val glass = rememberGlassSurfaceModifier(
        backdrop = backdrop,
        shape = GlassShapes.CardBlock(),
        blurRadius = 8.dp,
        lensHeight = 12.dp,
        lensAmount = 22.dp,
        surfaceColor = surfaceColor,
        highlight = highlight,
        shadow = glow,
        innerShadow = null,
        enableChromaticAberration = false,
        enableVibrancy = false,
    )
    Row(
        modifier = modifier
            .then(glass)
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
    MainTab.STUDY -> Icons.AutoMirrored.Filled.MenuBook
    MainTab.ACHIEVEMENT -> Icons.Filled.EmojiEvents
    MainTab.ABOUT -> Icons.Filled.Person
}

private fun MainTab.label(): String = when (this) {
    MainTab.STUDY -> "学习"
    MainTab.ACHIEVEMENT -> "成就"
    MainTab.ABOUT -> "关于作者"
}

/**
 * 液态玻璃底部导航栏（形态与库官方 LiquidBottomTabs 保持一致）：
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
    val barGlass = rememberGlassSurfaceModifier(
        backdrop = backdrop,
        shape = GlassShapes.PanelBlock(),
        blurRadius = 12.dp,
        lensHeight = 16.dp,
        lensAmount = 28.dp,
        surfaceColor = barSurface,
        highlight = barHighlight,
        shadow = barGlow,
        innerShadow = null,
        enableChromaticAberration = false,
    )
    val indicatorGlass = rememberGlassSurfaceModifier(
        backdrop = backdrop,
        shape = GlassShapes.PillBlock(),
        blurRadius = 8.dp,
        lensHeight = 14.dp,
        lensAmount = 24.dp,
        surfaceColor = accent.copy(alpha = 0.32f),
        highlight = indicatorHighlight,
        shadow = indicatorGlow,
        innerShadow = null,
        enableChromaticAberration = false,
    )

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
                .then(barGlass)
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
                .then(indicatorGlass)
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
            .clip(GlassShapes.Card)
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

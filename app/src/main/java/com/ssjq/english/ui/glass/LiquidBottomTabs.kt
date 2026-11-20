package com.ssjq.english.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalGlassPalette.current
    val containerColor =
        if (palette.isDark) Color(0xFF121212).copy(0.4f)
        else Color(0xFFFAFAFA).copy(0.4f)

    val density = LocalDensity.current
    // 指示器左右各留 4dp，拖动换算与它保持一致
    val sideInset = with(density) { 8f.dp.toPx() }

    val animationScope = rememberCoroutineScope()
    val offsetAnimation = remember { Animatable(0f) }
    // 拖动位移收敛到常驻协程，避免每个触摸移动事件创建协程
    val offsetChannel = remember { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(offsetAnimation) {
        offsetChannel.consumeAsFlow().collectLatest { target ->
            offsetAnimation.snapTo(target)
        }
    }

    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    var currentIndex by remember(selectedTabIndex) {
        mutableIntStateOf(selectedTabIndex())
    }

    // 手势挂在整条底栏上：标签文字要绘制在指示器之上，若把手势留在指示器上，
    // 上层文字会挡住拖动。父层先处理 Main pass，标签自身的点击仍能正常触发。
    val dampedDragAnimation = remember(animationScope, isLtr, tabsCount) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedTabIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                // 松手吸附始终需要一段可见过渡；拖回原页签时下标不变，也要回弹
                animateToTab(targetIndex.toFloat())
                if (currentIndex != targetIndex) currentIndex = targetIndex
                animationScope.launch {
                    offsetAnimation.animateTo(
                        0f,
                        spring(1f, 300f, 0.5f)
                    )
                }
            },
            onDrag = { size, dragAmount ->
                val tab = ((size.width.toFloat() - sideInset) / tabsCount).coerceAtLeast(1f)
                updateValue(
                    (targetValue + dragAmount.x / tab * if (isLtr) 1f else -1f)
                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                )
                offsetChannel.trySend(offsetAnimation.value + dragAmount.x)
            }
        )
    }

    LaunchedEffect(selectedTabIndex) {
        snapshotFlow { selectedTabIndex() }
            .collectLatest { index ->
                if (index != currentIndex) {
                    // 点击标签：页面切换与指示器动画在同一帧启动
                    dampedDragAnimation.animateToTab(index.toFloat())
                    currentIndex = index
                }
            }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }
            .drop(1)
            .collectLatest { index -> onTabSelected(index) }
    }

    BoxWithConstraints(
        modifier.then(dampedDragAnimation.modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        val tabWidth = (constraints.maxWidth.toFloat() - sideInset) / tabsCount
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val decorations = GlassQuality.decorationsEnabled
        val barModifier = remember(backdrop, dampedDragAnimation, containerColor, decorations) {
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = GlassShapes.PillBlock,
                    effects = {
                        blur(GlassQuality.adaptiveBlur(GlassQuality.bottomBarBlurRadius).toPx())
                    },
                    highlight = null,
                    shadow = null,
                    innerShadow = null,
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp)
        }

        // 1) 整条胶囊玻璃：底栏唯一一次模糊
        Row(barModifier, content = {})

        val indicatorModifier = remember(
            backdrop, dampedDragAnimation, isLtr, tabWidth, tabsCount,
            palette.isDark, decorations,
        ) {
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = GlassShapes.PillBlock,
                    effects = {
                        blur(GlassQuality.adaptiveBlur(2f.dp).toPx())
                    },
                    highlight = if (decorations) {
                        {
                            Highlight.Default.copy(
                                alpha = 0.16f + 0.24f * dampedDragAnimation.pressProgress
                            )
                        }
                    } else null,
                    shadow = if (decorations) { { Shadow(alpha = 0.06f) } } else null,
                    innerShadow = if (decorations) {
                        { InnerShadow(radius = 6f.dp, alpha = 0.10f) }
                    } else null,
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (palette.isDark) Color.White.copy(0.1f)
                            else Color.Black.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount)
        }

        // 2) 滑块指示器：直接采样应用背景，只做轻模糊
        Box(indicatorModifier)

        // 3) 标签文字画在最上层，否则会被指示器整块盖住
        Row(
            Modifier
                .fillMaxWidth()
                .height(64f.dp)
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

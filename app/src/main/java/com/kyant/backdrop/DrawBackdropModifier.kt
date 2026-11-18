package com.kyant.backdrop

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightElement
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.InnerShadowElement
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.ShadowElement

private val DefaultHighlight = { Highlight.Default }
private val DefaultShadow = { Shadow.Default }
private val DefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }

fun Modifier.drawPlainBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront
            )
        )
}

fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (() -> Highlight?)? = DefaultHighlight,
    shadow: (() -> Shadow?)? = DefaultShadow,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            if (innerShadow != null) {
                InnerShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = innerShadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (shadow != null) {
                ShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = shadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (highlight != null) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = highlight
                )
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront
            )
        )
}

private class DrawBackdropElement(
    val backdrop: Backdrop,
    val shapeProvider: ShapeProvider,
    val effects: BackdropEffectScope.() -> Unit,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val exportedBackdrop: LayerBackdrop?,
    val onDrawBehind: (DrawScope.() -> Unit)?,
    val onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val onDrawFront: (DrawScope.() -> Unit)?
) : ModifierNodeElement<DrawBackdropNode>() {

    override fun create(): DrawBackdropNode {
        return DrawBackdropNode(
            backdrop = backdrop,
            shapeProvider = shapeProvider,
            effects = effects,
            layerBlock = layerBlock,
            exportedBackdrop = exportedBackdrop,
            onDrawBehind = onDrawBehind,
            onDrawBackdrop = onDrawBackdrop,
            onDrawSurface = onDrawSurface,
            onDrawFront = onDrawFront
        )
    }

    override fun update(node: DrawBackdropNode) {
        node.backdrop = backdrop
        node.shapeProvider = shapeProvider
        node.effects = effects
        node.layerBlock = layerBlock
        node.exportedBackdrop = exportedBackdrop
        node.onDrawBehind = onDrawBehind
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.markDirty()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBackdrop"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["effects"] = effects
        properties["layerBlock"] = layerBlock
        properties["exportedBackdrop"] = exportedBackdrop
        properties["onDrawBehind"] = onDrawBehind
        properties["onDrawBackdrop"] = onDrawBackdrop
        properties["onDrawSurface"] = onDrawSurface
        properties["onDrawFront"] = onDrawFront
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (shapeProvider != other.shapeProvider) return false
        if (effects != other.effects) return false
        if (layerBlock != other.layerBlock) return false
        if (exportedBackdrop != other.exportedBackdrop) return false
        if (onDrawBehind != other.onDrawBehind) return false
        if (onDrawBackdrop != other.onDrawBackdrop) return false
        if (onDrawSurface != other.onDrawSurface) return false
        if (onDrawFront != other.onDrawFront) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (exportedBackdrop?.hashCode() ?: 0)
        result = 31 * result + (onDrawBehind?.hashCode() ?: 0)
        result = 31 * result + onDrawBackdrop.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + (onDrawFront?.hashCode() ?: 0)
        return result
    }
}

private class DrawBackdropNode(
    var backdrop: Backdrop,
    var shapeProvider: ShapeProvider,
    var effects: BackdropEffectScope.() -> Unit,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var exportedBackdrop: LayerBackdrop?,
    var onDrawBehind: (DrawScope.() -> Unit)?,
    var onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var onDrawFront: (DrawScope.() -> Unit)?
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private val effectScope =
        object : BackdropEffectScopeImpl() {

            override val shape: Shape get() = shapeProvider.innerShape
        }

    private var graphicsLayer: GraphicsLayer? = null

    private val layoutLayerBlock: GraphicsLayerScope.() -> Unit = {
        clip = true
        shape = shapeProvider.shape
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }

    private var layoutCoordinates: LayoutCoordinates? = null

    // 窗口位置缓存：只在位置真正变化时才重录背景采样并请求重绘。
    private var lastWindowX = Float.NaN
    private var lastWindowY = Float.NaN

    // 背景采样是否过期。坐标、尺寸、效果参数变化时置为 true。
    private var backdropDirty = true

    private var effectsDirty = true

    private var padding by mutableFloatStateOf(0f)

    private val recordBackdropBlock: (DrawScope.() -> Unit) = {
        val canvas = drawContext.canvas
        val padding = padding

        if (padding != 0f) {
            canvas.translate(padding, padding)
        }
        onDrawBackdrop {
            with(backdrop) {
                drawBackdrop(
                    density = effectScope,
                    coordinates = layoutCoordinates,
                    layerBlock = layerBlock
                )
            }
        }
        if (padding != 0f) {
            canvas.translate(-padding, -padding)
        }
    }

    /** 重新采样背景到离屏层。仅在 backdropDirty 时调用。 */
    private fun DrawScope.recordBackdropLayer() {
        val layer = graphicsLayer ?: return
        val padding = padding
        recordLayer(
            layer,
            size = IntSize(
                size.width.toInt() + padding.toInt() * 2,
                size.height.toInt() + padding.toInt() * 2
            ),
            block = recordBackdropBlock
        )
        layer.topLeft =
            if (padding != 0f) IntOffset(-padding.toInt(), -padding.toInt())
            else IntOffset.Zero
    }

    /** 把已采样的离屏层画到画布，不触发重新录制。 */
    private val drawBackdropLayer: DrawScope.() -> Unit = {
        graphicsLayer?.let { drawLayer(it) }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(IntOffset.Zero, layerBlock = layoutLayerBlock)
        }
    }

    /** 上一次采样到的版本号，用于判断采样源是否已重录 */
    private var lastRevision = -1

    /** 读取采样版本建立快照依赖；变化时标记需要重新采样 */
    private fun observeBackdropRevision() {
        val current = backdrop.revision
        if (current != lastRevision) {
            lastRevision = current
            backdropDirty = true
        }
    }

    override fun ContentDrawScope.draw() {
        // 采样层重录后（换主题、背景尺寸变化）版本号会变，这里建立观察依赖，
        // 变化时自动重绘并重新采样，不再等下一次点击。
        // 组合采样同样透传版本号，开关这类多层叠加的玻璃也能实时刷新。
        observeReads { observeBackdropRevision() }
        if (effectsDirty) {
            observeEffects()
            effectsDirty = false
        }
        // 尺寸或密度变化会改变效果链与采样区域的输入，必须重建并重录
        if (effectScope.update(this)) {
            observeEffects()
            backdropDirty = true
        }

        val needsRecord = backdropDirty
        if (needsRecord) {
            recordBackdropLayer()
        }

        onDrawBehind?.invoke(this)
        drawBackdropLayer()
        onDrawSurface?.invoke(this)
        drawContent()
        onDrawFront?.invoke(this)

        if (needsRecord) {
            exportedBackdrop?.let { exported ->
                exported.graphicsLayer?.let { layer ->
                    recordLayer(layer) {
                        onDrawBehind?.invoke(this)
                        drawBackdropLayer()
                        onDrawSurface?.invoke(this)
                        onDrawFront?.invoke(this)
                    }
                }
                // 导出层内容已更新，消费它的玻璃需要重新采样
                exported.notifyContentChanged()
            }
            backdropDirty = false
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        val position = coordinates.positionInWindow()
        // 位置未变化时既不重录也不请求重绘：静止界面的玻璃保持上一帧采样结果
        if (position.x == lastWindowX && position.y == lastWindowY) return
        lastWindowX = position.x
        lastWindowY = position.y
        layoutCoordinates = if (backdrop.isCoordinatesDependent) coordinates else null
        exportedBackdrop?.layerCoordinates = coordinates
        backdropDirty = true
        invalidateDraw()
    }

    override fun onObservedReadsChanged() {
        effectsDirty = true
        // 采样层版本变化同样需要重新采样
        backdropDirty = true
        invalidateDraw()
    }

    fun invalidateDrawCache() {
        markDirty()
    }

    fun markDirty() {
        backdropDirty = true
        effectsDirty = true
        invalidateDraw()
    }

    private fun observeEffects() {
        observeReads { updateEffects() }
    }

    private fun updateEffects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            effectScope.apply(effects)
            graphicsLayer?.renderEffect = effectScope.renderEffect?.asComposeRenderEffect()
            // padding 决定离屏层的尺寸，变化时必须重新录制采样区域
            if (padding != effectScope.padding) {
                padding = effectScope.padding
                backdropDirty = true
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer = graphicsContext.createGraphicsLayer()
        backdropDirty = true
        effectsDirty = true
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            graphicsLayer = null
        }

        effectScope.reset()
        layoutCoordinates = null
        exportedBackdrop?.layerCoordinates = null
    }
}

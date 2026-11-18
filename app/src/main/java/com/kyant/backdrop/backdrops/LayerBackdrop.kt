package com.kyant.backdrop.backdrops

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.InverseLayerScope

private val DefaultOnDraw: ContentDrawScope.() -> Unit = { drawContent() }

@Composable
fun rememberLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw
): LayerBackdrop {
    val backdrop = remember(graphicsLayer, onDraw) {
        LayerBackdrop(graphicsLayer, onDraw)
    }
    // 主题配色变化会改变背景渐变与光斑，此时必须重新录制采样层
    val colorScheme = MaterialTheme.colorScheme
    LaunchedEffect(colorScheme) { backdrop.invalidateBackground() }
    return backdrop
}

@Stable
class LayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal val onDraw: ContentDrawScope.() -> Unit
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    // 普通字段：坐标只在绘制阶段读取，写入不触发重组
    internal var layerCoordinates: LayoutCoordinates? = null

    @Volatile
    internal var backgroundDirty: Boolean = true

    /**
     * 采样内容版本。用玻璃的节点在绘制阶段读取它，
     * 重录采样层后版本变化，消费端才会重新采样；否则要等一次交互才刷新。
     */
    private var revisionState by mutableIntStateOf(0)
    override val revision: Int get() = revisionState

    // 由 layerBackdrop 节点在 attach 时设置，用于内容变化时请求重绘
    internal var requestRedraw: (() -> Unit)? = null

    private var inverseLayerScope: InverseLayerScope? = null

    internal fun invalidateBackground() {
        backgroundDirty = true
        revisionState++
        requestRedraw?.invoke()
    }

    /** 内容已被外部录制（如 exportedBackdrop），只需通知消费端重采样 */
    internal fun notifyContentChanged() {
        revisionState++
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates ?: return
        val layerCoordinates = layerCoordinates ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            val offset =
                try {
                    layerCoordinates.localPositionOf(coordinates)
                } catch (_: Exception) {
                    // TODO: outer transformations lead to wrong position calculation
                    coordinates.positionInWindow() - layerCoordinates.positionInWindow()
                }
            translate(-offset.x, -offset.y)
        }) {
            drawLayer(graphicsLayer)
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope {
        return inverseLayerScope?.apply { reset() }
            ?: InverseLayerScope().also { inverseLayerScope = it }
    }
}

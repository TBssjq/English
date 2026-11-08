package com.kyant.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.kyant.backdrop.recordLayer

fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier =
    this then LayerBackdropElement(backdrop)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop)
    }

    override fun update(node: LayerBackdropNode) {
        node.backdrop = backdrop
        backdrop.invalidateBackground()
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false
        return backdrop == other.backdrop
    }

    override fun hashCode(): Int = backdrop.hashCode()
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var lastWidth = -1
    private var lastHeight = -1

    override fun ContentDrawScope.draw() {
        drawContent()
        // 背景是静态渐变与光斑，仅在尺寸、主题或内容参数变化时重新录制
        if (backdrop.backgroundDirty) {
            recordLayer(backdrop.graphicsLayer) { backdrop.onDraw(this@draw) }
            backdrop.backgroundDirty = false
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        val size = coordinates.size
        if (size.width != lastWidth || size.height != lastHeight) {
            lastWidth = size.width
            lastHeight = size.height
            backdrop.invalidateBackground()
            invalidateDraw()
        }
        backdrop.layerCoordinates = coordinates
    }

    override fun onAttach() {
        backdrop.requestRedraw = { invalidateDraw() }
        backdrop.invalidateBackground()
        lastWidth = -1
        lastHeight = -1
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
        backdrop.requestRedraw = null
    }
}

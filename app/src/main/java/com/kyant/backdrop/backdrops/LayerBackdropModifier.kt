package com.kyant.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
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
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false

        return true
    }

    override fun hashCode(): Int {
        return backdrop.hashCode()
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    override fun ContentDrawScope.draw() {
        drawContent()
        recordLayer(backdrop.graphicsLayer) { backdrop.onDraw(this@draw) }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            // 仅在窗口位置发生"有意义"的变化时才写状态。
            // 原因：LayoutCoordinates 每次布局都是新对象，无条件赋值会触发重组，
            // 重组又触发布局回调，形成同步无限递归 → StackOverflowError。
            // 这里用 0.5px 阈值，避免亚像素抖动（浮点误差）造成位置"永远在变"。
            val current = backdrop.layerCoordinates
            if (current == null || positionMoved(current, coordinates)) {
                backdrop.layerCoordinates = coordinates
            }
        }
    }

    private fun positionMoved(old: LayoutCoordinates, new: LayoutCoordinates): Boolean {
        val delta = old.positionInWindow() - new.positionInWindow()
        return kotlin.math.abs(delta.x) > 0.5f || kotlin.math.abs(delta.y) > 0.5f
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}

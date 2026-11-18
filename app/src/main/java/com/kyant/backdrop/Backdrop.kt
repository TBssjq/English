package com.kyant.backdrop

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density

interface Backdrop {

    val isCoordinatesDependent: Boolean

    /**
     * 采样内容版本。玻璃节点在绘制阶段读取它建立快照依赖，
     * 采样层重录（换主题、尺寸变化）后能立即重新采样，不必等下一次交互。
     */
    val revision: Int get() = 0

    fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)? = null
    )
}

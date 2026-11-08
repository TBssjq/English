package com.kyant.backdrop

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Immutable
internal class ShapeProvider(val shapeBlock: () -> Shape) {

    /**
     * 以 shapeBlock 的引用作为相等性依据。
     * DrawBackdropElement 用 ShapeProvider 判断是否需要 update()；
     * 默认的引用相等会让每次重组都触发 RenderEffect 重建。
     * 调用方需保证传入的 lambda 稳定（使用 remember）。
     */
    override fun equals(other: Any?): Boolean =
        this === other || (other is ShapeProvider && shapeBlock === other.shapeBlock)

    override fun hashCode(): Int = shapeBlock.hashCode()

    private var _shape: Shape? = null
    private var _outline: Outline? = null
    private var _size: Size = Size.Unspecified
    private var _layoutDirection: LayoutDirection? = null
    private var _density: Float? = null

    val innerShape
        get() = shapeBlock()

    val shape = object : Shape {

        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val shape = shapeBlock()
            if (_shape != shape) {
                _shape = shape
                _outline = null
            }
            if (_outline == null || _size != size || _layoutDirection != layoutDirection || _density != density.density) {
                _size = size
                _layoutDirection = layoutDirection
                _density = density.density
                _outline = shape.createOutline(size, layoutDirection, density)
            }

            return _outline!!
        }
    }
}

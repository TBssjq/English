package com.ssjq.english.ui.glass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull

/**
 * 拖动检测：按下只触发视觉反馈（onDragStart），事件保持未消费，页签自身的点击照常完成；
 * 累计位移越过 touchSlop 之后认定为拖动，此后的位移交给调用方并消费。
 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        // requireUnconsumed = false：即使子项已经接住按下事件，父层也要能把它接管成拖动
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)

        val touchSlop = viewConfiguration.touchSlop
        var pointerId = down.id
        var dragged = false
        // 越过 touchSlop 之前累积的位移，确认是拖动后一次性补齐，避免丢失开头一段行程
        var pending = Offset.Zero
        var upEvent: PointerInputChange? = null

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == pointerId } ?: break
            if (change.changedToUpIgnoreConsumed()) {
                upEvent = change
                break
            }
            // 事件已被其它手势消费（如列表滚动），本次不再接管
            if (change.isConsumed) break

            val delta = change.positionChange()
            if (!dragged) {
                pending += delta
                if (pending.getDistance() <= touchSlop) {
                    // 仍在点击容差内：不消费、不回调，点击照常成立
                    continue
                }
                dragged = true
                change.consume()
                onDrag(change, pending)
                pending = Offset.Zero
            } else {
                change.consume()
                onDrag(change, delta)
            }
            pointerId = change.id
        }

        if (dragged) {
            if (upEvent != null) onDragEnd(upEvent) else onDragCancel()
        } else {
            // 没有形成拖动（纯点击）：只通知取消，让按下反馈回弹，不干扰点击
            onDragCancel()
        }
    }
}

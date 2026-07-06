package com.ssjq.english.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 统一形状规范（M3 风格）：
 * - extraSmall = 8.dp：小按钮、标签、药丸
 * - small = 16.dp：输入框、小组件
 * - medium = 16.dp：中等卡片
 * - large = 24.dp：大卡片
 * - extraLarge = 28.dp：搜索框、底部弹窗、悬浮栏
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

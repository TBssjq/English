package com.ssjq.english.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 现代 fallback 配色（蓝绿系）：
 * 当设备不支持 dynamicColor（Android < 12）或用户关闭动态取色时使用。
 * primary 蓝色 + tertiary 绿色，比默认紫粉更清新现代。
 */

// ========== 亮色模式 ==========
val PrimaryLight = Color(0xFF2563EB)        // 蓝
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFDBE4FF)
val OnPrimaryContainerLight = Color(0xFF001A41)

val SecondaryLight = Color(0xFF565E71)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFDAE2F9)
val OnSecondaryContainerLight = Color(0xFF131C2B)

val TertiaryLight = Color(0xFF10B981)       // 绿
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFCFF5E5)
val OnTertiaryContainerLight = Color(0xFF002117)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = Color(0xFFFDFBFF)
val OnBackgroundLight = Color(0xFF1A1B1F)
val SurfaceLight = Color(0xFFFDFBFF)
val OnSurfaceLight = Color(0xFF1A1B1F)
val SurfaceVariantLight = Color(0xFFE1E2EC)
val OnSurfaceVariantLight = Color(0xFF44474F)
val OutlineLight = Color(0xFF74777F)
val OutlineVariantLight = Color(0xFFC4C6D0)

// ========== 暗色模式 ==========
val PrimaryDark = Color(0xFFB0C6FF)
val OnPrimaryDark = Color(0xFF002E69)
val PrimaryContainerDark = Color(0xFF004193)
val OnPrimaryContainerDark = Color(0xFFDBE4FF)

val SecondaryDark = Color(0xFFBEC6DC)
val OnSecondaryDark = Color(0xFF283041)
val SecondaryContainerDark = Color(0xFF3E4759)
val OnSecondaryContainerDark = Color(0xFFDAE2F9)

val TertiaryDark = Color(0xFF74DCAA)
val OnTertiaryDark = Color(0xFF003826)
val TertiaryContainerDark = Color(0xFF005138)
val OnTertiaryContainerDark = Color(0xFFCFF5E5)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF1A1B1F)
val OnBackgroundDark = Color(0xFFE3E2E6)
val SurfaceDark = Color(0xFF1A1B1F)
val OnSurfaceDark = Color(0xFFE3E2E6)
val SurfaceVariantDark = Color(0xFF44474F)
val OnSurfaceVariantDark = Color(0xFFC4C6D0)
val OutlineDark = Color(0xFF8E9099)
val OutlineVariantDark = Color(0xFF44474F)

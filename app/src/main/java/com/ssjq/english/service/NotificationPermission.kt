package com.ssjq.english.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 通知权限工具：检查 Android 13+ 通知权限状态。
 *
 * Android 13 (API 33) 起 POST_NOTIFICATIONS 为运行时权限，
 * 需用户手动授权后才能显示通知（前台服务通知也需要）。
 */
object NotificationPermission {

    /** 是否需要申请通知权限（Android 13+ 需要） */
    fun needsPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** 是否已授予通知权限（Android 13 以下始终返回 true） */
    fun hasPermission(context: Context): Boolean {
        if (!needsPermission()) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 权限名 */
    val permission: String
        get() = Manifest.permission.POST_NOTIFICATIONS
}

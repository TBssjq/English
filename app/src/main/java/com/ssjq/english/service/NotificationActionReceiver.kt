package com.ssjq.english.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 通知栏按钮点击接收器。
 *
 * 工作流程：
 * 1. 用户点击通知上的"发音/不认识/认识"按钮
 * 2. 系统通过 PendingIntent 触发本接收器
 * 3. 本接收器将动作转发给 LearningForegroundService 处理
 * 4. Service 更新单词/进度，重新 notify 刷新通知 UI
 *
 * 为什么用 BroadcastReceiver 而非直接启动 Service？
 * - BroadcastReceiver 响应更快，无需走 Service 的 onStartCommand 完整链路
 * - 通知按钮点击本就是"一次性事件"，广播更贴合语义
 * - 转发给 Service 后由 Service 统一维护状态，避免状态散落
 *
 * Android 14 适配：
 * - 用 context.startService 转发，目标 Service 已是前台服务，不受后台启动限制
 * - 接收器本身是动态注册或静态注册均可，这里用静态注册（Manifest 声明）
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val wordIndex = intent.getIntExtra(NotificationHelper.EXTRA_WORD_INDEX, -1)
        Log.d(TAG, "收到通知按钮事件: action=$action, index=$wordIndex")

        // 转发给 Service 处理：复用同一 Service 实例，通过 action 区分动作
        val serviceIntent = Intent(context, LearningForegroundService::class.java).apply {
            this.action = LearningForegroundService.ACTION_HANDLE_BUTTON
            putExtra(LearningForegroundService.EXTRA_BUTTON_ACTION, action)
            putExtra(NotificationHelper.EXTRA_WORD_INDEX, wordIndex)
        }

        // Android 8+ 启动 Service：因为是前台服务，不受后台启动限制
        // 但 Android 14+ 对 startService 仍有约束，前台服务场景豁免
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "NotifActionReceiver"
    }
}

package com.ssjq.english.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.ssjq.english.MainActivity
import com.ssjq.english.R

object NotificationHelper {

    const val CHANNEL_ID = "learning_foreground_v2"
    private const val OLD_CHANNEL_ID = "learning_foreground"
    const val NOTIFICATION_ID = 0x7E01

    const val ACTION_SPEAK = "com.ssjq.english.action.SPEAK"
    const val ACTION_WRONG = "com.ssjq.english.action.WRONG"
    const val ACTION_KNOWN = "com.ssjq.english.action.KNOWN"
    const val ACTION_NEXT = "com.ssjq.english.action.NEXT"
    const val ACTION_STOP = "com.ssjq.english.action.STOP"

    const val EXTRA_WORD_INDEX = "extra_word_index"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            if (existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                manager.deleteNotificationChannel(CHANNEL_ID)
            } else {
                return
            }
        }

        manager.getNotificationChannel(OLD_CHANNEL_ID)?.let {
            manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "学习状态",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "后台复习单词时显示实时进度"
            setShowBadge(true)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            importance = NotificationManager.IMPORTANCE_HIGH
        }
        manager.createNotificationChannel(channel)
    }

    fun buildLearningNotification(
        context: Context,
        word: String,
        phonetic: String,
        translation: String,
        index: Int,
        total: Int,
    ): Notification {
        ensureChannel(context)

        val expandedView = RemoteViews(context.packageName, R.layout.notification_learning).apply {
            setTextViewText(R.id.tv_word, word)
            setTextViewText(R.id.tv_phonetic, phonetic.ifBlank { "—" })
            setTextViewText(R.id.tv_translation, translation.ifBlank { "—" })
            setTextViewText(R.id.tv_progress_text, "${index + 1} / $total")
            setProgressBar(R.id.pb_progress, total, index + 1, false)
            setOnClickPendingIntent(R.id.btn_speak, buildActionPendingIntent(context, ACTION_SPEAK, index))
            setOnClickPendingIntent(R.id.btn_wrong, buildActionPendingIntent(context, ACTION_WRONG, index))
            setOnClickPendingIntent(R.id.btn_known, buildActionPendingIntent(context, ACTION_KNOWN, index))
        }

        val compactView = RemoteViews(context.packageName, R.layout.notification_learning_compact).apply {
            setTextViewText(R.id.tv_word, word)
            setTextViewText(R.id.tv_phonetic, phonetic.ifBlank { "—" })
            setTextViewText(R.id.tv_progress_text, "${index + 1}/$total")
            setOnClickPendingIntent(R.id.btn_speak, buildActionPendingIntent(context, ACTION_SPEAK, index))
            setOnClickPendingIntent(R.id.btn_wrong, buildActionPendingIntent(context, ACTION_WRONG, index))
            setOnClickPendingIntent(R.id.btn_known, buildActionPendingIntent(context, ACTION_KNOWN, index))
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = buildActionPendingIntent(context, ACTION_STOP, index)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compactView)
            .setCustomBigContentView(expandedView)
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(false)
            .setContentTitle(word)
            .setContentText(translation.ifBlank { phonetic.ifBlank { "学习中..." } })
            .also { builder ->
                applyDynamicIslandExtras(builder)
            }
            .build()
    }

    private fun buildActionPendingIntent(context: Context, action: String, wordIndex: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_WORD_INDEX, wordIndex)
        }
        val requestCode = action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun applyDynamicIslandExtras(builder: NotificationCompat.Builder) {
        builder.setExtras(android.os.Bundle().apply {
            putInt("miui.progress", 1)
            putBoolean("android.live_activity", true)
            putString("miui.category", "event")
        })
    }
}
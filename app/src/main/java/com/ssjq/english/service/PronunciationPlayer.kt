package com.ssjq.english.service

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.net.URLEncoder

/**
 * 有道词典发音播放器。
 * 复用单个 MediaPlayer，新的发音会先停掉上一个；页面销毁时必须调用 [release]。
 */
class PronunciationPlayer {

    private var player: MediaPlayer? = null

    fun speak(word: String, type: Int = 2) {
        release()
        val encoded = URLEncoder.encode(word, "UTF-8")
        val url = "https://dict.youdao.com/dictvoice?audio=$encoded&type=$type"
        val created = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(url)
            setOnPreparedListener { start() }
            setOnCompletionListener { mp -> finish(mp) }
            setOnErrorListener { mp, _, _ ->
                finish(mp)
                true
            }
            prepareAsync()
        }
        player = created
    }

    fun release() {
        player?.release()
        player = null
    }

    /** 播完或出错：释放该实例；它仍是当前实例时顺带清空字段，避免留下已释放的引用 */
    private fun finish(mp: MediaPlayer) {
        mp.release()
        if (player === mp) player = null
    }
}

package com.wupp.meow.data.media

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.wupp.meow.R

/**
 * Manages low-latency WhatsApp UI sound effects extracted into res/raw.
 */
object SoundPoolManager {
    private var soundPool: SoundPool? = null
    private var sendId = 0
    private var incomingId = 0
    private var pttStartId = 0
    private var pttEndId = 0
    private var pttCancelId = 0
    private var pttSentId = 0
    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()
            val appCtx = context.applicationContext
            sendId = runCatching { pool.load(appCtx, R.raw.send_message, 1) }.getOrDefault(0)
            incomingId = runCatching { pool.load(appCtx, R.raw.incoming_message, 1) }.getOrDefault(0)
            pttStartId = runCatching { pool.load(appCtx, R.raw.ptt_start, 1) }.getOrDefault(0)
            pttEndId = runCatching { pool.load(appCtx, R.raw.ptt_end, 1) }.getOrDefault(0)
            pttCancelId = runCatching { pool.load(appCtx, R.raw.ptt_cancel, 1) }.getOrDefault(0)
            pttSentId = runCatching { pool.load(appCtx, R.raw.ptt_sent, 1) }.getOrDefault(0)
            soundPool = pool
            initialized = true
        }
    }

    fun playSend() = play(sendId)
    fun playIncoming() = play(incomingId)
    fun playPttStart() = play(pttStartId)
    fun playPttEnd() = play(pttEndId)
    fun playPttCancel() = play(pttCancelId)
    fun playPttSent() = play(pttSentId)

    private fun play(sampleId: Int) {
        if (sampleId != 0) {
            soundPool?.play(sampleId, 0.85f, 0.85f, 1, 0, 1.0f)
        }
    }

    fun release() {
        synchronized(this) {
            soundPool?.release()
            soundPool = null
            initialized = false
        }
    }
}

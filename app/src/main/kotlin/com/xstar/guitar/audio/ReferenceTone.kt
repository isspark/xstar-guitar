package com.xstar.guitar.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** Cancellation always stops and releases the reference tone. */
object ReferenceTone {
    suspend fun play(frequency: Double) = withContext(Dispatchers.IO) {
        val rate = 44100
        val samples = ShortArray(rate * 2) { index ->
            val envelope = minOf(index / (rate * .04), (rate * 2 - index) / (rate * .25), 1.0)
            val phase = 2 * PI * frequency * index / rate
            // Harmonics keep low E audible on small phone speakers without changing its pitch.
            val wave = sin(phase) * .55 + sin(phase * 2) * .28 + sin(phase * 3) * .12 + sin(phase * 4) * .05
            (wave * 14000 * envelope).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        try {
            // MODE_STATIC stays in STATE_NO_STATIC_DATA until the first successful write.
            check(track.state != AudioTrack.STATE_UNINITIALIZED)
            check(track.write(samples, 0, samples.size) == samples.size)
            check(track.state == AudioTrack.STATE_INITIALIZED)
            track.play()
            delay(2100)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}

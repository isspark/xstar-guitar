package com.xstar.guitar.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.xstar.guitar.data.PluckEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** Mix all strings into one PCM stream so strums overlap naturally without clipping. */
object ChordPlayer {
    const val sampleRate = 44100
    fun synthesize(events: List<PluckEvent>, rate: Int = sampleRate): ShortArray {
        require(events.isNotEmpty() && rate > 0)
        val length = ((events.maxOf { it.offsetMillis } / 1000.0 + 1.8) * rate).toInt()
        val mix = DoubleArray(length)
        events.forEach { event ->
            val start = event.offsetMillis * rate / 1000
            val frequency = 440.0 * 2.0.pow((event.midiNote - 69) / 12.0)
            for (i in 0 until minOf((rate * 1.8).toInt(), length - start)) {
                val seconds = i.toDouble() / rate
                val phase = 2 * PI * frequency * seconds
                val attack = minOf(1.0, seconds / .005)
                val decay = exp(-seconds * 3.2)
                val wave = sin(phase) * .65 + sin(phase * 2) * .19 + sin(phase * 3) * .09 + sin(phase * 4) * .04
                mix[start + i] += wave * attack * decay * .24
            }
        }
        return ShortArray(length) { (tanh(mix[it]) * 26000).toInt().toShort() }
    }

    suspend fun play(events: List<PluckEvent>, onPosition: (PluckEvent) -> Unit) = withContext(Dispatchers.IO) {
        val samples = synthesize(events)
        coroutineContext.ensureActive()
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        try {
            check(track.state != AudioTrack.STATE_UNINITIALIZED) { "无法初始化声音输出" }
            check(track.write(samples, 0, samples.size) == samples.size) { "无法写入试听音频" }
            track.play()
            var next = 0
            while (track.playbackHeadPosition < samples.size) {
                coroutineContext.ensureActive()
                val elapsed = track.playbackHeadPosition.toLong() * 1000 / sampleRate
                while (next < events.size && events[next].offsetMillis <= elapsed) {
                    val event = events[next++]
                    withContext(Dispatchers.Main) { onPosition(event) }
                }
                kotlinx.coroutines.delay(16)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}

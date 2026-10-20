package com.xstar.guitar.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

data class MetronomeConfig(
    val bpm: Int = 120,
    val meter: String = "4/4",
    val subdivision: Int = 1,
    val voice: String = "electronic",
    val volume: Float = .65f,
    val sound: Boolean = true,
    val animation: Boolean = true,
    val accents: List<Int> = listOf(2, 1, 1, 1),
) {
    val beats: Int get() = meter.substringBefore('/').toInt()
    fun withMeter(value: String): MetronomeConfig = copy(
        meter = value,
        accents = List(value.substringBefore('/').toInt()) { i ->
            if (i == 0 || value == "6/8" && i == 3 || value == "7/8" && i in listOf(2, 4)) 2 else 1
        },
    )
}

/** The sample clock drives sound; the UI follows AudioTrack's consumed frames. */
class AudioMetronome(private val scope: CoroutineScope) {
    @Volatile private var config = MetronomeConfig()
    private var job: Job? = null
    @Volatile private var track: AudioTrack? = null
    private val mutableBeat = MutableStateFlow(-1)
    val beat = mutableBeat.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    fun update(value: MetronomeConfig) { config = value }

    fun start() {
        val previousJob = job
        stop()
        mutableError.value = null
        job = scope.launch(Dispatchers.IO) {
            previousJob?.join()
            val rate = 48_000
            var output: AudioTrack? = null
            try {
                val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                output = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(maxOf(minimum, 1920)).setTransferMode(AudioTrack.MODE_STREAM).build()
                track = output
                output.play()
                val events = java.util.ArrayDeque<Pair<Long, Int>>()
                val buffer = ShortArray(240)
                var frame = 0L
                var nextClick = 0.0
                var step = 0
                var previous = config
                var clickAge = rate
                var frequency = 1400.0
                var amplitude = 0.0
                var voice = "electronic"
                while (isActive) {
                    val current = config
                    if (current.meter != previous.meter || current.subdivision != previous.subdivision) { step = 0; nextClick = frame.toDouble() }
                    previous = current
                    for (i in buffer.indices) {
                        if (frame >= nextClick) {
                            val beatIndex = (step / current.subdivision) % current.beats
                            val sub = step % current.subdivision != 0
                            val level = current.accents[beatIndex]
                            if (!sub) events.add(frame to beatIndex)
                            voice = current.voice
                            frequency = (if (voice == "wood") 900.0 else if (voice == "soft") 660.0 else 1400.0) * (if (level == 2) 1.5 else 1.0) * (if (sub) .72 else 1.0)
                            amplitude = if (level == 0) 0.0 else if (sub) .13 else if (level == 2) .45 else .28
                            clickAge = 0
                            nextClick += rate * 60.0 / current.bpm / current.subdivision
                            step = (step + 1) % (current.beats * current.subdivision)
                        }
                        val duration = if (voice == "soft") .09 else .045
                        val t = clickAge.toDouble() / rate
                        val phase = 2 * PI * if (voice == "wood") (260 * t + (frequency - 260) * .015 * (1 - exp(-t / .015))) else frequency * t
                        val attack = minOf(1.0, t / .002)
                        val sample = if (t < duration && current.sound) sin(phase) * attack * exp(-t * 7 / duration) * amplitude * current.volume else 0.0
                        buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                        clickAge++; frame++
                    }
                    var offset = 0
                    while (offset < buffer.size && isActive) {
                        val written = output.write(buffer, offset, buffer.size - offset, AudioTrack.WRITE_BLOCKING)
                        check(written > 0) { "Audio output interrupted: $written" }
                        offset += written
                    }
                    val played = output.playbackHeadPosition.toLong() and 0xffffffffL
                    while (events.isNotEmpty() && events.first.first <= played) mutableBeat.value = events.removeFirst().second
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (isActive) mutableError.value = "节拍声音播放失败，请重试" }
            finally {
                output?.runCatching { stop(); release() }
                if (track === output) track = null
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        track?.runCatching { pause(); flush() }
        mutableBeat.value = -1
    }
}

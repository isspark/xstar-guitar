package com.xstar.guitar.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlin.math.abs

class AudioTuner(private val context: Context, private val scope: CoroutineScope) {
    private val _reading = MutableStateFlow<PitchReading?>(null)
    val reading: StateFlow<PitchReading?> = _reading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var recorder: AudioRecord? = null
    private var job: Job? = null
    private var smoothedFrequency: Double? = null
    private var lastNote: String? = null
    private var missedFrames = 0

    fun start() {
        if (job?.isActive == true) return
        stop()
        _error.value = null
        smoothedFrequency = null
        lastNote = null
        missedFrames = 0
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        // Most Android phones expose the built-in microphone at 48 kHz. Using
        // that rate avoids an extra resampling stage which can smear the short
        // E4 period and make its weak fundamental disappear.
        val rate = 48_000
        val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val input = try {
            val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
            val candidate = AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
            val usable = if (candidate.state == AudioRecord.STATE_INITIALIZED) candidate else {
                candidate.release()
                AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
            }
            Log.d("XStarTuner", "AudioRecord rate=$rate buffer=$size source=$source state=${usable.state}")
            usable.also { recorder = it; check(it.state == AudioRecord.STATE_INITIALIZED); it.startRecording() }
        } catch (_: Exception) {
            stop()
            _error.value = "无法使用麦克风，请结束通话或录音后重试"
            return
        }
        job = scope.launch(Dispatchers.Default) {
            val buffer = ShortArray(8192)
            var frames = 0
            var emptyFrames = 0
            try {
                while (isActive) {
                    val count = input.read(buffer, 0, buffer.size)
                    if (!isActive) break
                    if (count <= 0) { _error.value = "麦克风输入中断，请重试"; _reading.value = null; break }
                    val detected = PitchDetector.detect(buffer.copyOf(count), rate)
                    if (detected == null) {
                        missedFrames++
                        if (missedFrames % 20 == 0) Log.d("XStarTuner", "no pitch detected, samples=$count")
                        // A high E can briefly fall below the detector threshold
                        // between windows. Keep the last valid reading for a
                        // short period instead of making the UI blink empty.
                        if (missedFrames >= 8) _reading.value = null
                    } else {
                        missedFrames = 0
                        emptyFrames = 0
                        _reading.value = detected.let {
                            val result = smooth(it)
                            if (++frames % 10 == 0) Log.d("XStarTuner", "raw=${it.frequency}Hz/${it.note} ${it.cents}c confidence=${it.confidence}; smoothed=${result.frequency}Hz/${result.note} ${result.cents}c")
                            result
                        }
                    }
                }
            } catch (_: Exception) { if (isActive) { _reading.value = null; _error.value = "麦克风输入中断，请重试" } }
        }
    }

    private fun smooth(reading: PitchReading): PitchReading {
        val previous = smoothedFrequency
        val current = reading.frequency.toDouble()
        // Smooth within the same detected note. A note change is a real
        // candidate for another string and is allowed through immediately.
        // Also switch immediately across a large frequency jump. This is
        // important when a detector recovers from an octave error: E4 at
        // 329 Hz must not be pulled back down to the previous false E2/E3.
        if (previous != null && kotlin.math.abs(current - previous) / previous > .18) {
            smoothedFrequency = current
            val corrected = noteReading(current, reading.confidence)
            lastNote = corrected.note
            return corrected
        }
        val next = if (previous == null) current else previous + (current - previous) * .24
        smoothedFrequency = next
        lastNote = reading.note
        return noteReading(next, reading.confidence)
    }

    private fun noteReading(frequency: Double, confidence: Float): PitchReading {
        val midi = (69.0 + 12.0 * kotlin.math.log2(frequency / 440.0)).let { kotlin.math.round(it).toInt() }
        val nearestFrequency = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
        return PitchReading(frequency.toFloat(), (1200.0 * kotlin.math.log2(frequency / nearestFrequency)).toFloat(), names[((midi % 12) + 12) % 12], confidence)
    }

    fun stop() { job?.cancel(); job = null; recorder?.runCatching { stop(); release() }; recorder = null; smoothedFrequency = null; lastNote = null; _reading.value = null }
}

package com.xstar.guitar.audio

import kotlin.math.abs
import kotlin.math.log2

data class PitchReading(val frequency: Float, val cents: Float, val note: String, val confidence: Float)

/** Allocation-free-ish autocorrelation pitch detector for monophonic guitar input. */
object PitchDetector {
    private val notes = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

    fun detect(samples: ShortArray, sampleRate: Int): PitchReading? {
        if (samples.isEmpty()) return null
        // Phone microphones often have a small DC offset. It must not be part
        // of the correlation, otherwise the detector can prefer a false low
        // frequency peak (or reject a quiet but valid guitar note).
        var mean = 0.0
        for (sample in samples) mean += sample
        mean /= samples.size
        val centered = DoubleArray(samples.size)
        var energy = 0.0
        for (i in samples.indices) {
            centered[i] = samples[i] - mean
            energy += centered[i] * centered[i]
        }
        if (energy / samples.size < 20_000.0) return null
        val minLag = sampleRate / 500
        val maxLag = (sampleRate / 55).coerceAtMost(samples.size - 1)
        val correlations = DoubleArray(maxLag + 1)
        var bestLag = 0
        var best = 0.0
        for (lag in minLag..maxLag) {
            val sum = correlation(centered, lag)
            correlations[lag] = sum
            if (sum > best) { best = sum; bestLag = lag }
        }
        // The high E string can have a stronger second harmonic than its
        // fundamental. Only apply the octave correction in the guitar's high
        // register; this avoids turning the normal lower-string periods into
        // arbitrary long-lag peaks.
        if (bestLag > 0 && sampleRate.toDouble() / bestLag > 400.0) {
            val doubled = bestLag * 2
            if (doubled <= maxLag && correlations[doubled] >= best * 0.25) {
                bestLag = doubled
            }
        }
        if (bestLag == 0) return null
        // A guitar string's period is rarely an integer number of samples. This
        // matters most for the high E (E4 is ~133.8 samples at 44.1 kHz): using
        // the integer lag alone can introduce several cents of fake error.
        val refinedLag = if (bestLag > minLag && bestLag < maxLag) {
            val previous = correlation(centered, bestLag - 1)
            val current = correlations[bestLag]
            val next = correlation(centered, bestLag + 1)
            val denominator = previous - 2.0 * current + next
            if (abs(denominator) > 1e-9) {
                (bestLag + 0.5 * (previous - next) / denominator).coerceAtLeast(1.0)
            } else bestLag.toDouble()
        } else bestLag.toDouble()
        val frequency = (sampleRate / refinedLag).toFloat()
        val midi = (69.0 + 12.0 * log2(frequency / 440.0)).roundToInt()
        val target = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        return PitchReading(frequency, (1200.0 * log2(frequency / target)).toFloat(), notes[((midi % 12) + 12) % 12], (best / energy).toFloat().coerceIn(0f, 1f))
    }

    private fun correlation(samples: DoubleArray, lag: Int): Double {
        var sum = 0.0
        var i = 0
        while (i + lag < samples.size) {
            sum += samples[i] * samples[i + lag]
            i++
        }
        return sum
    }

    private fun Double.roundToInt() = kotlin.math.round(this).toInt()
}

package com.xstar.guitar.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class GuitarTuningTest {
    @Test fun standardStringsResolveWithOctaves() {
        GuitarTuning.strings.forEach { string ->
            assertEquals(string, GuitarTuning.nearest(string.frequency))
            assertEquals(0f, GuitarTuning.cents(string.frequency, string), .001f)
        }
    }
    @Test fun lockedStringDoesNotTreatOtherNotesAsInTune() {
        val e2 = GuitarTuning.strings.first()
        assertEquals(100f, GuitarTuning.cents(e2.frequency * Math.pow(2.0, 1.0 / 12), e2), .01f)
        assertEquals(-12f, GuitarTuning.cents(e2.frequency * Math.pow(2.0, -12.0 / 1200), e2), .01f)
    }
    @Test fun referenceFrequencyAdjustsTargets() {
        val a2 = GuitarTuning.strings[1]
        assertEquals(0f, GuitarTuning.cents(111.0, a2, 444), .001f)
    }

    @Test fun pitchDetectorKeepsHighEAccurateBetweenIntegerSampleLags() {
        val sampleRate = 44_100
        val frequency = GuitarTuning.strings.last().frequency
        val samples = ShortArray(4096) { index ->
            (sin(2.0 * PI * frequency * index / sampleRate) * 12_000).toInt().toShort()
        }

        val reading = PitchDetector.detect(samples, sampleRate)

        assertEquals("E", reading?.note)
        assertEquals(0f, reading?.cents ?: Float.NaN, 1f)
    }

    @Test fun pitchDetectorRejectsHighESecondHarmonicAsTheNote() {
        val sampleRate = 44_100
        val frequency = GuitarTuning.strings.last().frequency
        val samples = ShortArray(4096) { index ->
            val phase = 2.0 * PI * frequency * index / sampleRate
            (sin(phase) * 1_500 + sin(phase * 2) * 10_000 + sin(phase * 3) * 4_000).toInt().toShort()
        }

        val reading = PitchDetector.detect(samples, sampleRate)

        assertEquals("E", reading?.note)
        assertEquals(0f, reading?.cents ?: Float.NaN, 2f)
    }
}

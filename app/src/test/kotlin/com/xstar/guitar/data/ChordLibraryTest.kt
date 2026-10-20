package com.xstar.guitar.data

import com.xstar.guitar.audio.ChordPlayer
import org.junit.Assert.*
import org.junit.Test

class ChordLibraryTest {
    @Test fun everyVoicingHasCorrectNotesAndPlayableFingerAssignments() {
        assertEquals(120, ChordLibrary.chords.size)
        assertEquals(253, ChordLibrary.chords.sumOf { ChordLibrary.voicings(it).size })
        ChordLibrary.chords.forEach { chord ->
            val expected = chord.type.intervals.map { (chord.pitchClass + it) % 12 }.toSet()
            // A seventh voicing may omit its perfect fifth, but not its defining tones.
            val required = chord.type.intervals.filterNot { it == 7 && expected.size == 4 }.map { (chord.pitchClass + it) % 12 }
            ChordLibrary.voicings(chord).forEach { v ->
                val actual = v.soundingStrings.map { (ChordLibrary.tuning[it] + v.frets[it]) % 12 }.toSet()
                assertTrue("${chord.id} has a non-chord tone", expected.containsAll(actual))
                assertTrue("${chord.id} is missing defining tones", actual.containsAll(required))
                val positions = mutableMapOf<Int, Int>()
                v.frets.forEachIndexed { string, fret ->
                    if (fret > 0) {
                        val finger = v.fingers[string]
                        assertTrue("${chord.id} has invalid finger", finger in 1..4)
                        positions[finger]?.let { assertEquals("${chord.id} uses one finger on different frets", it, fret) }
                        positions[finger] = fret
                    }
                }
            }
        }
    }

    @Test fun rightHandPatternsSkipMutedStringsAndFollowExpectedOrder() {
        ChordLibrary.chords.forEach { chord -> ChordLibrary.voicings(chord).forEach { v ->
            assertEquals(v.soundingStrings, ChordLibrary.sequence(v, PickingStyle.Down).map { it.stringIndex })
            assertEquals(v.soundingStrings.reversed(), ChordLibrary.sequence(v, PickingStyle.Up).map { it.stringIndex })
            PickingStyle.entries.forEach { style ->
                val events = ChordLibrary.sequence(v, style)
                assertTrue(events.all { v.frets[it.stringIndex] >= 0 })
                assertTrue(events.zipWithNext().all { (a, b) -> a.offsetMillis < b.offsetMillis })
                assertTrue(events.all { it.midiNote == ChordLibrary.tuning[it.stringIndex] + v.frets[it.stringIndex] })
            }
        } }
        val c = ChordLibrary.chords.first { it.id == "C" }
        val pattern = ChordLibrary.sequence(ChordLibrary.voicings(c).first(), PickingStyle.Pima)
        assertEquals(listOf(1, 3, 4, 5, 4, 3), pattern.map { it.stringIndex })
        assertEquals(listOf("P 拇指", "i 食指", "m 中指", "a 无名指", "m 中指", "i 食指"), pattern.map { it.finger })
    }

    @Test fun searchSupportsAccidentalsAndChineseTypes() {
        assertEquals(listOf("Cmaj7"), ChordLibrary.search(" Cmaj7 ").map { it.id })
        assertTrue(ChordLibrary.search("C#").all { it.root == "C♯" })
        assertEquals(10, ChordLibrary.search("Eb").size)
        assertEquals(12, ChordLibrary.search("小七和弦").size)
    }

    @Test fun synthesizedStrumHasNoOverflowAndDecaysToSilence() {
        val em = ChordLibrary.chords.first { it.id == "Em" }
        val events = ChordLibrary.sequence(ChordLibrary.voicings(em).first(), PickingStyle.Down)
        val samples = ChordPlayer.synthesize(events)
        assertEquals(0, samples.first().toInt())
        assertTrue(samples.any { kotlin.math.abs(it.toInt()) > 2000 })
        assertTrue(samples.all { kotlin.math.abs(it.toInt()) <= 26000 })
        val early = samples.sliceArray(5000..10000).map { kotlin.math.abs(it.toInt()) }.average()
        val tail = samples.takeLast(4000).map { kotlin.math.abs(it.toInt()) }.average()
        assertTrue("Plucked strings should decay", tail < early * .05)
    }
}

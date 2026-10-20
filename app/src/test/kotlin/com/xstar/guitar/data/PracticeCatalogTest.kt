package com.xstar.guitar.data

import org.junit.Assert.*
import org.junit.Test

class PracticeCatalogTest {
    @Test fun catalogReferencesAndGuidanceAreValid() {
        val exercises = PracticeCatalog.exercises
        assertEquals(exercises.size, exercises.map { it.id }.distinct().size)
        PracticeCatalog.categories.forEach { category -> assertTrue(exercises.any { it.categoryId == category.id }) }
        exercises.forEach { exercise ->
            assertTrue(PracticeCatalog.categories.any { it.id == exercise.categoryId })
            assertTrue(exercise.goal.isNotBlank())
            assertTrue(exercise.steps.size >= 3)
            assertTrue(exercise.actions.isNotEmpty())
            assertTrue(exercise.notes.all { it in 0..127 })
            exercise.chords.forEach { name ->
                val chord = ChordLibrary.chords.first { it.id == name }
                assertTrue(ChordLibrary.voicings(chord).first().soundingStrings.isNotEmpty())
            }
            exercise.prerequisites.forEach { prerequisite -> assertNotNull("${exercise.id}: $prerequisite", PracticeCatalog.find(prerequisite)) }
            assertTrue(exercise.application.isNotBlank())
        }
    }

    @Test fun curriculumCoversAllDocumentDomainsAndStages() {
        assertEquals(16, PracticeCatalog.categories.size)
        assertTrue(PracticeCatalog.exercises.size >= 70)
        listOf("prepare", "left", "right", "chord", "transition", "rhythm", "strum", "fingerstyle", "percussion", "ear", "theory", "fretboard", "technique", "performance", "creation", "management").forEach { id ->
            assertTrue("Missing category: $id", PracticeCatalog.categories.any { it.id == id })
        }
        PracticeLevel.entries.forEach { level -> assertTrue(PracticeCatalog.exercises.any { it.level == level }) }
    }

    @Test fun earAnswersMatchActualPitchesAcrossSeeds() {
        for (seed in 0..50) for (index in 0..9) {
            val pitch = EarTraining.question("pitch", index, seed)
            val difference = pitch.notes[1] - pitch.notes[0]
            assertEquals(if (difference > 0) 0 else if (difference < 0) 1 else 2, pitch.answer)
            val interval = EarTraining.question("interval", index, seed)
            assertEquals(listOf(4, 7, 12)[interval.answer], interval.notes[1] - interval.notes[0])
            val quality = EarTraining.question("quality", index, seed)
            assertTrue(quality.simultaneous)
            assertEquals(if (quality.answer == 0) 4 else 3, quality.notes[1] - quality.notes[0])
            assertEquals(7, quality.notes[2] - quality.notes[0])
            assertEquals(pitch, EarTraining.question("pitch", index, seed))
        }
    }
}

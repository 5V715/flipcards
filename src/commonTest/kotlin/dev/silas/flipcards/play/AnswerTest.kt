package dev.silas.flipcards.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnswerTest {
    @Test fun matchesIgnoringCaseAccentsAndSpaces() {
        assertEquals(true, suggest("  bogota ", "Bogotá"))
        assertEquals(true, suggest("new   delhi", "New Delhi"))
        assertEquals(true, suggest("SAO TOME", "São Tomé"))
        assertEquals(false, suggest("Wien", "Vienna"))
    }

    @Test fun noSuggestionWithoutInputOrAnswer() {
        assertNull(suggest("", "Vienna"))
        assertNull(suggest("   ", "Vienna"))
        assertNull(suggest("Vienna", null))
    }
}

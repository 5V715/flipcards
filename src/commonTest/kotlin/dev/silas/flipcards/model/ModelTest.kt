package dev.silas.flipcards.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    private val vienna = Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien")))

    @Test fun resolvesPlayLanguage() = assertEquals("Wien", vienna.resolveText("de", "en"))

    @Test fun fallsBackWhenMissing() = assertEquals("Vienna", vienna.resolveText("es", "en"))

    @Test fun fallsBackWhenBlank() =
        assertEquals(
            "Vienna",
            Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "  "))).resolveText("de", "en"),
        )

    @Test fun sameIgnoresLanguage() = assertEquals("casa", Side(SideText.Same("casa")).resolveText("de", "en"))

    @Test fun noTextResolvesToNull() {
        assertNull(Side().resolveText("en", "en"))
        assertNull(Side(SideText.Same(" ")).resolveText("en", "en"))
    }

    @Test fun completeness() {
        assertTrue(Side(imageId = "i1").isComplete("en"))
        assertTrue(Side(SideText.Same("casa")).isComplete("en"))
        assertTrue(vienna.isComplete("en"))
        assertFalse(Side().isComplete("en"))
        assertFalse(Side(SideText.Same("")).isComplete("en"))
        assertFalse(Side(SideText.Translated(mapOf("de" to "Wien"))).isComplete("en")) // first language missing
    }

    @Test fun completeCardsSkipsIncomplete() {
        val stack = Stack("s", "S", listOf("en"), listOf(Card("a", vienna, vienna), Card("b", vienna, Side())))
        assertEquals(listOf("a"), stack.completeCards.map { it.id })
        assertEquals(2, stack.summary().cardCount)
    }

    @Test fun jsonRoundTrip() {
        val stack = Stack("s", "S", listOf("en", "de"), listOf(Card("a", Side(imageId = "i1"), vienna)))
        assertEquals(stack, FlipJson.decodeFromString<Stack>(FlipJson.encodeToString(stack)))
    }
}

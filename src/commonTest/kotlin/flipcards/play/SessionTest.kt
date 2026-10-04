package flipcards.play

import flipcards.model.Card
import flipcards.model.Side
import flipcards.model.SideText
import flipcards.model.Stack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionTest {
    private val ids = listOf("a", "b", "c", "d")
    private fun start(seed: Long = 1) = startSession(ids, "en", HintMode.HINTED, seed)

    @Test fun shufflesDeterministically() {
        assertEquals(start(1).queue, start(1).queue)
        assertEquals(ids.toSet(), start(1).queue.toSet())
        assertEquals(4, start(1).total)
    }

    @Test fun knownCardLeavesQueue() {
        val s = start()
        val next = s.grade(true)
        assertEquals(s.queue.drop(1), next.queue)
        assertEquals(emptyList(), next.missed)
        assertEquals(1, next.step)
    }

    @Test fun missedCardGoesToEnd() {
        val s = start()
        val next = s.grade(false)
        assertEquals(s.queue.drop(1) + s.queue.first(), next.queue)
        assertEquals(listOf(s.queue.first()), next.missed)
    }

    @Test fun missedTwiceCountsOnce() {
        var s = startSession(listOf("a"), "en", HintMode.NONE, 1)
        s = s.grade(false).grade(false)
        assertEquals(listOf("a"), s.missed)
        assertFalse(s.isFinished)
        s = s.grade(true)
        assertTrue(s.isFinished)
        assertEquals(0, s.result().knownFirstTime)
    }

    @Test fun scoreCountsFirstAttempts() {
        var s = start()
        s = s.grade(true).grade(false).grade(true).grade(true).grade(true) // 4 cards, one missed then known
        assertTrue(s.isFinished)
        assertEquals(3, s.result().knownFirstTime)
        assertEquals(4, s.result().total)
    }

    @Test fun hintChangesWhenACardReturns() {
        val stack = Stack(
            "s", "S", listOf("en"),
            listOf(Card("a", Side(SideText.Same("Austria")), Side(SideText.Same("Vienna Vienna Vienna")))),
        )
        val first = startSession(listOf("a"), "en", HintMode.HINTED, 3)
        val hints = generateSequence(first) { it.grade(false) }.take(6).map { hintFor(stack, it) }.toSet()
        assertTrue(hints.size > 1)
    }

    @Test fun hintUsesPlayLanguageWithFallback() {
        val back = Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien")))
        val stack = Stack("s", "S", listOf("en", "de"), listOf(Card("a", Side(imageId = "i"), back)))
        assertEquals("_ _ _ _", hintFor(stack, startSession(listOf("a"), "de", HintMode.LENGTH_ONLY, 1)))
        assertEquals("_ _ _ _ _ _", hintFor(stack, startSession(listOf("a"), "es", HintMode.LENGTH_ONLY, 1)))
    }
}

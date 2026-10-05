package dev.silas.flipcards.play

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
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

    @Test fun missedCardIsNotAskedAgain() {
        val s = start()
        val next = s.grade(false)
        assertEquals(s.queue.drop(1), next.queue)
        assertEquals(listOf(s.queue.first()), next.missed)
    }

    @Test fun aRoundIsOnePassEvenWhenNothingIsKnown() { // review: the game never ended
        var s = start()
        repeat(4) { s = s.grade(false) }
        assertTrue(s.isFinished)
        assertEquals(s.result().missed.toSet(), ids.toSet())
        assertEquals(0, s.result().known)
    }

    @Test fun countsKnownCards() {
        var s = start()
        s = s.grade(true).grade(false).grade(true).grade(true)
        assertTrue(s.isFinished)
        assertEquals(3, s.result().known)
        assertEquals(4, s.result().total)
    }

    @Test fun eachCardGetsItsOwnHint() {
        val stack = Stack(
            "s", "S", listOf("en"),
            listOf(Card("a", Side(SideText.Same("Austria")), Side(SideText.Same("Vienna Vienna Vienna")))),
        )
        val first = startSession(listOf("a"), "en", HintMode.HINTED, 3)
        val hints = (0..5).map { hintFor(stack, first.copy(step = it))?.pattern() }.toSet()
        assertTrue(hints.size > 1)
    }

    @Test fun hintUsesPlayLanguageWithFallback() {
        val back = Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien")))
        val stack = Stack("s", "S", listOf("en", "de"), listOf(Card("a", Side(imageId = "i"), back)))
        assertEquals("_ _ _ _", hintFor(stack, startSession(listOf("a"), "de", HintMode.LENGTH_ONLY, 1))?.pattern())
        assertEquals("_ _ _ _ _ _", hintFor(stack, startSession(listOf("a"), "es", HintMode.LENGTH_ONLY, 1))?.pattern())
    }

    @Test fun pointsOnlyForKnownCards() {
        var s = start()
        s = s.grade(true).grade(false).grade(true).grade(true)
        assertEquals(30, s.result().score) // 3 cards known, 10 points each with hints
    }

    @Test fun harderLevelsGiveMorePoints() {
        fun session(level: HintLevel) = startSession(listOf("a"), "en", HintMode.HINTED, 1, level = level)
        assertEquals(listOf(5, 10, 15), HintLevel.entries.map { session(it).grade(true).score })
        assertEquals(HintLevel.HARD, session(HintLevel.HARD).result().level)
    }

    @Test fun fewerHintsGiveMorePoints() {
        assertEquals(20, startSession(listOf("a"), "en", HintMode.LENGTH_ONLY, 1).grade(true).score)
        assertEquals(30, startSession(listOf("a"), "en", HintMode.NONE, 1).grade(true).score)
    }

    @Test fun resultCarriesSecondLanguageAndWhetherItCounts() {
        val result = startSession(listOf("a"), "en", HintMode.NONE, 1, secondLanguage = "de", countsForBest = false)
            .grade(true).result()
        assertEquals("de", result.secondLanguage)
        assertFalse(result.countsForBest)
    }
}

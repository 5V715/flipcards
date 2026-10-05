package dev.silas.flipcards.play

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HintTest {
    @Test fun lengthOnly() = assertEquals("_ _ _ _ _ _", hint("Vienna", HintMode.LENGTH_ONLY, Random(1)))

    @Test fun keepsSpacesAndPunctuation() {
        assertEquals("_ _ _   _ _ _ _ _", hint("New Delhi", HintMode.LENGTH_ONLY, Random(1)))
        assertEquals("_ _ . _ _ _ _", hint("St.John", HintMode.LENGTH_ONLY, Random(1)))
    }

    @Test fun hidesNonLatinLetters() = assertEquals("_ _ _ _", hint("Київ", HintMode.LENGTH_ONLY, Random(1)))

    @Test fun hintedRevealsAThird() {
        val h = hint("Vienna", HintMode.HINTED, Random(1))!!
        val shown = h.split(" ")
        assertEquals(6, shown.size)
        assertEquals(2, shown.count { it != "_" }) // max(1, 6 / 3)
        shown.forEachIndexed { i, c -> if (c != "_") assertEquals("Vienna"[i].toString(), c) }
    }

    @Test fun hintedRevealsAtLeastOne() =
        assertEquals(1, hint("Rom", HintMode.HINTED, Random(1))!!.count { it.isLetter() })

    @Test fun hintedSingleCharacterRevealsNothing() = assertEquals("_", hint("A", HintMode.HINTED, Random(1)))

    @Test fun sameSeedSameHint() =
        assertEquals(hint("Vienna", HintMode.HINTED, Random(7)), hint("Vienna", HintMode.HINTED, Random(7)))

    @Test fun noHint() {
        assertNull(hint("Vienna", HintMode.NONE, Random(1)))
        assertNull(hint(null, HintMode.HINTED, Random(1)))
        assertNull(hint("  ", HintMode.LENGTH_ONLY, Random(1)))
    }

    @Test fun slotsKeepSpacesAndRevealedLetters() {
        val slots = hintSlots("ab c", HintMode.LENGTH_ONLY, Random(1))!!
        assertEquals(listOf(Slot.Blank, Slot.Blank, Slot.Fixed(' '), Slot.Blank), slots)
        assertEquals(3, slots.blankCount)
    }

    @Test fun typedLettersFillTheBlanksInOrder() {
        val slots = listOf(Slot.Blank, Slot.Fixed('i'), Slot.Blank, Slot.Fixed(' '), Slot.Blank)
        assertEquals("_i_ _", slots.fill(""))
        assertEquals("Vi_ _", slots.fill("V"))
        assertEquals("Vie n", slots.fill("Ven"))
        assertEquals(listOf('V', null, 'e', null, null), slots.typedPerSlot("Ve"))
    }

    @Test fun onlyLettersAndDigitsFitTheBlanks() {
        val slots = listOf(Slot.Blank, Slot.Fixed(' '), Slot.Blank)
        assertEquals("ab", slots.acceptTyped("a b"))
        assertEquals("ab", slots.acceptTyped("abc"))
        assertEquals("ó1", slots.acceptTyped("ó-1"))
    }

    private fun shown(answer: String, level: HintLevel) =
        hintSlots(answer, HintMode.HINTED, Random(1), level)!!.count { it is Slot.Fixed && it.char.isLetter() }

    @Test fun levelsShowMoreOrFewerLetters() {
        val answer = "Copenhagen" // 10 letters
        assertEquals(5, shown(answer, HintLevel.EASY))
        assertEquals(3, shown(answer, HintLevel.MEDIUM))
        assertEquals(2, shown(answer, HintLevel.HARD))
    }

    @Test fun everyLevelShowsAtLeastOneLetter() {
        assertEquals(1, shown("Rom", HintLevel.HARD))
        assertEquals(1, shown("Oslo", HintLevel.HARD))
        assertEquals(2, shown("Oslo", HintLevel.EASY))
    }

    @Test fun levelsDoNotChangeTheOtherModes() {
        assertEquals("_ _ _ _", hint("Oslo", HintMode.LENGTH_ONLY, Random(1), HintLevel.EASY))
        assertEquals(null, hint("Oslo", HintMode.NONE, Random(1), HintLevel.EASY))
    }
}

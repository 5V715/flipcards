package flipcards.play

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
}

package dev.silas.flipcards.play

import kotlin.math.max
import kotlin.random.Random

enum class HintMode { HINTED, LENGTH_ONLY, NONE }

/** How many letters [HintMode.HINTED] shows: one in [divisor] of the letters, but at least one. */
enum class HintLevel(val divisor: Int) { EASY(2), MEDIUM(3), HARD(5) }

/** One character of the answer as the player sees it while typing. */
sealed interface Slot {
    /** A letter or digit the player has to type. */
    data object Blank : Slot

    /** A character shown as it is: a revealed letter, a space or punctuation. */
    data class Fixed(val char: Char) : Slot
}

/**
 * The slots for [answer]. Letters and digits are blanks, spaces and punctuation stay;
 * in [HintMode.HINTED] some letters are revealed, how many depends on [level]. Returns null when
 * there is nothing to hint at.
 */
fun hintSlots(answer: String?, mode: HintMode, random: Random, level: HintLevel = HintLevel.MEDIUM): List<Slot>? {
    if (mode == HintMode.NONE || answer.isNullOrBlank()) return null
    val hidden = answer.indices.filter { answer[it].isLetterOrDigit() }
    val revealCount = when {
        mode == HintMode.LENGTH_ONLY || hidden.size <= 1 -> 0
        else -> max(1, hidden.size / level.divisor)
    }
    val revealed = hidden.shuffled(random).take(revealCount).toSet()
    return answer.mapIndexed { i, c -> if (i in hidden && i !in revealed) Slot.Blank else Slot.Fixed(c) }
}

/** The slots as a pattern such as `_ i _ _ _ a`. */
fun List<Slot>.pattern(): String =
    map { if (it is Slot.Fixed) it.char else '_' }.joinToString(" ")

/** A pattern such as `_ i _ _ _ a` for [answer], or null when there is nothing to hint at. */
fun hint(answer: String?, mode: HintMode, random: Random, level: HintLevel = HintLevel.MEDIUM): String? =
    hintSlots(answer, mode, random, level)?.pattern()

val List<Slot>.blankCount: Int get() = count { it == Slot.Blank }

/** Only what can go into a blank: letters and digits, at most one per blank. */
fun List<Slot>.acceptTyped(typed: String): String = typed.filter { it.isLetterOrDigit() }.take(blankCount)

/** The character typed into each slot, in order: null for a blank not typed yet and for a fixed slot. */
fun List<Slot>.typedPerSlot(typed: String): List<Char?> {
    var next = 0
    return map { slot -> if (slot == Slot.Blank) typed.getOrNull(next++) else null }
}

/** The whole answer as typed into the slots: blanks filled in order, fixed characters kept, unfilled blanks as `_`. */
fun List<Slot>.fill(typed: String): String =
    zip(typedPerSlot(typed)) { slot, char -> if (slot is Slot.Fixed) slot.char else char ?: '_' }.joinToString("")

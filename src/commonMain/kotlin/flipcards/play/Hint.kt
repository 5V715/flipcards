package flipcards.play

import kotlin.math.max
import kotlin.random.Random

enum class HintMode { HINTED, LENGTH_ONLY, NONE }

/**
 * A pattern such as `_ i _ _ _ a` for [answer]. Letters and digits are hidden,
 * spaces and punctuation stay. Returns null when there is nothing to hint at.
 */
fun hint(answer: String?, mode: HintMode, random: Random): String? {
    if (mode == HintMode.NONE || answer.isNullOrBlank()) return null
    val hidden = answer.indices.filter { answer[it].isLetterOrDigit() }
    val revealCount = when {
        mode == HintMode.LENGTH_ONLY || hidden.size <= 1 -> 0
        else -> max(1, hidden.size / 3)
    }
    val revealed = hidden.shuffled(random).take(revealCount).toSet()
    return answer.mapIndexed { i, c -> if (i in hidden && i !in revealed) '_' else c }.joinToString(" ")
}

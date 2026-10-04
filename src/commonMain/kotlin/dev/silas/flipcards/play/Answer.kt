package dev.silas.flipcards.play

/** Removes accents: "Bogotá" becomes "Bogota". Needs Unicode normalization, so each platform supplies it. */
expect fun stripAccents(text: String): String

private val whitespace = Regex("\\s+")

fun normalizeAnswer(text: String): String =
    stripAccents(text.trim().replace(whitespace, " ").lowercase())

/** Whether [typed] looks right, or null when there is nothing to compare. */
fun suggest(typed: String, correct: String?): Boolean? {
    if (typed.isBlank() || correct.isNullOrBlank()) return null
    return normalizeAnswer(typed) == normalizeAnswer(correct)
}

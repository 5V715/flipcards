package dev.silas.flipcards.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A named collection of cards. The first language is the fallback for missing translations. */
@Serializable
data class Stack(
    val id: String,
    val name: String,
    val languages: List<String>,
    val cards: List<Card>,
)

@Serializable
data class Card(val id: String, val front: Side = Side(), val back: Side = Side())

@Serializable
data class Side(val text: SideText? = null, val imageId: String? = null)

@Serializable
sealed interface SideText {
    /** The same text whatever the play language, e.g. "casa". */
    @Serializable
    @SerialName("same")
    data class Same(val value: String) : SideText

    /** One text per language code, e.g. en = "Vienna", de = "Wien". */
    @Serializable
    @SerialName("translated")
    data class Translated(val values: Map<String, String>) : SideText
}

enum class Face { FRONT, BACK }

data class StackSummary(val id: String, val name: String, val languages: List<String>, val cardCount: Int)

val FlipJson = Json { ignoreUnknownKeys = true }

fun Card.side(face: Face): Side = if (face == Face.FRONT) front else back

/** The text to show for [language], or null when the side has no non-blank text. */
fun Side.resolveText(language: String, fallback: String): String? {
    val resolved = when (val text = text) {
        null -> null
        is SideText.Same -> text.value
        is SideText.Translated ->
            text.values[language]?.takeIf { it.isNotBlank() } ?: text.values[fallback]
    }
    return resolved?.takeIf { it.isNotBlank() }
}

fun Side.isComplete(firstLanguage: String): Boolean =
    imageId != null || resolveText(firstLanguage, firstLanguage) != null

fun Card.isComplete(firstLanguage: String): Boolean =
    front.isComplete(firstLanguage) && back.isComplete(firstLanguage)

val Stack.completeCards: List<Card>
    get() = languages.firstOrNull()?.let { first -> cards.filter { it.isComplete(first) } } ?: emptyList()

fun Stack.summary(): StackSummary = StackSummary(id, name, languages, cards.size)

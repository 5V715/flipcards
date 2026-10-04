package dev.silas.flipcards.play

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.resolveText
import kotlin.random.Random

/** One run through a set of cards. Immutable: [grade] returns the next session. */
data class Session(
    val language: String,
    val mode: HintMode,
    /** Card ids still to be answered; the first is the current card. */
    val queue: List<String>,
    val total: Int,
    /** Cards marked "Didn't know" at least once, in the order they were first missed. */
    val missed: List<String>,
    val seed: Long,
    /** Increases with every grade, so a returning card gets a different hint. */
    val step: Int = 0,
    /** Also shown on the front, under the text in [language]; null for none. */
    val secondLanguage: String? = null,
    val score: Int = 0,
    /** Whether the score can become the stack's best: false when only some cards are played, e.g. the missed ones. */
    val countsForBest: Boolean = true,
)

data class SessionResult(
    val language: String,
    val mode: HintMode,
    val total: Int,
    val missed: List<String>,
    val secondLanguage: String? = null,
    val score: Int = 0,
    val countsForBest: Boolean = true,
) {
    val knownFirstTime: Int get() = total - missed.size
}

/** Points for a card known the first time it comes up. Fewer hints, more points. */
fun pointsPerCard(mode: HintMode): Int = when (mode) {
    HintMode.HINTED -> 10
    HintMode.LENGTH_ONLY -> 20
    HintMode.NONE -> 30
}

fun startSession(
    cardIds: List<String>,
    language: String,
    mode: HintMode,
    seed: Long,
    secondLanguage: String? = null,
    countsForBest: Boolean = true,
): Session =
    Session(
        language, mode, cardIds.shuffled(Random(seed)), cardIds.size, emptyList(), seed,
        secondLanguage = secondLanguage, countsForBest = countsForBest,
    )

fun Session.grade(knew: Boolean): Session {
    val current = queue.first()
    val rest = queue.drop(1)
    return if (knew) {
        val firstTime = current !in missed
        copy(queue = rest, step = step + 1, score = if (firstTime) score + pointsPerCard(mode) else score)
    } else {
        copy(
            queue = rest + current,
            missed = if (current in missed) missed else missed + current,
            step = step + 1,
        )
    }
}

val Session.isFinished: Boolean get() = queue.isEmpty()

fun Session.result(): SessionResult =
    SessionResult(language, mode, total, missed, secondLanguage, score, countsForBest)

fun Session.currentCard(stack: Stack): Card = stack.cards.first { it.id == queue.first() }

/** The answer slots for the current card's back, in the session's language. */
fun hintFor(stack: Stack, session: Session): List<Slot>? {
    val answer = session.currentCard(stack).back.resolveText(session.language, stack.languages.first())
    return hintSlots(answer, session.mode, Random(session.seed + session.step))
}

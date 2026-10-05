package dev.silas.flipcards.play

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.resolveText
import kotlin.random.Random

/**
 * One pass through a set of cards: every card comes up once, known or not. The missed ones can be
 * played again afterwards. Immutable: [grade] returns the next session.
 */
data class Session(
    val language: String,
    val mode: HintMode,
    /** Card ids still to be answered; the first is the current card. */
    val queue: List<String>,
    val total: Int,
    /** Cards marked "Didn't know", in the order they were missed. */
    val missed: List<String>,
    val seed: Long,
    /** Increases with every grade, so that each card gets its own hint. */
    val step: Int = 0,
    /** Also shown on the front, under the text in [language]; null for none. */
    val secondLanguage: String? = null,
    val score: Int = 0,
    /** Whether the score can become the stack's best: false when replaying only the missed cards. */
    val countsForBest: Boolean = true,
    /** How many cards were asked for; null for all. "Play again" asks for as many. */
    val cardCount: Int? = null,
    /** How many letters [HintMode.HINTED] shows. */
    val level: HintLevel = HintLevel.MEDIUM,
)

data class SessionResult(
    val language: String,
    val mode: HintMode,
    val total: Int,
    val missed: List<String>,
    val secondLanguage: String? = null,
    val score: Int = 0,
    val countsForBest: Boolean = true,
    val cardCount: Int? = null,
    val level: HintLevel = HintLevel.MEDIUM,
) {
    val known: Int get() = total - missed.size
}

/** Points for a known card. Fewer hints, more points. */
fun pointsPerCard(mode: HintMode, level: HintLevel = HintLevel.MEDIUM): Int = when (mode) {
    HintMode.HINTED -> when (level) {
        HintLevel.EASY -> 5
        HintLevel.MEDIUM -> 10
        HintLevel.HARD -> 15
    }
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
    /** Plays only this many of the cards, picked at random; null for all. */
    cardCount: Int? = null,
    level: HintLevel = HintLevel.MEDIUM,
): Session {
    val queue = cardIds.shuffled(Random(seed)).take(cardCount ?: cardIds.size)
    return Session(
        language, mode, queue, queue.size, emptyList(), seed,
        secondLanguage = secondLanguage, countsForBest = countsForBest, cardCount = cardCount, level = level,
    )
}

fun Session.grade(knew: Boolean): Session {
    val current = queue.first()
    val rest = queue.drop(1)
    return if (knew) {
        copy(queue = rest, step = step + 1, score = score + pointsPerCard(mode, level))
    } else {
        copy(queue = rest, missed = missed + current, step = step + 1)
    }
}

val Session.isFinished: Boolean get() = queue.isEmpty()

fun Session.result(): SessionResult =
    SessionResult(language, mode, total, missed, secondLanguage, score, countsForBest, cardCount, level)

fun Session.currentCard(stack: Stack): Card = stack.cards.first { it.id == queue.first() }

/** The answer slots for the current card's back, in the session's language. */
fun hintFor(stack: Stack, session: Session): List<Slot>? {
    val answer = session.currentCard(stack).back.resolveText(session.language, stack.languages.first())
    return hintSlots(answer, session.mode, Random(session.seed + session.step), session.level)
}

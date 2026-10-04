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
)

data class SessionResult(val language: String, val mode: HintMode, val total: Int, val missed: List<String>) {
    val knownFirstTime: Int get() = total - missed.size
}

fun startSession(cardIds: List<String>, language: String, mode: HintMode, seed: Long): Session =
    Session(language, mode, cardIds.shuffled(Random(seed)), cardIds.size, emptyList(), seed)

fun Session.grade(knew: Boolean): Session {
    val current = queue.first()
    val rest = queue.drop(1)
    return if (knew) {
        copy(queue = rest, step = step + 1)
    } else {
        copy(
            queue = rest + current,
            missed = if (current in missed) missed else missed + current,
            step = step + 1,
        )
    }
}

val Session.isFinished: Boolean get() = queue.isEmpty()

fun Session.result(): SessionResult = SessionResult(language, mode, total, missed)

fun Session.currentCard(stack: Stack): Card = stack.cards.first { it.id == queue.first() }

/** The hint for the current card's back, in the session's language. */
fun hintFor(stack: Stack, session: Session): String? {
    val answer = session.currentCard(stack).back.resolveText(session.language, stack.languages.first())
    return hint(answer, session.mode, Random(session.seed + session.step))
}

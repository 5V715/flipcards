package dev.silas.flipcards.state

import dev.silas.flipcards.model.completeCards
import dev.silas.flipcards.model.resolveText
import dev.silas.flipcards.play.HintMode
import dev.silas.flipcards.play.acceptTyped
import dev.silas.flipcards.play.currentCard
import dev.silas.flipcards.play.fill
import dev.silas.flipcards.play.grade
import dev.silas.flipcards.play.hintFor
import dev.silas.flipcards.play.isFinished
import dev.silas.flipcards.play.result
import dev.silas.flipcards.play.startSession
import dev.silas.flipcards.play.suggest

/**
 * Opens the play screen on its setup phase. The language is the one used last time with this stack,
 * else the interface language if the stack has it, else the stack's first language.
 */
internal fun playLoaded(state: AppState, action: PlayLoaded): AppState {
    if (state.route != Route.Play(action.stack.id)) return state
    val languages = action.stack.languages
    val language = action.storedLanguage?.takeIf { it in languages }
        ?: state.uiLanguage.takeIf { it in languages }
        ?: languages.first()
    return state.copy(
        screen = Screen.Play(
            action.stack,
            action.images,
            PlayPhase.Setup(language, HintMode.HINTED),
            action.bestScores,
        ),
    )
}

/** The best score for rounds of [cardCount] cards (null: all of them), if there is one. */
fun Screen.Play.bestFor(cardCount: Int?): Int? = bestScores[cardCount ?: stack.completeCards.size]

internal fun updatePlay(state: AppState, action: PlayAction): AppState {
    val play = state.screen as? Screen.Play ?: return state
    val stack = play.stack
    val phase = play.phase
    fun to(phase: PlayPhase) = state.copy(screen = play.copy(phase = phase))

    return when (action) {
        is PlayLanguageChosen -> {
            if (action.language !in stack.languages) return state
            // The interface follows the language the player picks.
            val picked = state.copy(uiLanguage = action.language)
            fun to(phase: PlayPhase) = picked.copy(screen = play.copy(phase = phase))
            when (phase) {
                is PlayPhase.Setup -> to(phase.copy(language = action.language))
                is PlayPhase.Asking -> {
                    val session = phase.session.copy(language = action.language)
                    val hint = hintFor(stack, session)
                    // The new answer may have fewer blanks than letters typed so far.
                    to(phase.copy(session = session, hint = hint, typed = hint?.acceptTyped(phase.typed) ?: phase.typed))
                }
                is PlayPhase.Revealed -> to(phase.copy(session = phase.session.copy(language = action.language)))
                is PlayPhase.Summary -> picked
            }
        }

        is HintModeChosen -> if (phase is PlayPhase.Setup) to(phase.copy(mode = action.mode)) else state

        is SecondLanguageChosen ->
            if (phase is PlayPhase.Setup && (action.language == null || action.language in stack.languages)) {
                to(phase.copy(secondLanguage = action.language))
            } else {
                state
            }

        is CardCountChosen -> {
            if (phase !is PlayPhase.Setup) return state
            val all = stack.completeCards.size
            val count = action.count?.coerceAtLeast(1)?.takeIf { it < all }
            to(phase.copy(cardCount = count))
        }

        is SessionStarted -> {
            val setup = when (phase) {
                is PlayPhase.Setup -> phase
                is PlayPhase.Summary -> with(phase.result) { PlayPhase.Setup(language, mode, secondLanguage, cardCount) }
                else -> return state
            }
            val cardIds = stack.completeCards.map { it.id }
                .filter { action.cardIds == null || it in action.cardIds }
            if (cardIds.isEmpty()) return state
            val replayingMissed = action.cardIds != null
            val session = startSession(
                cardIds, setup.language, setup.mode, action.seed,
                secondLanguage = setup.secondLanguage,
                countsForBest = !replayingMissed,
                cardCount = if (replayingMissed) null else setup.cardCount,
            )
            to(PlayPhase.Asking(session, hintFor(stack, session), typed = ""))
        }

        is AnswerTyped ->
            if (phase is PlayPhase.Asking) {
                to(phase.copy(typed = phase.hint?.acceptTyped(action.value) ?: action.value))
            } else {
                state
            }

        AnswerSubmitted -> {
            if (phase !is PlayPhase.Asking) return state
            val session = phase.session
            val answer = session.currentCard(stack).back.resolveText(session.language, stack.languages.first())
            // Nothing typed stays empty, so it is neither shown nor compared.
            val typed = if (phase.typed.isBlank()) "" else phase.hint?.fill(phase.typed) ?: phase.typed
            to(PlayPhase.Revealed(session, typed, suggest(typed, answer)))
        }

        is CardGraded -> {
            if (phase !is PlayPhase.Revealed) return state
            val session = phase.session.grade(action.knew)
            if (session.isFinished) {
                val result = session.result()
                // Best scores are kept per number of cards, so that short rounds compete with each other.
                val best = play.bestScores[result.total]
                val bestScores = if (result.countsForBest && (best == null || result.score > best)) {
                    play.bestScores + (result.total to result.score)
                } else {
                    play.bestScores
                }
                state.copy(screen = play.copy(phase = PlayPhase.Summary(result, previousBest = best), bestScores = bestScores))
            } else {
                to(PlayPhase.Asking(session, hintFor(stack, session), typed = ""))
            }
        }
    }
}

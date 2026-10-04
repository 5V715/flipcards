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

/** Opens the play screen on its setup phase, preferring the language used last time. */
internal fun playLoaded(state: AppState, action: PlayLoaded): AppState {
    if (state.route != Route.Play(action.stack.id)) return state
    val language = action.storedLanguage?.takeIf { it in action.stack.languages }
        ?: action.stack.languages.first()
    return state.copy(
        screen = Screen.Play(
            action.stack,
            action.images,
            PlayPhase.Setup(language, HintMode.HINTED),
            action.bestScore,
        ),
    )
}

internal fun updatePlay(state: AppState, action: PlayAction): AppState {
    val play = state.screen as? Screen.Play ?: return state
    val stack = play.stack
    val phase = play.phase
    fun to(phase: PlayPhase) = state.copy(screen = play.copy(phase = phase))

    return when (action) {
        is PlayLanguageChosen -> {
            if (action.language !in stack.languages) return state
            when (phase) {
                is PlayPhase.Setup -> to(phase.copy(language = action.language))
                is PlayPhase.Asking -> {
                    val session = phase.session.copy(language = action.language)
                    val hint = hintFor(stack, session)
                    // The new answer may have fewer blanks than letters typed so far.
                    to(phase.copy(session = session, hint = hint, typed = hint?.acceptTyped(phase.typed) ?: phase.typed))
                }
                is PlayPhase.Revealed -> to(phase.copy(session = phase.session.copy(language = action.language)))
                is PlayPhase.Summary -> state
            }
        }

        is HintModeChosen -> if (phase is PlayPhase.Setup) to(phase.copy(mode = action.mode)) else state

        is SecondLanguageChosen ->
            if (phase is PlayPhase.Setup && (action.language == null || action.language in stack.languages)) {
                to(phase.copy(secondLanguage = action.language))
            } else {
                state
            }

        is SessionStarted -> {
            val (language, mode, secondLanguage) = when (phase) {
                is PlayPhase.Setup -> Triple(phase.language, phase.mode, phase.secondLanguage)
                is PlayPhase.Summary -> Triple(phase.result.language, phase.result.mode, phase.result.secondLanguage)
                else -> return state
            }
            val cardIds = stack.completeCards.map { it.id }
                .filter { action.cardIds == null || it in action.cardIds }
            if (cardIds.isEmpty()) return state
            val session = startSession(
                cardIds, language, mode, action.seed,
                secondLanguage = secondLanguage,
                countsForBest = action.cardIds == null,
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
                val best = play.bestScore
                val newBest = if (result.countsForBest && (best == null || result.score > best)) result.score else best
                state.copy(screen = play.copy(phase = PlayPhase.Summary(result, previousBest = best), bestScore = newBest))
            } else {
                to(PlayPhase.Asking(session, hintFor(stack, session), typed = ""))
            }
        }
    }
}

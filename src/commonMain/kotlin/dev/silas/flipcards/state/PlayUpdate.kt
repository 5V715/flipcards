package dev.silas.flipcards.state

import dev.silas.flipcards.model.completeCards
import dev.silas.flipcards.model.resolveText
import dev.silas.flipcards.play.HintMode
import dev.silas.flipcards.play.currentCard
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
        screen = Screen.Play(action.stack, action.images, PlayPhase.Setup(language, HintMode.HINTED)),
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
                    to(phase.copy(session = session, hint = hintFor(stack, session)))
                }
                is PlayPhase.Revealed -> to(phase.copy(session = phase.session.copy(language = action.language)))
                is PlayPhase.Summary -> state
            }
        }

        is HintModeChosen -> if (phase is PlayPhase.Setup) to(phase.copy(mode = action.mode)) else state

        is SessionStarted -> {
            val (language, mode) = when (phase) {
                is PlayPhase.Setup -> phase.language to phase.mode
                is PlayPhase.Summary -> phase.result.language to phase.result.mode
                else -> return state
            }
            val cardIds = stack.completeCards.map { it.id }
                .filter { action.cardIds == null || it in action.cardIds }
            if (cardIds.isEmpty()) return state
            val session = startSession(cardIds, language, mode, action.seed)
            to(PlayPhase.Asking(session, hintFor(stack, session), typed = ""))
        }

        is AnswerTyped -> if (phase is PlayPhase.Asking) to(phase.copy(typed = action.value)) else state

        AnswerSubmitted -> {
            if (phase !is PlayPhase.Asking) return state
            val session = phase.session
            val answer = session.currentCard(stack).back.resolveText(session.language, stack.languages.first())
            to(PlayPhase.Revealed(session, phase.typed, suggest(phase.typed, answer)))
        }

        is CardGraded -> {
            if (phase !is PlayPhase.Revealed) return state
            val session = phase.session.grade(action.knew)
            if (session.isFinished) to(PlayPhase.Summary(session.result()))
            else to(PlayPhase.Asking(session, hintFor(stack, session), typed = ""))
        }
    }
}

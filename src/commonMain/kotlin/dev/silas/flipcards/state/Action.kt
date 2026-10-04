package dev.silas.flipcards.state

import dev.silas.flipcards.model.Face
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.play.HintMode

/** Something that happened: a click, a keystroke, or the result of asynchronous work. */
sealed interface Action {
    /** Silent actions change the state without re-rendering, so typing keeps focus and cursor. */
    val silent: Boolean get() = false
}

// Navigation and loading

data class Navigate(val route: Route) : Action
data class StackListLoaded(val stacks: List<StackSummary>) : Action
data class EditorLoaded(val stack: Stack, val images: Map<String, String>) : Action
data class PlayLoaded(val stack: Stack, val images: Map<String, String>, val storedLanguage: String?) : Action
data class StackMissing(val stackId: String) : Action
data class ErrorRaised(val message: String) : Action
data object ErrorDismissed : Action

// Stack list. These change no state; they only trigger effects.

data object NewStackRequested : Action
data class DeleteStackConfirmed(val stackId: String) : Action
data class ExportRequested(val stackId: String) : Action
data class ImportFileRead(val text: String) : Action

// Editor

sealed interface EditorAction : Action

data class StackRenamed(val name: String) : EditorAction {
    override val silent get() = true
}
data class LanguageAdded(val code: String) : EditorAction
data class LanguageRemoved(val code: String) : EditorAction
data class CardAdded(val cardId: String) : EditorAction
data class CardDeleted(val cardId: String) : EditorAction

/** [delta] is -1 to move the card up, +1 to move it down. */
data class CardMoved(val cardId: String, val delta: Int) : EditorAction
data class SideTextModeChanged(val cardId: String, val face: Face, val translated: Boolean) : EditorAction

/** [language] is null for a text that is the same in all languages. */
data class SideTextChanged(val cardId: String, val face: Face, val language: String?, val value: String) :
    EditorAction {
    override val silent get() = true
}
data class ImageChosen(val cardId: String, val face: Face, val imageId: String, val dataUrl: String) : EditorAction
data class ImageRemoved(val cardId: String, val face: Face) : EditorAction
data class ImageRejected(val cardId: String, val face: Face) : EditorAction
data class ImageSaveFailed(val cardId: String, val face: Face, val imageId: String) : EditorAction
data class StackSaved(val stack: Stack) : EditorAction
data class StackSaveFailed(val message: String) : EditorAction

// Play

sealed interface PlayAction : Action

data class PlayLanguageChosen(val language: String) : PlayAction
data class HintModeChosen(val mode: HintMode) : PlayAction

/** [cardIds] limits the session to those cards; null means all complete cards. */
data class SessionStarted(val seed: Long, val cardIds: List<String>? = null) : PlayAction
data class AnswerTyped(val value: String) : PlayAction {
    override val silent get() = true
}
data object AnswerSubmitted : PlayAction
data class CardGraded(val knew: Boolean) : PlayAction

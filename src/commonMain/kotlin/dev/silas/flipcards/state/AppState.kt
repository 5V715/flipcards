package dev.silas.flipcards.state

import dev.silas.flipcards.model.Face
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.play.HintMode
import dev.silas.flipcards.play.Session
import dev.silas.flipcards.play.SessionResult

/** Everything the UI shows. The screens are pure functions of this. */
data class AppState(
    val route: Route = Route.Home,
    val screen: Screen = Screen.Loading,
    /** Shown as a dismissible banner above the current screen. */
    val error: String? = null,
)

/** One side of one card. */
data class SideRef(val cardId: String, val face: Face)

sealed interface Screen {
    data object Loading : Screen

    data class StackList(val stacks: List<StackSummary>) : Screen

    data class Editor(
        val stack: Stack,
        /** Image id to data URL, for the images of this stack. */
        val images: Map<String, String>,
        val saved: Boolean,
        /** The side whose last chosen file was not a readable image. */
        val imageError: SideRef? = null,
    ) : Screen

    data class Play(val stack: Stack, val images: Map<String, String>, val phase: PlayPhase) : Screen

    data class NotFound(val message: String) : Screen
}

sealed interface PlayPhase {
    data class Setup(val language: String, val mode: HintMode) : PlayPhase
    data class Asking(val session: Session, val hint: String?, val typed: String) : PlayPhase
    data class Revealed(val session: Session, val typed: String, val suggestion: Boolean?) : PlayPhase
    data class Summary(val result: SessionResult) : PlayPhase
}

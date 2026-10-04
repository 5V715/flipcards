package dev.silas.flipcards.state

import dev.silas.flipcards.model.Face
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.play.HintMode
import dev.silas.flipcards.play.Session
import dev.silas.flipcards.play.SessionResult
import dev.silas.flipcards.play.Slot
import dev.silas.flipcards.samples.Sample

/** Everything the UI shows. The screens are pure functions of this. */
data class AppState(
    val route: Route = Route.Home,
    val screen: Screen = Screen.Loading,
    /** Shown as a dismissible banner above the current screen. */
    val error: String? = null,
    /** The language code the interface is shown in: the language the player picked last. */
    val uiLanguage: String = "en",
)

/** One side of one card. */
data class SideRef(val cardId: String, val face: Face)

sealed interface Screen {
    data object Loading : Screen

    /** [samples] is the list of sample stacks, while it is open. */
    data class StackList(val stacks: List<StackSummary>, val samples: SamplesPanel? = null) : Screen

    data class Editor(
        val stack: Stack,
        /** Image id to data URL, for the images of this stack. */
        val images: Map<String, String>,
        val saved: Boolean,
        /** The side whose last chosen file was not a readable image. */
        val imageError: SideRef? = null,
    ) : Screen

    data class Play(
        val stack: Stack,
        val images: Map<String, String>,
        val phase: PlayPhase,
        /** The highest score per number of cards played, for this stack. */
        val bestScores: Map<Int, Int> = emptyMap(),
    ) : Screen

    data class NotFound(val message: String) : Screen
}

sealed interface SamplesPanel {
    data object Loading : SamplesPanel

    /** [adding] and [added] hold file names: samples being downloaded, and samples added since the list opened. */
    data class Loaded(
        val samples: List<Sample>,
        val adding: Set<String> = emptySet(),
        val added: Set<String> = emptySet(),
    ) : SamplesPanel
}

sealed interface PlayPhase {
    /** [cardCount] is how many cards to play; null for all. */
    data class Setup(
        val language: String,
        val mode: HintMode,
        val secondLanguage: String? = null,
        val cardCount: Int? = null,
    ) : PlayPhase

    /**
     * [hint] holds the slots the player types into; null when there are none (no hints, or an image answer).
     * With slots, [typed] holds only the letters for the blanks; without, the whole answer.
     */
    data class Asking(val session: Session, val hint: List<Slot>?, val typed: String) : PlayPhase

    /** [typed] is the whole answer as the player entered it, with the slots filled in. */
    data class Revealed(val session: Session, val typed: String, val suggestion: Boolean?) : PlayPhase

    /** [previousBest] is the best score for this number of cards before this session, to tell whether it was beaten. */
    data class Summary(val result: SessionResult, val previousBest: Int? = null) : PlayPhase
}

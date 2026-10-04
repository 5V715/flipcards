package dev.silas.flipcards.state

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.play.HintMode
import dev.silas.flipcards.play.SessionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayUpdateTest {
    private val stack = Stack(
        "s1", "S", listOf("en", "de"),
        listOf(
            Card("a", Side(imageId = "i"), Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien")))),
            Card("b", Side(SideText.Same("Spain")), Side(SideText.Same("Madrid"))),
            Card("c", Side(SideText.Same("?")), Side(imageId = "j")),
            Card("d"),
        ),
    )
    private val images = mapOf("i" to "data:image/jpeg;base64,AA", "j" to "data:image/jpeg;base64,BB")

    private fun setup(language: String = "en", mode: HintMode = HintMode.LENGTH_ONLY) =
        AppState(Route.Play("s1"), Screen.Play(stack, images, PlayPhase.Setup(language, mode)))

    private val AppState.phase get() = (screen as Screen.Play).phase

    @Test fun loadedUsesStoredLanguage() =
        assertEquals(
            PlayPhase.Setup("de", HintMode.HINTED),
            update(AppState(Route.Play("s1")), PlayLoaded(stack, images, "de")).phase,
        )

    @Test fun loadedFallsBackWhenStoredLanguageIsGone() { // Review Focus 2
        assertEquals(
            PlayPhase.Setup("en", HintMode.HINTED),
            update(AppState(Route.Play("s1")), PlayLoaded(stack, images, "fr")).phase,
        )
        assertEquals(
            PlayPhase.Setup("en", HintMode.HINTED),
            update(AppState(Route.Play("s1")), PlayLoaded(stack, images, null)).phase,
        )
    }

    @Test fun stalePlayLoadIgnored() {
        val home = AppState(Route.Home, Screen.StackList(emptyList()))
        assertEquals(home, update(home, PlayLoaded(stack, images, null)))
    }

    @Test fun setupChoices() {
        assertEquals(PlayPhase.Setup("de", HintMode.LENGTH_ONLY), update(setup(), PlayLanguageChosen("de")).phase)
        assertEquals(setup(), update(setup(), PlayLanguageChosen("fr")))
        assertEquals(PlayPhase.Setup("en", HintMode.NONE), update(setup(), HintModeChosen(HintMode.NONE)).phase)
    }

    @Test fun startSkipsIncompleteCards() {
        val asking = update(setup(), SessionStarted(1)).phase as PlayPhase.Asking
        assertEquals(setOf("a", "b", "c"), asking.session.queue.toSet())
        assertEquals(3, asking.session.total)
        assertEquals("", asking.typed)
    }

    @Test fun startWithNoCompleteCardsDoesNothing() {
        val empty = AppState(
            Route.Play("s1"),
            Screen.Play(stack.copy(cards = emptyList()), emptyMap(), PlayPhase.Setup("en", HintMode.NONE)),
        )
        assertEquals(empty, update(empty, SessionStarted(1)))
    }

    @Test fun typeSubmitAndGrade() {
        var s = update(setup(), SessionStarted(1, listOf("b")))
        assertEquals("_ _ _ _ _ _", (s.phase as PlayPhase.Asking).hint)
        s = update(s, AnswerTyped("madrid"))
        s = update(s, AnswerSubmitted)
        assertEquals(true, (s.phase as PlayPhase.Revealed).suggestion)
        assertEquals("madrid", (s.phase as PlayPhase.Revealed).typed)
        s = update(s, CardGraded(true))
        assertEquals(SessionResult("en", HintMode.LENGTH_ONLY, 1, emptyList()), (s.phase as PlayPhase.Summary).result)
    }

    @Test fun wrongAndEmptyAnswers() {
        val asked = update(setup(), SessionStarted(1, listOf("b")))
        assertEquals(
            false,
            (update(update(asked, AnswerTyped("Lisbon")), AnswerSubmitted).phase as PlayPhase.Revealed).suggestion,
        )
        assertNull((update(asked, AnswerSubmitted).phase as PlayPhase.Revealed).suggestion)
    }

    @Test fun imageOnlyBackHasNoHintAndNoSuggestion() {
        val asked = update(setup(), SessionStarted(1, listOf("c")))
        assertNull((asked.phase as PlayPhase.Asking).hint)
        assertNull((update(update(asked, AnswerTyped("x")), AnswerSubmitted).phase as PlayPhase.Revealed).suggestion)
    }

    @Test fun missedCardComesBack() {
        var s = update(setup(), SessionStarted(1, listOf("b")))
        s = update(update(s, AnswerSubmitted), CardGraded(false))
        val again = s.phase as PlayPhase.Asking
        assertEquals(listOf("b"), again.session.queue)
        assertEquals("", again.typed)
        s = update(update(s, AnswerSubmitted), CardGraded(true))
        assertEquals(listOf("b"), (s.phase as PlayPhase.Summary).result.missed)
    }

    @Test fun switchLanguageMidSession() {
        var s = update(setup(), SessionStarted(1, listOf("a")))
        assertEquals("_ _ _ _ _ _", (s.phase as PlayPhase.Asking).hint)
        s = update(update(s, AnswerTyped("wi")), PlayLanguageChosen("de"))
        val asking = s.phase as PlayPhase.Asking
        assertEquals("_ _ _ _", asking.hint)
        assertEquals("de", asking.session.language)
        assertEquals("wi", asking.typed)
        s = update(update(s, AnswerTyped("wien")), AnswerSubmitted)
        assertEquals(true, (s.phase as PlayPhase.Revealed).suggestion)
    }

    @Test fun replayFromSummary() {
        var s = update(setup("de", HintMode.NONE), SessionStarted(1, listOf("a", "b")))
        repeat(2) { s = update(update(s, AnswerSubmitted), CardGraded(it == 0)) } // first known, second missed
        s = update(update(s, AnswerSubmitted), CardGraded(true))
        val missed = (s.phase as PlayPhase.Summary).result.missed
        assertEquals(1, missed.size)
        val again = update(s, SessionStarted(2, missed)).phase as PlayPhase.Asking
        assertEquals(missed, again.session.queue)
        assertEquals("de", again.session.language)
        assertEquals(HintMode.NONE, again.session.mode)
        // "Play again" plays every complete card of the stack (a, b and c), not only the previous subset.
        assertEquals(3, (update(s, SessionStarted(2)).phase as PlayPhase.Asking).session.total)
    }
}

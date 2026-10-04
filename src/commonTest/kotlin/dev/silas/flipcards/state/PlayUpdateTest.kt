package dev.silas.flipcards.state

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.play.HintMode
import dev.silas.flipcards.play.SessionResult
import dev.silas.flipcards.play.pattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals("_ _ _ _ _ _", (s.phase as PlayPhase.Asking).hint?.pattern())
        s = update(s, AnswerTyped("madrid"))
        s = update(s, AnswerSubmitted)
        assertEquals(true, (s.phase as PlayPhase.Revealed).suggestion)
        assertEquals("madrid", (s.phase as PlayPhase.Revealed).typed)
        s = update(s, CardGraded(true))
        assertEquals(
            SessionResult("en", HintMode.LENGTH_ONLY, 1, emptyList(), score = 20, countsForBest = false),
            (s.phase as PlayPhase.Summary).result,
        )
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

    @Test fun aMissedCardEndsTheRoundWhenItIsTheLast() {
        var s = update(setup(), SessionStarted(1, listOf("b")))
        s = update(update(s, AnswerSubmitted), CardGraded(false))
        assertEquals(listOf("b"), (s.phase as PlayPhase.Summary).result.missed)
    }

    @Test fun switchLanguageMidSession() {
        var s = update(setup(), SessionStarted(1, listOf("a")))
        assertEquals("_ _ _ _ _ _", (s.phase as PlayPhase.Asking).hint?.pattern())
        s = update(update(s, AnswerTyped("wi")), PlayLanguageChosen("de"))
        val asking = s.phase as PlayPhase.Asking
        assertEquals("_ _ _ _", asking.hint?.pattern())
        assertEquals("de", asking.session.language)
        assertEquals("wi", asking.typed)
        s = update(update(s, AnswerTyped("wien")), AnswerSubmitted)
        assertEquals(true, (s.phase as PlayPhase.Revealed).suggestion)
    }

    @Test fun replayFromSummary() {
        var s = update(setup("de", HintMode.NONE), SessionStarted(1, listOf("a", "b")))
        repeat(2) { s = update(update(s, AnswerSubmitted), CardGraded(it == 0)) } // first known, second missed
        val missed = (s.phase as PlayPhase.Summary).result.missed
        assertEquals(1, missed.size)
        val again = update(s, SessionStarted(2, missed)).phase as PlayPhase.Asking
        assertEquals(missed, again.session.queue)
        assertEquals("de", again.session.language)
        assertEquals(HintMode.NONE, again.session.mode)
        // "Play again" plays every complete card of the stack (a, b and c), not only the previous subset.
        assertEquals(3, (update(s, SessionStarted(2)).phase as PlayPhase.Asking).session.total)
    }

    @Test fun loadedKeepsTheBestScores() =
        assertEquals(
            mapOf(3 to 150),
            (update(AppState(Route.Play("s1")), PlayLoaded(stack, images, null, mapOf(3 to 150))).screen as Screen.Play)
                .bestScores,
        )

    @Test fun loadedPrefersTheInterfaceLanguageOverTheFirst() {
        val german = AppState(Route.Play("s1"), uiLanguage = "de")
        assertEquals("de", (update(german, PlayLoaded(stack, images, null)).phase as PlayPhase.Setup).language)
        assertEquals("en", (update(german, PlayLoaded(stack, images, "en")).phase as PlayPhase.Setup).language)
    }

    @Test fun pickingAPlayLanguageChangesTheInterfaceLanguage() {
        assertEquals("de", update(setup(), PlayLanguageChosen("de")).uiLanguage)
        assertEquals("en", update(setup(), PlayLanguageChosen("fr")).uiLanguage) // not in the stack
        val asking = update(setup(), SessionStarted(1))
        assertEquals("de", update(asking, PlayLanguageChosen("de")).uiLanguage)
    }

    @Test fun cardCountLimitsTheSession() {
        var s = update(setup(), CardCountChosen(2))
        assertEquals(2, (s.phase as PlayPhase.Setup).cardCount)
        s = update(s, SessionStarted(1))
        val session = (s.phase as PlayPhase.Asking).session
        assertEquals(2, session.total)
        assertEquals(2, session.queue.toSet().size)
        assertTrue(session.queue.all { it in setOf("a", "b", "c") })
    }

    @Test fun cardCountOfAllOrMoreMeansAll() {
        assertNull((update(setup(), CardCountChosen(3)).phase as PlayPhase.Setup).cardCount)
        assertNull((update(setup(), CardCountChosen(99)).phase as PlayPhase.Setup).cardCount)
        assertNull((update(setup(), CardCountChosen(null)).phase as PlayPhase.Setup).cardCount)
        assertEquals(1, (update(setup(), CardCountChosen(0)).phase as PlayPhase.Setup).cardCount)
    }

    @Test fun playAgainKeepsTheCardCountButMissedOnlyPlaysAllMissed() {
        var s = playThrough(update(update(setup(), CardCountChosen(2)), SessionStarted(1)), knew = false)
        val summary = s.phase as PlayPhase.Summary
        assertEquals(2, summary.result.missed.size)
        assertEquals(2, (update(s, SessionStarted(2)).phase as PlayPhase.Asking).session.total)
        s = update(s, SessionStarted(3, summary.result.missed))
        assertEquals(2, (s.phase as PlayPhase.Asking).session.total)
        assertNull((s.phase as PlayPhase.Asking).session.cardCount)
    }

    @Test fun secondLanguageIsChosenInSetupAndKeptForReplays() {
        var s = update(setup(), SecondLanguageChosen("de"))
        assertEquals("de", (s.phase as PlayPhase.Setup).secondLanguage)
        assertEquals(s, update(s, SecondLanguageChosen("fr")))
        s = update(s, SessionStarted(1, listOf("b")))
        assertEquals("de", (s.phase as PlayPhase.Asking).session.secondLanguage)
        s = update(update(s, AnswerSubmitted), CardGraded(true))
        assertEquals("de", (update(s, SessionStarted(2)).phase as PlayPhase.Asking).session.secondLanguage)
        assertNull((update(setup(), SecondLanguageChosen(null)).phase as PlayPhase.Setup).secondLanguage)
    }

    @Test fun typingGoesIntoTheBlanks() {
        var s = update(setup(), SessionStarted(1, listOf("b"))) // Madrid, length only
        s = update(s, AnswerTyped("ma dridxx"))
        assertEquals("madrid", (s.phase as PlayPhase.Asking).typed)
        s = update(update(s, AnswerTyped("mad")), AnswerSubmitted)
        val revealed = s.phase as PlayPhase.Revealed
        assertEquals("mad___", revealed.typed)
        assertEquals(false, revealed.suggestion)
    }

    @Test fun withoutHintsTheAnswerIsTypedFreely() {
        var s = update(setup(mode = HintMode.NONE), SessionStarted(1, listOf("b")))
        assertNull((s.phase as PlayPhase.Asking).hint)
        s = update(update(s, AnswerTyped("Madrid ")), AnswerSubmitted)
        assertEquals(true, (s.phase as PlayPhase.Revealed).suggestion)
    }

    /** Plays to the summary, marking every card as known or every card as missed. */
    private fun playThrough(state: AppState, knew: Boolean): AppState {
        var s = state
        while (s.phase is PlayPhase.Asking) s = update(update(s, AnswerSubmitted), CardGraded(knew))
        return s
    }

    @Test fun aFullRoundSetsTheBestScore() {
        val first = playThrough(update(setup(), SessionStarted(1)), knew = true) // 3 cards × 20
        assertEquals(mapOf(3 to 60), (first.screen as Screen.Play).bestScores)
        assertNull((first.phase as PlayPhase.Summary).previousBest)

        val worse = playThrough(update(first, SessionStarted(2)), knew = false)
        assertEquals(0, (worse.phase as PlayPhase.Summary).result.score)
        assertEquals(60, (worse.phase as PlayPhase.Summary).previousBest)
        assertEquals(mapOf(3 to 60), (worse.screen as Screen.Play).bestScores)
    }

    @Test fun shorterRoundsHaveTheirOwnBestScore() {
        val full = playThrough(update(setup(), SessionStarted(1)), knew = true)
        val oneCard = PlayPhase.Setup("en", HintMode.LENGTH_ONLY, cardCount = 1)
        val backInSetup = full.copy(screen = (full.screen as Screen.Play).copy(phase = oneCard))
        val done = playThrough(update(backInSetup, SessionStarted(2)), knew = true)
        assertNull((done.phase as PlayPhase.Summary).previousBest)
        assertEquals(mapOf(3 to 60, 1 to 20), (done.screen as Screen.Play).bestScores)
        assertEquals(20, (done.screen as Screen.Play).bestFor(1))
        assertEquals(60, (done.screen as Screen.Play).bestFor(null))
    }

    @Test fun aRoundWithSomeCardsDoesNotSetTheBestScore() {
        val s = playThrough(update(setup(), SessionStarted(1, listOf("b"))), knew = true)
        assertEquals(20, (s.phase as PlayPhase.Summary).result.score)
        assertEquals(emptyMap(), (s.screen as Screen.Play).bestScores)
    }
}

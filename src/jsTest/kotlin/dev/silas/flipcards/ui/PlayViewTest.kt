package dev.silas.flipcards.ui

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.play.HintMode
import dev.silas.flipcards.play.SessionResult
import dev.silas.flipcards.play.Slot
import dev.silas.flipcards.play.hintSlots
import dev.silas.flipcards.play.startSession
import dev.silas.flipcards.state.Action
import dev.silas.flipcards.state.AnswerSubmitted
import dev.silas.flipcards.state.AnswerTyped
import dev.silas.flipcards.state.AppState
import dev.silas.flipcards.state.CardGraded
import dev.silas.flipcards.state.HintModeChosen
import dev.silas.flipcards.state.PlayLanguageChosen
import dev.silas.flipcards.state.PlayPhase
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.SecondLanguageChosen
import dev.silas.flipcards.state.SessionStarted
import kotlinx.browser.document
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import kotlinx.coroutines.MainScope
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayViewTest {
    private val flag = "data:image/jpeg;base64,FLAG"
    private val map = "data:image/jpeg;base64,MAP"
    private val stack = Stack(
        "s1", "Capitals", listOf("en", "de"),
        listOf(
            Card("a", Side(imageId = "i"), Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien")))),
            Card("b", Side(SideText.Same("Spain")), Side(SideText.Same("Madrid"))),
            Card("c", Side(SideText.Same("?")), Side(imageId = "j")),
        ),
    )

    private fun play(phase: PlayPhase, stack: Stack = this.stack, bestScore: Int? = null) =
        mount(AppState(Route.Play(stack.id), Screen.Play(stack, mapOf("i" to flag, "j" to map), phase, bestScore)))

    private fun session(cardId: String, language: String = "en", secondLanguage: String? = null) =
        startSession(listOf(cardId), language, HintMode.LENGTH_ONLY, 1, secondLanguage)

    private fun asking(
        cardId: String,
        hint: List<Slot>? = null,
        typed: String = "",
        language: String = "en",
        secondLanguage: String? = null,
    ) = play(PlayPhase.Asking(session(cardId, language, secondLanguage), hint, typed))

    private val Mounted.answerInput get() = one("#$ANSWER_INPUT_ID") as HTMLInputElement

    private val Mounted.slots get() = all(".answer-slots .slot").map { it.textContent ?: "" }

    private fun revealed(cardId: String, typed: String, suggestion: Boolean?, language: String = "en") =
        play(PlayPhase.Revealed(session(cardId, language), typed, suggestion))

    private fun Mounted.chooseLanguage(code: String) {
        val select = field("Language") as HTMLSelectElement
        select.value = code
        select.dispatchEvent(Event("change", EventInit(bubbles = true)))
    }

    // Setup

    @Test fun setupShowsTheCurrentChoices() {
        val page = play(PlayPhase.Setup("de", HintMode.LENGTH_ONLY))
        assertEquals("Capitals", page.one("h1").textContent)
        val select = page.field("Language") as HTMLSelectElement
        assertEquals(listOf("en", "de"), select.options.asList().map { it.textContent })
        assertEquals("de", select.value)
        assertFalse((page.field("Hinted") as HTMLInputElement).checked)
        assertTrue((page.field("Length only") as HTMLInputElement).checked)
        assertFalse((page.field("No hint") as HTMLInputElement).checked)
        assertTrue(page.exists("a[href='#/']"))
        assertEquals("", (page.field("Also show on the front") as HTMLSelectElement).value)
        assertFalse(page.exists(".best"))
    }

    @Test fun setupShowsTheBestScore() =
        assertEquals("Best score: 120", play(PlayPhase.Setup("en", HintMode.HINTED), bestScore = 120).one(".best").textContent)

    @Test fun secondLanguageCanBeChosenAndCleared() {
        val page = play(PlayPhase.Setup("en", HintMode.HINTED, secondLanguage = "de"))
        val select = page.field("Also show on the front") as HTMLSelectElement
        assertEquals("de", select.value)
        select.value = ""
        select.dispatchEvent(Event("change", EventInit(bubbles = true)))
        assertEquals(listOf<Action>(SecondLanguageChosen(null)), page.dispatched)
    }

    @Test fun setupChoicesDispatch() {
        val page = play(PlayPhase.Setup("en", HintMode.HINTED))
        page.chooseLanguage("de")
        page.field("No hint").click()
        assertEquals(listOf<Action>(PlayLanguageChosen("de"), HintModeChosen(HintMode.NONE)), page.dispatched)
    }

    @Test fun startBeginsASessionWithAllCards() {
        val page = play(PlayPhase.Setup("en", HintMode.HINTED))
        assertEquals(page.button("Start"), document.activeElement)
        page.button("Start").click()
        assertNull((page.dispatched.single() as SessionStarted).cardIds)
    }

    @Test fun setupWithoutCompleteCardsPointsToTheEditor() {
        val page = play(PlayPhase.Setup("en", HintMode.HINTED), stack.copy(cards = listOf(Card("x"))))
        assertFalse(page.hasButton("Start"))
        assertTrue("This stack has no complete cards yet." in page.text)
        assertEquals("Open the editor", page.one("a[href='#/stack/s1/edit']").textContent)
    }

    // Asking

    @Test fun askingShowsFrontAndTypedLettersInTheBlanks() {
        val madrid = hintSlots("Madrid", HintMode.LENGTH_ONLY, Random(1))
        val page = asking("b", hint = madrid, typed = "ma")
        assertTrue("1 of 1 left" in page.text)
        assertTrue("Score 0" in page.text)
        assertEquals("Spain", page.one(".flipcard").textContent)
        assertFalse("Madrid" in page.text)
        assertEquals(listOf("m", "a", "", "", "", ""), page.slots)
        assertEquals(2, page.all(".slot.filled").size)
        assertEquals(2, page.all(".slot").indexOf(page.one(".slot.current")))
        assertEquals("ma", page.answerInput.value)
        assertEquals("6", page.answerInput.getAttribute("maxlength"))
        assertEquals(page.answerInput, document.activeElement)
    }

    @Test fun revealedLettersAndSpacesStayInPlace() {
        val slots = listOf(Slot.Fixed('N'), Slot.Blank, Slot.Fixed(' '), Slot.Blank)
        val page = asking("b", hint = slots, typed = "e")
        assertEquals(listOf("N", "e", "", ""), page.slots)
        assertTrue(page.exists(".slot.gap"))
        assertEquals(page.all(".slot")[3], page.one(".slot.current"))
    }

    @Test fun withoutHintsTheTypedTextShowsOnOneLine() {
        val page = asking("b", hint = null, typed = "Mad")
        assertEquals("Mad", page.one(".answer-slots.free .typed").textContent)
        assertFalse(page.exists(".slot"))
        assertNull(page.answerInput.getAttribute("maxlength"))
    }

    @Test fun typingAndSubmitting() {
        val page = asking("b", hint = hintSlots("Madrid", HintMode.LENGTH_ONLY, Random(1)))
        page.type(page.answerInput, "madrid")
        page.button("Show answer").click()
        assertEquals(listOf<Action>(AnswerTyped("madrid"), AnswerSubmitted), page.dispatched)
    }

    @Test fun patchRedrawsTheSlotsWithoutLosingFocus() {
        val slots = hintSlots("Madrid", HintMode.LENGTH_ONLY, Random(1))
        val page = asking("b", hint = slots)
        val input = page.answerInput
        input.value = "m d"
        patchPlay(Screen.Play(stack, emptyMap(), PlayPhase.Asking(session("b"), slots, "md")))
        assertEquals(listOf("m", "d", "", "", "", ""), page.slots)
        assertEquals("md", input.value)
        assertEquals(input, document.activeElement)
    }

    @Test fun frontCanShowASecondLanguage() {
        val translatedFront = stack.copy(
            cards = listOf(
                Card("t", Side(SideText.Translated(mapOf("en" to "hello", "de" to "hallo"))), Side(SideText.Same("hola"))),
            ),
        )
        val page = play(PlayPhase.Asking(session("t", secondLanguage = "de"), null, ""), translatedFront)
        assertEquals("hello", page.one(".side-text:not(.second)").textContent)
        assertEquals("hallo", page.one(".side-text.second").textContent)
    }

    @Test fun secondLanguageIsNotRepeatedWhenTheTextIsTheSame() {
        val page = asking("b", secondLanguage = "de") // "Spain" in every language
        assertEquals(1, page.all(".side-text").size)
    }

    @Test fun askingShowsAnImageFront() {
        val page = asking("a")
        assertEquals(flag, page.one(".flipcard img.side-image").getAttribute("src"))
        assertFalse(page.exists(".slot"))
    }

    @Test fun imageOnlyBackHasNoAnswerInput() {
        val page = asking("c")
        assertFalse(page.exists("#$ANSWER_INPUT_ID"))
        assertEquals(page.button("Show answer"), document.activeElement)
        page.button("Show answer").click()
        assertEquals(listOf<Action>(AnswerSubmitted), page.dispatched)
    }

    @Test fun languageCanBeSwitchedWhileAsking() {
        val page = asking("a")
        page.chooseLanguage("de")
        assertEquals(listOf<Action>(PlayLanguageChosen("de")), page.dispatched)
    }

    // Revealed

    @Test fun revealedRightAnswer() {
        val page = revealed("b", typed = "madrid", suggestion = true)
        assertEquals("Madrid", page.one(".flipcard.revealed").textContent)
        assertTrue("Your answer: madrid" in page.text)
        assertTrue("Looks right" in page.text)
        assertEquals(page.button("Knew it"), document.activeElement)
        page.button("Knew it").click()
        page.button("Didn't know").click()
        assertEquals(listOf<Action>(CardGraded(true), CardGraded(false)), page.dispatched)
    }

    @Test fun revealedDifferentAnswer() {
        val page = revealed("b", typed = "Lisbon", suggestion = false)
        assertTrue("Looks different" in page.text)
        assertEquals(page.button("Didn't know"), document.activeElement)
    }

    @Test fun revealedWithoutSuggestion() {
        val page = revealed("b", typed = "", suggestion = null)
        assertFalse("Your answer" in page.text)
        assertFalse("Looks" in page.text)
        assertFalse(page.exists("[data-autofocus]"))
    }

    @Test fun revealedUsesTheSessionLanguage() {
        assertEquals("Wien", revealed("a", "", null, language = "de").one(".flipcard").textContent)
        assertEquals(map, revealed("c", "", null).one(".flipcard img.side-image").getAttribute("src"))
    }

    // Summary

    @Test fun summaryListsMissedCards() {
        val page = play(PlayPhase.Summary(SessionResult("en", HintMode.HINTED, 3, listOf("a", "c"))))
        assertEquals("1 of 3 known first time", page.one("h1").textContent)
        assertTrue("Missed cards" in page.text)
        assertEquals(listOf("(image) → Vienna", "? → (image)"), page.all("li.missed").map { it.textContent })
        assertEquals("Back to stacks", page.one("a[href='#/']").textContent)

        page.button("Play again").click()
        page.button("Play missed cards only").click()
        val (again, missedOnly) = page.dispatched.map { it as SessionStarted }
        assertNull(again.cardIds)
        assertEquals(listOf("a", "c"), missedOnly.cardIds)
    }

    @Test fun summaryWithNothingMissed() {
        val page = play(PlayPhase.Summary(SessionResult("en", HintMode.HINTED, 3, emptyList(), score = 30)))
        assertEquals("3 of 3 known first time", page.one("h1").textContent)
        assertFalse("Missed cards" in page.text)
        assertFalse(page.hasButton("Play missed cards only"))
        assertEquals("Score: 30 of 30", page.one(".score").textContent)
        assertEquals("New best score!", page.one(".best").textContent)
    }

    @Test fun summaryBelowTheBest() {
        val result = SessionResult("en", HintMode.NONE, 3, listOf("a"), score = 60)
        val page = play(PlayPhase.Summary(result, previousBest = 90), bestScore = 90)
        assertEquals("Score: 60 of 90", page.one(".score").textContent)
        assertEquals("Best score: 90", page.one(".best").textContent)
    }

    @Test fun summaryOfSomeCardsDoesNotCompete() {
        val result = SessionResult("en", HintMode.NONE, 1, emptyList(), score = 30, countsForBest = false)
        val page = play(PlayPhase.Summary(result, previousBest = 10), bestScore = 10)
        assertEquals("Only rounds with all cards count for the best score.", page.one(".best").textContent)
    }

    @Test fun choosingAHintModeKeepsTheFocusOnIt() { // review I4
        val page = play(PlayPhase.Setup("en", HintMode.HINTED))
        val radio = page.field("Length only")
        radio.focus()
        render(
            page.root,
            AppState(Route.Play("s1"), Screen.Play(stack, emptyMap(), PlayPhase.Setup("en", HintMode.LENGTH_ONLY))),
            {},
            MainScope(),
        )
        assertEquals(radio.id, document.activeElement?.id)
        assertTrue(radio.id.isNotEmpty())
    }
}

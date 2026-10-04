package dev.silas.flipcards.ui

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.play.HintMode
import dev.silas.flipcards.play.SessionResult
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
import dev.silas.flipcards.state.SessionStarted
import kotlinx.browser.document
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
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

    private fun play(phase: PlayPhase, stack: Stack = this.stack) =
        mount(AppState(Route.Play(stack.id), Screen.Play(stack, mapOf("i" to flag, "j" to map), phase)))

    private fun session(cardId: String, language: String = "en") =
        startSession(listOf(cardId), language, HintMode.LENGTH_ONLY, 1)

    private fun asking(cardId: String, hint: String? = null, typed: String = "", language: String = "en") =
        play(PlayPhase.Asking(session(cardId, language), hint, typed))

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

    @Test fun askingShowsFrontHintAndFocusedInput() {
        val page = asking("b", hint = "_ _ _ _ _ _", typed = "ma")
        assertTrue("1 of 1 left" in page.text)
        assertEquals("Spain", page.one(".flipcard").textContent)
        assertFalse("Madrid" in page.text)
        assertEquals("_ _ _ _ _ _", page.one(".hint").textContent)
        val input = page.one("input[placeholder='Your answer']") as HTMLInputElement
        assertEquals("ma", input.value)
        assertEquals(input, document.activeElement)
    }

    @Test fun typingAndSubmitting() {
        val page = asking("b")
        page.type(page.one("input[placeholder='Your answer']"), "madrid")
        page.button("Show answer").click()
        assertEquals(listOf<Action>(AnswerTyped("madrid"), AnswerSubmitted), page.dispatched)
    }

    @Test fun askingShowsAnImageFront() {
        val page = asking("a")
        assertEquals(flag, page.one(".flipcard img.side-image").getAttribute("src"))
        assertFalse(page.exists(".hint"))
    }

    @Test fun imageOnlyBackHasNoAnswerInput() {
        val page = asking("c")
        assertFalse(page.exists("input[placeholder='Your answer']"))
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
        val page = play(PlayPhase.Summary(SessionResult("en", HintMode.HINTED, 3, emptyList())))
        assertEquals("3 of 3 known first time", page.one("h1").textContent)
        assertFalse("Missed cards" in page.text)
        assertFalse(page.hasButton("Play missed cards only"))
    }
}

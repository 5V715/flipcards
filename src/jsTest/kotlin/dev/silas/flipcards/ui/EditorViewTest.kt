package dev.silas.flipcards.ui

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Face
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.state.Action
import dev.silas.flipcards.state.AppState
import dev.silas.flipcards.state.CardAdded
import dev.silas.flipcards.state.CardDeleted
import dev.silas.flipcards.state.CardMoved
import dev.silas.flipcards.state.ImageRemoved
import dev.silas.flipcards.state.LanguageAdded
import dev.silas.flipcards.state.LanguageRemoved
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.SideRef
import dev.silas.flipcards.state.SideTextChanged
import dev.silas.flipcards.state.SideTextModeChanged
import dev.silas.flipcards.state.StackRenamed
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorViewTest {
    private val url = "data:image/jpeg;base64,AA"
    private val stack = Stack(
        "s1", "S", listOf("en", "de"),
        listOf(
            Card(
                "a",
                Side(SideText.Same("x"), imageId = "i1"),
                Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien"))),
            ),
            Card("b"),
        ),
    )

    private fun editor(
        stack: Stack = this.stack,
        saved: Boolean = true,
        imageError: SideRef? = null,
    ) = mount(AppState(Route.Edit(stack.id), Screen.Editor(stack, mapOf("i1" to url), saved, imageError)))

    private fun Mounted.card(number: Int): Element = all("section.card-editor")[number - 1]
    private fun Mounted.side(cardNumber: Int, face: String): Element =
        card(cardNumber).querySelector("fieldset.side[data-face='$face']")!!

    private fun Element.button(label: String): HTMLElement =
        querySelectorAll("button").asList().map { it as HTMLElement }
            .first { it.textContent?.trim() == label || it.getAttribute("aria-label") == label }

    private fun Element.hasButton(label: String): Boolean =
        querySelectorAll("button").asList().map { it as HTMLElement }
            .any { it.textContent?.trim() == label || it.getAttribute("aria-label") == label }

    @Test fun showsNameAndSaveStatus() {
        val page = editor()
        assertEquals("S", (page.field("Stack name") as HTMLInputElement).value)
        assertEquals("Saved", page.one("#save-status").textContent)
        assertEquals("Not saved", editor(saved = false).one("#save-status").textContent)
    }

    @Test fun linksBackAndToPlay() {
        val page = editor()
        assertTrue(page.exists("a[href='#/']"))
        assertTrue(page.exists("a[href='#/stack/s1/play']"))
    }

    @Test fun renaming() {
        val page = editor()
        page.type(page.field("Stack name"), "New")
        assertEquals(listOf<Action>(StackRenamed("New")), page.dispatched)
    }

    @Test fun removingALanguageAsksFirst() {
        val page = editor()
        val questions = stubConfirm(false)
        page.root.button("Remove de").click()
        assertEquals(listOf("Remove language \"de\"? Its texts are deleted from every card."), questions)
        assertTrue(page.dispatched.isEmpty())
        stubConfirm(true)
        page.root.button("Remove de").click()
        assertEquals(listOf<Action>(LanguageRemoved("de")), page.dispatched)
    }

    @Test fun theLastLanguageCannotBeRemoved() {
        val page = editor(stack.copy(languages = listOf("en")))
        assertFalse(page.root.hasButton("Remove en"))
    }

    @Test fun addingALanguage() {
        val page = editor()
        val input = page.one("input[placeholder='Language code, e.g. de']") as HTMLInputElement
        input.value = "es"
        page.button("Add language").click()
        assertEquals(listOf<Action>(LanguageAdded("es")), page.dispatched)
    }

    @Test fun showsCardsAndFlagsIncompleteOnes() {
        val page = editor()
        assertEquals(2, page.all("section.card-editor").size)
        assertTrue("Card 1" in page.card(1).textContent!!)
        // The badge is always in the page and hidden for complete cards, so the store can switch it
        // on and off while the user types (typing does not re-render).
        assertTrue((page.one("#incomplete-a")).hidden)
        assertFalse((page.one("#incomplete-b")).hidden)
        assertEquals("Incomplete", page.one("#incomplete-b").textContent)
    }

    @Test fun cardButtons() {
        val page = editor()
        stubConfirm(true)
        page.card(1).button("Move down").click()
        page.card(2).button("Move up").click()
        page.card(1).button("Delete").click()
        assertEquals(listOf<Action>(CardMoved("a", 1), CardMoved("b", -1), CardDeleted("a")), page.dispatched)
    }

    @Test fun deletingACardAsksFirst() {
        val page = editor()
        val questions = stubConfirm(false)
        page.card(1).button("Delete").click()
        assertEquals(listOf("Delete this card?"), questions)
        assertTrue(page.dispatched.isEmpty())
    }

    @Test fun addCardGetsANewId() {
        val page = editor()
        page.button("Add card").click()
        assertTrue((page.dispatched.single() as CardAdded).cardId.isNotBlank())
    }

    @Test fun textThatIsTheSameInAllLanguages() {
        val page = editor()
        val front = page.side(1, "front")
        val input = page.field("Text", front) as HTMLInputElement
        assertEquals("x", input.value)
        assertFalse((page.field("Translated", front) as HTMLInputElement).checked)
        page.type(input, "y")
        assertEquals(listOf<Action>(SideTextChanged("a", Face.FRONT, null, "y")), page.dispatched)
    }

    @Test fun translatedTextHasOneInputPerLanguage() {
        val page = editor()
        val back = page.side(1, "back")
        assertTrue((page.field("Translated", back) as HTMLInputElement).checked)
        assertEquals("Vienna", (page.field("en", back) as HTMLInputElement).value)
        val de = page.field("de", back) as HTMLInputElement
        assertEquals("Wien", de.value)
        page.type(de, "Wien!")
        assertEquals(listOf<Action>(SideTextChanged("a", Face.BACK, "de", "Wien!")), page.dispatched)
    }

    @Test fun togglingTranslated() {
        val page = editor()
        stubConfirm(true)
        page.field("Translated", page.side(1, "front")).click()
        page.field("Translated", page.side(1, "back")).click()
        assertEquals(
            listOf<Action>(
                SideTextModeChanged("a", Face.FRONT, true),
                SideTextModeChanged("a", Face.BACK, false),
            ),
            page.dispatched,
        )
    }

    @Test fun imagePreviewAndRemoval() {
        val page = editor()
        val front = page.side(1, "front")
        assertEquals(url, front.querySelector("img.side-image")!!.getAttribute("src"))
        front.button("Remove image").click()
        assertEquals(listOf<Action>(ImageRemoved("a", Face.FRONT)), page.dispatched)

        val back = page.side(1, "back")
        assertEquals(null, back.querySelector("img"))
        assertFalse(back.hasButton("Remove image"))
        assertEquals("image/*", page.field("Choose image", back).getAttribute("accept"))
    }

    @Test fun imageErrorShowsOnThatSideOnly() {
        val page = editor(imageError = SideRef("b", Face.BACK))
        val errors = page.all(".field-error")
        assertEquals(1, errors.size)
        assertEquals("This file is not an image the browser can read.", errors[0].textContent)
        assertTrue(page.side(2, "back").contains(errors[0]))
    }

    @Test fun droppingTranslationsAsksFirst() { // review I2
        val page = editor()
        val checkbox = page.field("Translated", page.side(1, "back")) as HTMLInputElement
        val questions = stubConfirm(false)
        checkbox.click()
        assertEquals(listOf("Use one text for all languages? The text for de is deleted."), questions)
        assertTrue(page.dispatched.isEmpty())
        assertTrue(checkbox.checked)
    }

    @Test fun noQuestionWhenNoTranslationWouldBeLost() { // review I2
        val onlyFirst = stack.copy(
            cards = listOf(Card("a", Side(), Side(SideText.Translated(mapOf("en" to "Vienna", "de" to " "))))),
        )
        val page = editor(onlyFirst)
        val questions = stubConfirm(false)
        page.field("Translated", page.side(1, "back")).click()
        assertTrue(questions.isEmpty())
        assertEquals(listOf<Action>(SideTextModeChanged("a", Face.BACK, false)), page.dispatched)
    }

    @Test fun focusStaysOnTheControlAfterARedraw() { // review I4
        val page = editor()
        val moveDown = page.one("#a-down")
        moveDown.focus()
        val moved = stack.copy(cards = stack.cards.reversed())
        render(page.root, AppState(Route.Edit("s1"), Screen.Editor(moved, mapOf("i1" to url), false)), {}, MainScope())
        assertEquals("a-down", document.activeElement?.id)
    }
}

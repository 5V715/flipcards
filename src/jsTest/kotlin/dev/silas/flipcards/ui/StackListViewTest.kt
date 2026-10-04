package dev.silas.flipcards.ui

import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.samples.Sample
import dev.silas.flipcards.state.Action
import dev.silas.flipcards.state.AppState
import dev.silas.flipcards.state.DeleteStackConfirmed
import dev.silas.flipcards.state.ErrorDismissed
import dev.silas.flipcards.state.ExportRequested
import dev.silas.flipcards.state.NewStackRequested
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.SampleChosen
import dev.silas.flipcards.state.SamplesClosed
import dev.silas.flipcards.state.SamplesPanel
import dev.silas.flipcards.state.SamplesRequested
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.UiLanguageChosen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event

class StackListViewTest {
    private val alpha = StackSummary("a", "Alpha", listOf("en", "de"), 1)
    private val beta = StackSummary("b", "Beta", listOf("en"), 2)

    private fun list(vararg stacks: StackSummary, error: String? = null) =
        mount(AppState(Route.Home, Screen.StackList(stacks.toList()), error))

    @Test fun emptyListShowsMessage() =
        assertTrue("No stacks yet. Create one or import a file." in list().text)

    @Test fun showsNameCardCountAndLanguages() {
        val page = list(alpha, beta)
        assertEquals(2, page.all("li.stack").size)
        assertTrue("Alpha" in page.text)
        assertTrue("1 card · en, de" in page.text)
        assertTrue("2 cards · en" in page.text)
    }

    @Test fun linksToPlayAndEdit() {
        val page = list(alpha)
        assertTrue(page.exists("a[href='#/stack/a/play']"))
        assertTrue(page.exists("a[href='#/stack/a/edit']"))
    }

    @Test fun newStackButton() {
        val page = list()
        page.button("New stack").click()
        assertEquals(listOf<Action>(NewStackRequested), page.dispatched)
    }

    @Test fun exportButton() {
        val page = list(alpha)
        page.button("Export").click()
        assertEquals(listOf<Action>(ExportRequested("a")), page.dispatched)
    }

    @Test fun deleteAsksFirst() {
        val page = list(alpha)
        val questions = stubConfirm(false)
        page.button("Delete").click()
        assertEquals(listOf("Delete \"Alpha\" and all its cards?"), questions)
        assertTrue(page.dispatched.isEmpty())
        stubConfirm(true)
        page.button("Delete").click()
        assertEquals(listOf<Action>(DeleteStackConfirmed("a")), page.dispatched)
    }

    @Test fun importIsAFileInputForJson() {
        val input = list().field("Import")
        assertEquals("file", input.getAttribute("type"))
        assertEquals(".json,application/json", input.getAttribute("accept"))
    }

    @Test fun errorBannerCanBeDismissed() {
        val page = list(error = "This is not a Flipcards stack file.")
        assertEquals("This is not a Flipcards stack file.", page.one("[role=alert] .banner-text").textContent)
        page.button("Dismiss").click()
        assertEquals(listOf<Action>(ErrorDismissed), page.dispatched)
        assertFalse(list().exists("[role=alert]"))
    }

    @Test fun stackNamesAreTextNotMarkup() {
        val page = list(StackSummary("x", "<b id='injected'>bold</b>", listOf("en"), 0))
        assertFalse(page.exists("#injected"))
        assertTrue("<b id='injected'>bold</b>" in page.text)
    }

    @Test fun loadingAndNotFound() {
        assertTrue("Loading…" in mount(AppState(Route.Home, Screen.Loading)).text)
        val notFound = mount(AppState(Route.Unknown, Screen.NotFound("Page not found.")))
        assertTrue("Page not found." in notFound.text)
        assertEquals("Back to stacks", notFound.one("a[href='#/']").textContent)
    }

    @Test fun interfaceLanguageCanBePicked() {
        val page = list(alpha)
        val select = page.field("Language") as HTMLSelectElement
        assertEquals("en", select.value)
        assertEquals(listOf("English", "Deutsch", "Español", "Français"), select.options.asList().map { it.textContent })
        select.value = "fr"
        select.dispatchEvent(Event("change", EventInit(bubbles = true)))
        assertEquals(listOf<Action>(UiLanguageChosen("fr")), page.dispatched)
    }

    @Test fun listInGerman() {
        val page = mount(AppState(Route.Home, Screen.StackList(listOf(alpha, beta)), uiLanguage = "de"))
        assertTrue(page.hasButton("Neuer Stapel"))
        assertTrue("1 Karte · en, de" in page.text)
        assertTrue("2 Karten · en" in page.text)
        assertEquals("Spielen", page.one("a[href='#/stack/a/play']").textContent)
        assertEquals("Deutsch", (page.field("Sprache") as HTMLSelectElement).selectedOptions.asList().single().textContent)
    }

    private val shapes = Sample("country-shapes.flipcards.json", "https://raw.example/shapes", 435000)
    private val words = Sample("spanish-basic-words.flipcards.json", "https://raw.example/words", 10700)

    private fun withSamples(panel: SamplesPanel?) = mount(AppState(Route.Home, Screen.StackList(listOf(alpha), panel)))

    @Test fun samplesButtonOpensAndClosesTheList() {
        val closed = withSamples(null)
        assertEquals("false", closed.button("Samples").getAttribute("aria-expanded"))
        assertFalse(closed.exists("#samples"))
        closed.button("Samples").click()
        val open = withSamples(SamplesPanel.Loading)
        assertEquals("true", open.button("Samples").getAttribute("aria-expanded"))
        assertTrue("Loading the samples…" in open.one("#samples").textContent!!)
        open.button("Samples").click()
        assertEquals(listOf<Action>(SamplesRequested), closed.dispatched)
        assertEquals(listOf<Action>(SamplesClosed), open.dispatched)
    }

    @Test fun samplesAreListedWithAnAddButton() {
        val panel = SamplesPanel.Loaded(listOf(shapes, words), adding = setOf(words.fileName), added = setOf(shapes.fileName))
        val page = withSamples(panel)
        assertEquals(listOf("Country shapes", "Spanish basic words"), page.all(".sample h3").map { it.textContent })
        assertTrue("425 KB" in page.one(".sample").textContent!!)
        assertEquals("✓ Added", page.one(".sample .added").textContent)
        assertTrue(page.one("a.samples-source").getAttribute("href")!!.startsWith("https://github.com/"))

        page.button("Add").click()
        page.button("Adding…").click() // already downloading: ignored
        assertEquals("true", page.button("Adding…").getAttribute("aria-disabled"))
        assertEquals(listOf<Action>(SampleChosen(shapes)), page.dispatched)
    }

    @Test fun emptySamplesFolder() =
        assertTrue("There are no samples in the folder." in withSamples(SamplesPanel.Loaded(emptyList())).text)
}

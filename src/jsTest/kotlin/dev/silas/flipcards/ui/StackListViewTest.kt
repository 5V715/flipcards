package dev.silas.flipcards.ui

import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.state.Action
import dev.silas.flipcards.state.AppState
import dev.silas.flipcards.state.DeleteStackConfirmed
import dev.silas.flipcards.state.ErrorDismissed
import dev.silas.flipcards.state.ExportRequested
import dev.silas.flipcards.state.NewStackRequested
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}

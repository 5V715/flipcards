package dev.silas.flipcards

import dev.silas.flipcards.effects.FakeEnv
import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.state.Navigate
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.ui.freshRoot
import kotlinx.browser.document
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {
    private fun newRoot() = freshRoot()

    @Test fun navigatingHomeRendersTheStoredStacks() = runTest {
        val env = FakeEnv()
        env.storage.stacks["a"] = Stack("a", "Alpha", listOf("en"), emptyList())
        val root = newRoot()
        val store = Store(root, env, this)

        store.dispatch(Navigate(Route.Home))
        assertTrue("Loading…" in root.textContent!!)
        advanceUntilIdle()

        assertTrue("Alpha" in root.textContent!!)
        assertTrue("0 cards · en" in root.textContent!!)
    }

    @Test fun newStackButtonCreatesAStackAndOpensItsEditor() = runTest {
        val env = FakeEnv()
        val root = newRoot()
        val store = Store(root, env, this)
        store.dispatch(Navigate(Route.Home))
        advanceUntilIdle()

        val button = root.querySelectorAll("button").asList().first { it.textContent == "New stack" }
        (button as HTMLElement).click()
        advanceUntilIdle()

        assertEquals("New stack", env.storage.stacks.values.single().name)
        assertEquals(listOf<Route>(Route.Edit(env.storage.stacks.keys.single())), env.navigations)
    }

    @Test fun typingKeepsTheInputAndUpdatesTheSaveStatus() = runTest {
        val env = FakeEnv()
        env.storage.stacks["a"] = Stack("a", "Alpha", listOf("en"), emptyList())
        val root = newRoot()
        val store = Store(root, env, this)
        store.dispatch(Navigate(Route.Edit("a")))
        advanceUntilIdle()
        val status = { root.querySelector("#save-status")!!.textContent }
        assertEquals("Saved", status())

        val input = root.querySelector("#stack-name") as HTMLInputElement
        input.value = "Alpha!"
        input.dispatchEvent(Event("input", EventInit(bubbles = true)))

        // No re-render: the very same input element is still on the page, so focus and cursor survive.
        assertTrue(root.contains(input))
        assertEquals("Not saved", status())

        advanceUntilIdle()
        assertEquals("Alpha!", env.storage.stacks["a"]!!.name)
        assertTrue(root.contains(input))
        assertEquals("Saved", status())
    }

    @Test fun playingASessionFromStartToSummary() = runTest {
        val env = FakeEnv()
        env.storage.stacks["a"] = Stack(
            "a", "Capitals", listOf("en"),
            listOf(Card("c1", Side(SideText.Same("Spain")), Side(SideText.Same("Madrid")))),
        )
        val root = newRoot()
        val store = Store(root, env, this)
        fun button(label: String) =
            root.querySelectorAll("button").asList().first { it.textContent == label } as HTMLElement

        store.dispatch(Navigate(Route.Play("a")))
        advanceUntilIdle()
        (root.querySelector("#hint-mode-length_only") as HTMLElement).click()
        button("Start").click()
        assertTrue("Spain" in root.textContent!!)

        // The letters typed into the hidden field appear in the blanks; the field keeps the focus.
        val input = root.querySelector("#answer-input") as HTMLInputElement
        input.value = "madrid"
        input.dispatchEvent(Event("input", EventInit(bubbles = true)))
        assertEquals("madrid", root.querySelector(".answer-slots")!!.textContent)
        assertTrue(root.contains(input))
        button("Show answer").click()
        assertTrue("Looks right" in root.textContent!!)

        button("Knew it").click()
        assertTrue("1 of 1 known" in root.textContent!!)
        assertTrue("New best score!" in root.textContent!!)
        assertEquals(mapOf(1 to 20), env.bestScores["a"])
    }

    @Test fun pickingALanguageTranslatesTheInterface() = runTest {
        val env = FakeEnv()
        env.storage.stacks["a"] = Stack(
            "a", "Capitals", listOf("en", "de"),
            listOf(Card("c1", Side(SideText.Same("Spain")), Side(SideText.Same("Madrid")))),
        )
        val root = newRoot()
        val store = Store(root, env, this)
        store.dispatch(Navigate(Route.Play("a")))
        advanceUntilIdle()
        assertTrue("Start" in root.textContent!!)

        val select = root.querySelector("#play-language") as HTMLSelectElement
        select.value = "de"
        select.dispatchEvent(Event("change", EventInit(bubbles = true)))
        assertTrue("Starten" in root.textContent!!)
        assertTrue("Hinweise" in root.textContent!!)
        assertEquals("de", document.documentElement?.getAttribute("lang"))
        assertEquals("de", env.uiLanguage)

        // Going back to the list keeps the language.
        store.dispatch(Navigate(Route.Home))
        advanceUntilIdle()
        assertTrue("Neuer Stapel" in root.textContent!!)
    }

    @Test fun startsInTheStoredInterfaceLanguage() = runTest {
        val env = FakeEnv()
        env.uiLanguage = "fr"
        val root = newRoot()
        val store = Store(root, env, this)
        assertEquals("fr", store.state.uiLanguage)
        assertTrue("Chargement…" in root.textContent!!)
    }

    @Test fun typingUpdatesTheIncompleteBadge() = runTest { // review I3
        val env = FakeEnv()
        env.storage.stacks["a"] = Stack("a", "Alpha", listOf("en"), listOf(Card("c1", Side(SideText.Same("x")), Side())))
        val root = newRoot()
        val store = Store(root, env, this)
        store.dispatch(Navigate(Route.Edit("a")))
        advanceUntilIdle()
        val badge = root.querySelector("#incomplete-c1") as HTMLElement
        assertEquals(false, badge.hidden)

        val back = root.querySelector("fieldset[data-face='back'] input[type=text]") as HTMLInputElement
        back.value = "y"
        back.dispatchEvent(Event("input", EventInit(bubbles = true)))
        assertTrue(root.contains(back))
        assertEquals(true, badge.hidden)

        back.value = ""
        back.dispatchEvent(Event("input", EventInit(bubbles = true)))
        assertEquals(false, badge.hidden)
        advanceUntilIdle()
    }
}

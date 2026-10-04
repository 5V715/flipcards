package dev.silas.flipcards

import dev.silas.flipcards.effects.FakeEnv
import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.state.Navigate
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.ui.freshRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
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
        button("Start").click()
        assertTrue("Spain" in root.textContent!!)

        val input = root.querySelector("input[placeholder='Your answer']") as HTMLInputElement
        input.value = "madrid"
        input.dispatchEvent(Event("input", EventInit(bubbles = true)))
        button("Show answer").click()
        assertTrue("Looks right" in root.textContent!!)

        button("Knew it").click()
        assertTrue("1 of 1 known first time" in root.textContent!!)
    }
}

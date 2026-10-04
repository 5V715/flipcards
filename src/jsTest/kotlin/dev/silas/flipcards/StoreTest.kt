package dev.silas.flipcards

import dev.silas.flipcards.effects.FakeEnv
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.state.Navigate
import dev.silas.flipcards.state.Route
import kotlinx.browser.document
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {
    private fun newRoot() = (document.createElement("div") as HTMLElement).also { document.body!!.appendChild(it) }

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
}

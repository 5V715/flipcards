package dev.silas.flipcards.state

import dev.silas.flipcards.model.Stack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateTest {
    private val stack = Stack("s1", "S", listOf("en"), emptyList())

    @Test fun navigateShowsLoadingAndClearsError() {
        val s = update(AppState(error = "boom"), Navigate(Route.Edit("s1")))
        assertEquals(AppState(Route.Edit("s1"), Screen.Loading, null), s)
    }

    @Test fun unknownRouteIsNotFound() =
        assertEquals(Screen.NotFound("Page not found."), update(AppState(), Navigate(Route.Unknown)).screen)

    @Test fun listLoads() =
        assertEquals(Screen.StackList(emptyList()), update(AppState(), StackListLoaded(emptyList())).screen)

    @Test fun editorLoads() {
        val s = update(AppState(Route.Edit("s1")), EditorLoaded(stack, emptyMap()))
        assertEquals(Screen.Editor(stack, emptyMap(), saved = true), s.screen)
    }

    @Test fun staleLoadsAreIgnored() { // Review Focus 1
        val onList = AppState(Route.Home, Screen.StackList(emptyList()))
        assertEquals(onList, update(onList, EditorLoaded(stack, emptyMap())))
        val onOther = AppState(Route.Edit("other"))
        assertEquals(onOther, update(onOther, EditorLoaded(stack, emptyMap())))
        val onEditor = AppState(Route.Edit("s1"))
        assertEquals(onEditor, update(onEditor, StackListLoaded(emptyList())))
        assertEquals(onEditor, update(onEditor, StackMissing("other")))
    }

    @Test fun missingStack() =
        assertEquals(
            Screen.NotFound("This stack does not exist."),
            update(AppState(Route.Play("s1")), StackMissing("s1")).screen,
        )

    @Test fun errorBanner() {
        assertEquals("x", update(AppState(), ErrorRaised("x")).error)
        assertNull(update(AppState(error = "x"), ErrorDismissed).error)
    }
}

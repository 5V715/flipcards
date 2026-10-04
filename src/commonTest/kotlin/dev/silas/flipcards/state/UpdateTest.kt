package dev.silas.flipcards.state

import dev.silas.flipcards.i18n.French
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.samples.Sample
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

    private val sample = Sample("a.flipcards.json", "https://raw.example/a.flipcards.json", 1)
    private val list = AppState(Route.Home, Screen.StackList(emptyList()))
    private val AppState.samples get() = (screen as Screen.StackList).samples

    @Test fun samplesPanelOpensLoadsAndCloses() {
        var s = update(list, SamplesRequested)
        assertEquals(SamplesPanel.Loading, s.samples)
        s = update(s, SamplesLoaded(listOf(sample)))
        assertEquals(SamplesPanel.Loaded(listOf(sample)), s.samples)
        assertNull(update(s, SamplesClosed).samples)
        // A listing that arrives after the panel was closed does not open it again.
        assertNull(update(update(update(list, SamplesRequested), SamplesClosed), SamplesLoaded(listOf(sample))).samples)
    }

    @Test fun samplesPanelSurvivesAReloadOfTheStacks() {
        val open = update(update(list, SamplesRequested), SamplesLoaded(listOf(sample)))
        val stacks = listOf(StackSummary("x", "X", listOf("en"), 1))
        val reloaded = update(open, StackListLoaded(stacks))
        assertEquals(stacks, (reloaded.screen as Screen.StackList).stacks)
        assertEquals(open.samples, reloaded.samples)
    }

    @Test fun addingASampleIsTracked() {
        var s = update(update(list, SamplesRequested), SamplesLoaded(listOf(sample)))
        s = update(s, SampleChosen(sample))
        assertEquals(setOf(sample.fileName), (s.samples as SamplesPanel.Loaded).adding)
        assertEquals(
            SamplesPanel.Loaded(listOf(sample), added = setOf(sample.fileName)),
            update(s, SampleAdded(sample.fileName)).samples,
        )
        assertEquals(SamplesPanel.Loaded(listOf(sample)), update(s, SampleFailed(sample.fileName)).samples)
    }

    @Test fun samplesFailureClosesThePanelWithAMessage() {
        val failed = update(update(list.copy(uiLanguage = "fr"), SamplesRequested), SamplesFailed)
        assertNull(failed.samples)
        assertEquals(French.samplesUnavailable, failed.error)
    }

    @Test fun samplesActionsElsewhereChangeNothing() {
        val loading = AppState(Route.Home, Screen.Loading)
        assertEquals(loading, update(loading, SamplesRequested))
        assertEquals(loading, update(loading, SampleAdded("a")))
    }
}

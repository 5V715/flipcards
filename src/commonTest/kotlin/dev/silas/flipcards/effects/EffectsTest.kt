package dev.silas.flipcards.effects

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Face
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.summary
import dev.silas.flipcards.state.Action
import dev.silas.flipcards.state.AnswerSubmitted
import dev.silas.flipcards.state.AppState
import dev.silas.flipcards.state.CardDeleted
import dev.silas.flipcards.state.CardGraded
import dev.silas.flipcards.state.DeleteStackConfirmed
import dev.silas.flipcards.state.EditorLoaded
import dev.silas.flipcards.state.ErrorRaised
import dev.silas.flipcards.state.ExportRequested
import dev.silas.flipcards.state.ImageChosen
import dev.silas.flipcards.state.ImageRemoved
import dev.silas.flipcards.state.ImageSaveFailed
import dev.silas.flipcards.state.ImportFileRead
import dev.silas.flipcards.state.Navigate
import dev.silas.flipcards.state.NewStackRequested
import dev.silas.flipcards.state.PlayLanguageChosen
import dev.silas.flipcards.state.PlayLoaded
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.SessionStarted
import dev.silas.flipcards.state.StackListLoaded
import dev.silas.flipcards.state.StackMissing
import dev.silas.flipcards.state.StackRenamed
import dev.silas.flipcards.state.StackSaveFailed
import dev.silas.flipcards.state.StackSaved
import dev.silas.flipcards.state.UiLanguageChosen
import dev.silas.flipcards.state.update
import dev.silas.flipcards.transfer.buildExport
import dev.silas.flipcards.transfer.encodeExport
import dev.silas.flipcards.transfer.parseImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EffectsTest {
    private val url = "data:image/jpeg;base64,AA"
    private val stackA = Stack(
        "a", "Alpha", listOf("en", "de"),
        listOf(Card("c1", Side(imageId = "i1"), Side(SideText.Same("x")))),
    )

    /** A miniature store: applies update(), then lets the effects react, like the real one. */
    private class Harness(scope: TestScope) {
        val env = FakeEnv()
        val storage get() = env.storage
        var current = AppState()
        val dispatched = mutableListOf<Action>()
        private val effects = Effects(env, scope, { current }) { dispatched += it; run(it) }

        fun run(action: Action) {
            val before = current
            current = update(before, action)
            effects.handle(action, before, current)
        }
    }

    private fun TestScope.harness(withStackA: Boolean = true) = Harness(this).apply {
        if (withStackA) {
            storage.stacks["a"] = stackA
            storage.images["i1"] = "a" to url
        }
    }

    private fun TestScope.editing() = harness().apply {
        run(Navigate(Route.Edit("a")))
        advanceUntilIdle()
        dispatched.clear()
    }

    @Test fun navigateHomeLoadsSummaries() = runTest {
        val h = harness()
        h.run(Navigate(Route.Home))
        advanceUntilIdle()
        assertEquals(listOf<Action>(StackListLoaded(listOf(stackA.summary()))), h.dispatched)
    }

    @Test fun navigateToMissingStack() = runTest {
        val h = harness()
        h.run(Navigate(Route.Edit("x")))
        advanceUntilIdle()
        assertEquals(listOf<Action>(StackMissing("x")), h.dispatched)
    }

    @Test fun navigateToEditorLoadsStackAndImages() = runTest {
        val h = harness()
        h.run(Navigate(Route.Edit("a")))
        advanceUntilIdle()
        assertEquals(listOf<Action>(EditorLoaded(stackA, mapOf("i1" to url))), h.dispatched)
    }

    @Test fun navigateToPlayPassesStoredLanguage() = runTest {
        val h = harness()
        h.env.playLanguages["a"] = "de"
        h.run(Navigate(Route.Play("a")))
        advanceUntilIdle()
        assertEquals(listOf<Action>(PlayLoaded(stackA, mapOf("i1" to url), "de")), h.dispatched)
    }

    @Test fun newStackIsSavedAndOpened() = runTest {
        val h = harness(withStackA = false)
        h.run(NewStackRequested)
        advanceUntilIdle()
        assertEquals(Stack("id1", "New stack", listOf("en"), emptyList()), h.storage.stacks["id1"])
        assertEquals(listOf<Route>(Route.Edit("id1")), h.env.navigations)
    }

    @Test fun deleteReloadsList() = runTest {
        val h = harness()
        h.run(DeleteStackConfirmed("a"))
        advanceUntilIdle()
        assertTrue(h.storage.stacks.isEmpty())
        assertTrue(h.storage.images.isEmpty())
        assertEquals(listOf<Action>(StackListLoaded(emptyList())), h.dispatched)
    }

    @Test fun exportDownloadsFile() = runTest {
        val h = harness()
        h.run(ExportRequested("a"))
        advanceUntilIdle()
        val (fileName, text) = h.env.downloads.single()
        assertEquals("alpha.flipcards.json", fileName)
        assertEquals(buildExport(stackA, mapOf("i1" to url)), parseImport(text))
    }

    @Test fun importStoresACopyWithNewIdsAndUniqueName() = runTest {
        val h = harness()
        h.run(ImportFileRead(encodeExport(buildExport(stackA, mapOf("i1" to url)))))
        advanceUntilIdle()
        assertEquals(2, h.storage.stacks.size)
        val copy = h.storage.stacks.values.single { it.id != "a" }
        assertEquals("Alpha (2)", copy.name)
        assertFalse(copy.cards[0].id == "c1")
        val imageId = copy.cards[0].front.imageId!!
        assertFalse(imageId == "i1")
        assertEquals(copy.id to url, h.storage.images[imageId])
        assertEquals(stackA, h.storage.stacks["a"])
        assertEquals(2, (h.dispatched.last() as StackListLoaded).stacks.size)
    }

    @Test fun invalidImportRaisesErrorAndStoresNothing() = runTest {
        val h = harness()
        h.run(ImportFileRead("nope"))
        advanceUntilIdle()
        assertEquals(listOf<Action>(ErrorRaised("This is not a Flipcards stack file.")), h.dispatched)
        assertEquals(1, h.storage.stacks.size)
    }

    @Test fun editsAreSavedAfter500ms() = runTest {
        val h = editing()
        h.run(StackRenamed("X"))
        advanceTimeBy(499)
        assertEquals("Alpha", h.storage.stacks["a"]!!.name)
        advanceTimeBy(2)
        runCurrent()
        assertEquals("X", h.storage.stacks["a"]!!.name)
        assertEquals(listOf<Action>(StackSaved(stackA.copy(name = "X"))), h.dispatched)
    }

    @Test fun rapidEditsSaveOnce() = runTest {
        val h = editing()
        for (name in listOf("X", "XY", "XYZ")) {
            h.run(StackRenamed(name))
            advanceTimeBy(100)
        }
        advanceUntilIdle()
        assertEquals(listOf<Action>(StackSaved(stackA.copy(name = "XYZ"))), h.dispatched)
    }

    @Test fun failedSaveReportsError() = runTest {
        val h = editing()
        h.storage.failSaves = true
        h.run(StackRenamed("X"))
        advanceUntilIdle()
        assertEquals(listOf<Action>(StackSaveFailed("Could not save: full")), h.dispatched)
    }

    @Test fun leavingTheEditorSavesPendingChanges() = runTest { // Review Focus 3
        val h = editing()
        h.run(StackRenamed("X"))
        h.run(Navigate(Route.Home))
        runCurrent()
        assertEquals("X", h.storage.stacks["a"]!!.name)
        assertEquals("X", (h.dispatched.single() as StackListLoaded).stacks.single().name)
    }

    @Test fun chosenImageIsStored() = runTest {
        val h = editing()
        h.run(ImageChosen("c1", Face.BACK, "i9", "data:image/jpeg;base64,NEW"))
        advanceUntilIdle()
        assertEquals("a" to "data:image/jpeg;base64,NEW", h.storage.images["i9"])
        assertEquals("i9", h.storage.stacks["a"]!!.cards[0].back.imageId)
    }

    @Test fun failedImageSaveIsReported() = runTest { // Review Focus 4
        val h = editing()
        h.storage.failSaves = true
        h.run(ImageChosen("c1", Face.BACK, "i9", "data:image/jpeg;base64,NEW"))
        advanceUntilIdle()
        assertTrue(ImageSaveFailed("c1", Face.BACK, "i9") in h.dispatched)
        assertFalse("i9" in h.storage.images)
    }

    @Test fun replacedAndRemovedImagesAreDeleted() = runTest {
        val h = editing()
        h.run(ImageChosen("c1", Face.FRONT, "i9", "data:image/jpeg;base64,NEW"))
        advanceUntilIdle()
        assertEquals(setOf("i9"), h.storage.images.keys)
        h.run(ImageRemoved("c1", Face.FRONT))
        advanceUntilIdle()
        assertTrue(h.storage.images.isEmpty())
    }

    @Test fun deletingACardDeletesItsImages() = runTest {
        val h = editing()
        h.run(CardDeleted("c1"))
        advanceUntilIdle()
        assertTrue(h.storage.images.isEmpty())
        assertTrue(h.storage.stacks["a"]!!.cards.isEmpty())
    }

    @Test fun playLanguageIsRemembered() = runTest {
        val h = harness()
        h.run(Navigate(Route.Play("a")))
        advanceUntilIdle()
        h.run(PlayLanguageChosen("de"))
        h.run(PlayLanguageChosen("fr")) // not a language of the stack
        assertEquals("de", h.env.playLanguages["a"])
    }

    @Test fun bestScoreIsLoadedAndSaved() = runTest {
        val h = harness()
        h.env.bestScores["a"] = mapOf(1 to 5, 7 to 70)
        h.run(Navigate(Route.Play("a")))
        advanceUntilIdle()
        assertEquals(mapOf(1 to 5, 7 to 70), (h.current.screen as Screen.Play).bestScores)
        h.run(SessionStarted(1))
        h.run(AnswerSubmitted)
        h.run(CardGraded(true)) // the only card, known first time with hints: 10 points
        assertEquals(mapOf(1 to 10, 7 to 70), h.env.bestScores["a"])
    }

    @Test fun aLowerScoreKeepsTheBest() = runTest {
        val h = harness()
        h.env.bestScores["a"] = mapOf(1 to 50)
        h.run(Navigate(Route.Play("a")))
        advanceUntilIdle()
        h.run(SessionStarted(1))
        h.run(AnswerSubmitted)
        h.run(CardGraded(true))
        assertEquals(mapOf(1 to 50), h.env.bestScores["a"])
    }

    @Test fun pickingAPlayLanguageSavesTheInterfaceLanguage() = runTest {
        val h = harness()
        h.run(Navigate(Route.Play("a")))
        advanceUntilIdle()
        h.run(PlayLanguageChosen("de"))
        assertEquals("de", h.env.uiLanguage)
        h.run(UiLanguageChosen("fr"))
        assertEquals("fr", h.env.uiLanguage)
    }

    @Test fun messagesAreInTheInterfaceLanguage() = runTest {
        val h = harness()
        h.run(UiLanguageChosen("de"))
        h.run(ImportFileRead("{}"))
        advanceUntilIdle()
        assertEquals(listOf<Action>(ErrorRaised("Das ist keine Flipcards-Stapeldatei.")), h.dispatched)
    }

    @Test fun aNewStackIsNamedInTheInterfaceLanguage() = runTest {
        val h = harness(withStackA = false)
        h.run(UiLanguageChosen("es"))
        h.run(NewStackRequested)
        advanceUntilIdle()
        assertEquals("Nuevo mazo", h.storage.stacks.values.single().name)
    }

    @Test fun leavingTheEditorStillNavigatesWhenTheSaveFails() = runTest { // review I1
        val h = editing()
        h.storage.failSaves = true
        h.run(StackRenamed("X"))
        h.run(Navigate(Route.Home))
        advanceUntilIdle()
        assertTrue(ErrorRaised("Could not save: full") in h.dispatched)
        assertTrue(h.current.screen is Screen.StackList)
        assertEquals("Could not save: full", h.current.error)
    }
}

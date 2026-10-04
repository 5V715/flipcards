package dev.silas.flipcards.effects

import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.state.Action
import dev.silas.flipcards.state.AppState
import dev.silas.flipcards.state.DeleteStackConfirmed
import dev.silas.flipcards.state.EditorLoaded
import dev.silas.flipcards.state.ErrorRaised
import dev.silas.flipcards.state.ExportRequested
import dev.silas.flipcards.state.ImageChosen
import dev.silas.flipcards.state.ImageSaveFailed
import dev.silas.flipcards.state.ImportFileRead
import dev.silas.flipcards.state.Navigate
import dev.silas.flipcards.state.NewStackRequested
import dev.silas.flipcards.state.PlayLanguageChosen
import dev.silas.flipcards.state.PlayLoaded
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.StackListLoaded
import dev.silas.flipcards.state.StackMissing
import dev.silas.flipcards.state.StackSaveFailed
import dev.silas.flipcards.state.StackSaved
import dev.silas.flipcards.transfer.ImportException
import dev.silas.flipcards.transfer.buildExport
import dev.silas.flipcards.transfer.encodeExport
import dev.silas.flipcards.transfer.exportFileName
import dev.silas.flipcards.transfer.parseImport
import dev.silas.flipcards.transfer.uniqueName
import dev.silas.flipcards.transfer.withFreshIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val AUTOSAVE_DELAY_MS = 500L

/**
 * The asynchronous side of the app: loading, saving, importing and exporting.
 *
 * update() only computes the next state. After each action the store calls [handle],
 * which does the slow work in a coroutine and reports the outcome as a new action.
 */
class Effects(
    private val env: Env,
    private val scope: CoroutineScope,
    /** The store's current state, which may be newer than what [handle] was called with. */
    private val state: () -> AppState,
    private val dispatch: (Action) -> Unit,
) {
    private val storage get() = env.storage
    private var autosave: Job? = null

    fun handle(action: Action, before: AppState, after: AppState) {
        when (action) {
            is Navigate -> {
                // Leaving the editor before the autosave fired must not lose the change.
                val unsaved = (before.screen as? Screen.Editor)?.takeIf { !it.saved }?.stack
                if (unsaved != null) autosave?.cancel()
                launch {
                    if (unsaved != null) {
                        // A failed save must not block the navigation, or the user is stuck on "Loading…".
                        try {
                            storage.saveStack(unsaved)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            dispatch(ErrorRaised("Could not save: ${e.message}"))
                        }
                    }
                    load(action.route)
                }
            }

            NewStackRequested -> launch {
                val stack = Stack(env.newId(), "New stack", listOf("en"), emptyList())
                storage.saveStack(stack)
                env.navigate(Route.Edit(stack.id))
            }

            is DeleteStackConfirmed -> launch {
                storage.deleteStack(action.stackId)
                dispatch(StackListLoaded(storage.loadStackSummaries()))
            }

            is ExportRequested -> launch {
                val stack = storage.loadStack(action.stackId)
                if (stack == null) {
                    dispatch(ErrorRaised("This stack does not exist."))
                } else {
                    val file = buildExport(stack, storage.loadImages(stack.id))
                    env.download(exportFileName(stack.name), encodeExport(file))
                }
            }

            is ImportFileRead -> launch { import(action.text) }

            is ImageChosen -> launch {
                val stackId = (after.screen as? Screen.Editor)?.stack?.id ?: return@launch
                try {
                    storage.saveImage(stackId, action.imageId, action.dataUrl)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dispatch(ImageSaveFailed(action.cardId, action.face, action.imageId))
                }
            }

            is PlayLanguageChosen -> {
                val stack = (after.screen as? Screen.Play)?.stack
                if (stack != null && action.language in stack.languages) {
                    env.savePlayLanguage(stack.id, action.language)
                }
            }

            else -> {}
        }

        // Whatever the action was: if it changed the stack being edited, clean up and save.
        val editorBefore = before.screen as? Screen.Editor
        val editorAfter = after.screen as? Screen.Editor
        if (editorBefore != null && editorAfter != null && editorBefore.stack.id == editorAfter.stack.id) {
            val removedImages = editorBefore.images.keys - editorAfter.images.keys
            if (removedImages.isNotEmpty()) launch { removedImages.forEach { storage.deleteImage(it) } }
            if (editorBefore.stack != editorAfter.stack) scheduleAutosave()
        }
    }

    private suspend fun load(route: Route) {
        when (route) {
            Route.Home -> dispatch(StackListLoaded(storage.loadStackSummaries()))
            is Route.Edit -> {
                val stack = storage.loadStack(route.stackId)
                if (stack == null) dispatch(StackMissing(route.stackId))
                else dispatch(EditorLoaded(stack, storage.loadImages(stack.id)))
            }
            is Route.Play -> {
                val stack = storage.loadStack(route.stackId)
                if (stack == null) dispatch(StackMissing(route.stackId))
                else dispatch(PlayLoaded(stack, storage.loadImages(stack.id), env.loadPlayLanguage(stack.id)))
            }
            Route.Unknown -> {}
        }
    }

    private suspend fun import(text: String) {
        val file = try {
            parseImport(text)
        } catch (e: ImportException) {
            dispatch(ErrorRaised(e.message ?: "The file could not be imported."))
            return
        }
        val fresh = withFreshIds(file, env::newId)
        val taken = storage.loadStackSummaries().map { it.name }.toSet()
        val stack = fresh.stack.copy(name = uniqueName(fresh.stack.name, taken))
        storage.importStack(stack, fresh.images)
        dispatch(StackListLoaded(storage.loadStackSummaries()))
    }

    /** Saves once the user has stopped editing for [AUTOSAVE_DELAY_MS]. Each new edit restarts the wait. */
    private fun scheduleAutosave() {
        autosave?.cancel()
        autosave = scope.launch {
            delay(AUTOSAVE_DELAY_MS)
            val stack = (state().screen as? Screen.Editor)?.stack ?: return@launch
            try {
                storage.saveStack(stack)
                dispatch(StackSaved(stack))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dispatch(StackSaveFailed("Could not save: ${e.message}"))
            }
        }
    }

    /** Runs [block] in the background; a storage failure becomes an error banner. */
    private fun launch(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dispatch(ErrorRaised("Storage error: ${e.message}"))
            }
        }
    }
}

package dev.silas.flipcards

import dev.silas.flipcards.effects.Effects
import dev.silas.flipcards.effects.Env
import dev.silas.flipcards.state.Action
import dev.silas.flipcards.state.AppState
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.update
import dev.silas.flipcards.ui.patchEditor
import dev.silas.flipcards.ui.render
import kotlinx.coroutines.CoroutineScope
import org.w3c.dom.HTMLElement

/**
 * Holds the one [AppState] and is the only thing that changes it.
 *
 * Every action goes through [dispatch]: compute the new state, redraw the screen, start any async work.
 */
class Store(private val root: HTMLElement, env: Env, private val scope: CoroutineScope) {
    var state = AppState()
        private set

    private val effects = Effects(env, scope, { state }, ::dispatch)

    init {
        // Draw the initial "Loading…" screen; from here on dispatch() redraws when the state changes.
        render(root, state, ::dispatch, scope)
    }

    fun dispatch(action: Action) {
        val before = state
        state = update(before, action)
        if (!action.silent && state != before) {
            render(root, state, ::dispatch, scope)
        } else {
            // Silent actions skip the redraw, so the few things they change on screen are patched by hand.
            (state.screen as? Screen.Editor)?.let { patchEditor(it) }
        }
        effects.handle(action, before, state)
    }
}

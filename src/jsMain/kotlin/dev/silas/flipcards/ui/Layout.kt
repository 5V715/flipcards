package dev.silas.flipcards.ui

import dev.silas.flipcards.i18n.Strings
import dev.silas.flipcards.i18n.stringsFor
import dev.silas.flipcards.state.Action
import dev.silas.flipcards.state.AppState
import dev.silas.flipcards.state.ErrorDismissed
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.toHash
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.dom.clear
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import org.w3c.dom.HTMLElement

typealias Dispatch = (Action) -> Unit

const val SAVE_STATUS_ID = "save-status"

fun saveStatusText(saved: Boolean, strings: Strings): String = if (saved) strings.saved else strings.notSaved

/** Replaces everything inside [root] with the screen for [state]. */
fun render(root: HTMLElement, state: AppState, dispatch: Dispatch, scope: CoroutineScope) {
    // The redraw replaces every element. Remember which control had the focus, by id,
    // so that a keyboard user is not thrown back to the top of the page.
    val focusedId = (document.activeElement as? HTMLElement)
        ?.takeIf { root.contains(it) }?.id?.takeIf { it.isNotEmpty() }

    val strings = stringsFor(state.uiLanguage)
    document.documentElement?.setAttribute("lang", state.uiLanguage)
    root.clear()
    root.append {
        main("page") {
            state.error?.let { errorBanner(it, strings, dispatch) }
            when (val screen = state.screen) {
                Screen.Loading -> p("loading") { +strings.loading }
                is Screen.StackList -> stackListView(screen, state.uiLanguage, strings, dispatch, scope)
                is Screen.Editor -> editorView(screen, strings, dispatch, scope)
                is Screen.Play -> playView(screen, strings, dispatch)
                is Screen.NotFound -> notFound(screen.message, strings)
            }
        }
    }
    // If that control still exists it keeps the focus; otherwise the element the view marked gets it.
    val stillThere = focusedId?.let { document.getElementById(it) as? HTMLElement }?.takeIf { root.contains(it) }
    (stillThere ?: root.querySelector("[data-autofocus]") as? HTMLElement)?.focus()
}

fun renderFatal(root: HTMLElement, strings: Strings) {
    root.clear()
    root.append {
        main("page") {
            p("fatal") { +strings.storageUnavailable }
        }
    }
}

private fun FlowContent.errorBanner(message: String, strings: Strings, dispatch: Dispatch) {
    div("banner") {
        role = "alert"
        span("banner-text") { +message }
        button {
            type = ButtonType.button
            +strings.dismiss
            onClickFunction = { dispatch(ErrorDismissed) }
        }
    }
}

private fun FlowContent.notFound(message: String, strings: Strings) {
    p { +message }
    a(href = Route.Home.toHash()) { +strings.backToStacks }
}

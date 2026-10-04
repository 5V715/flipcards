package dev.silas.flipcards

import dev.silas.flipcards.browser.BrowserEnv
import dev.silas.flipcards.state.Navigate
import dev.silas.flipcards.state.parseRoute
import dev.silas.flipcards.storage.IndexedDbStorage
import dev.silas.flipcards.ui.renderFatal
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

fun main() {
    val root = document.getElementById("root") as HTMLElement
    val scope = MainScope()
    scope.launch {
        val storage = try {
            IndexedDbStorage.open()
        } catch (e: Throwable) {
            renderFatal(root)
            return@launch
        }
        val store = Store(root, BrowserEnv(storage), scope)
        // The URL hash is the source of truth for where the app is: links and the back button
        // only change the hash, and every change arrives here as a Navigate action.
        fun navigateToCurrentHash() = store.dispatch(Navigate(parseRoute(window.location.hash)))
        window.addEventListener("hashchange", { navigateToCurrentHash() })
        navigateToCurrentHash()
    }
}

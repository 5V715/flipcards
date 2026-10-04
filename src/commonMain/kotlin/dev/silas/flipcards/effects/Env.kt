package dev.silas.flipcards.effects

import dev.silas.flipcards.state.Route
import dev.silas.flipcards.storage.Storage

/** Everything the effects need from the outside world. The browser provides the real one. */
interface Env {
    val storage: Storage

    fun newId(): String

    /** Offers [text] to the user as a file download. */
    fun download(fileName: String, text: String)

    /** Changes the URL; the app then receives a Navigate action for the new route. */
    fun navigate(route: Route)

    fun loadPlayLanguage(stackId: String): String?

    fun savePlayLanguage(stackId: String, language: String)
}

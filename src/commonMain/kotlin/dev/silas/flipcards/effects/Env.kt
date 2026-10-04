package dev.silas.flipcards.effects

import dev.silas.flipcards.state.Route
import dev.silas.flipcards.storage.Storage

/** Everything the effects need from the outside world. The browser provides the real one. */
interface Env {
    val storage: Storage

    fun newId(): String

    /** Downloads the text at [url]. Throws when there is no answer or the answer is an error. */
    suspend fun fetchText(url: String): String

    /** Offers [text] to the user as a file download. */
    fun download(fileName: String, text: String)

    /** Changes the URL; the app then receives a Navigate action for the new route. */
    fun navigate(route: Route)

    fun loadPlayLanguage(stackId: String): String?

    fun savePlayLanguage(stackId: String, language: String)

    /** The best score per number of cards played. */
    fun loadBestScores(stackId: String): Map<Int, Int>

    fun saveBestScores(stackId: String, scores: Map<Int, Int>)

    /** The interface language picked last, or a guess from the browser; null for the default. */
    fun loadUiLanguage(): String?

    fun saveUiLanguage(code: String)
}

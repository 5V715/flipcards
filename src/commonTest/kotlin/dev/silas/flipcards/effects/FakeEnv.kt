package dev.silas.flipcards.effects

import dev.silas.flipcards.state.Route
import dev.silas.flipcards.storage.InMemoryStorage

class FakeEnv(override val storage: InMemoryStorage = InMemoryStorage()) : Env {
    val downloads = mutableListOf<Pair<String, String>>()
    val navigations = mutableListOf<Route>()
    val playLanguages = mutableMapOf<String, String>()
    val bestScores = mutableMapOf<String, Int>()
    private var ids = 0

    override fun newId(): String = "id${++ids}"

    override fun download(fileName: String, text: String) {
        downloads += fileName to text
    }

    override fun navigate(route: Route) {
        navigations += route
    }

    override fun loadPlayLanguage(stackId: String): String? = playLanguages[stackId]

    override fun savePlayLanguage(stackId: String, language: String) {
        playLanguages[stackId] = language
    }

    override fun loadBestScore(stackId: String): Int? = bestScores[stackId]

    override fun saveBestScore(stackId: String, score: Int) {
        bestScores[stackId] = score
    }
}

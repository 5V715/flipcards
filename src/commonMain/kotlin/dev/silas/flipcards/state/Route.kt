package dev.silas.flipcards.state

/** Where the app is, taken from the URL hash. */
sealed interface Route {
    data object Home : Route
    data class Edit(val stackId: String) : Route
    data class Play(val stackId: String) : Route
    data object Unknown : Route
}

private val stackRoute = Regex("^#/stack/([^/]+)/(edit|play)$")

fun parseRoute(hash: String): Route {
    if (hash == "" || hash == "#" || hash == "#/") return Route.Home
    val match = stackRoute.find(hash) ?: return Route.Unknown
    val (stackId, page) = match.destructured
    return if (page == "edit") Route.Edit(stackId) else Route.Play(stackId)
}

fun Route.toHash(): String = when (this) {
    Route.Home, Route.Unknown -> "#/"
    is Route.Edit -> "#/stack/$stackId/edit"
    is Route.Play -> "#/stack/$stackId/play"
}

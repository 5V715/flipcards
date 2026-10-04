package dev.silas.flipcards.state

/** The only place state changes. Pure: no browser, no storage, no randomness. */
fun update(state: AppState, action: Action): AppState = when (action) {
    is Navigate -> AppState(
        route = action.route,
        screen = if (action.route == Route.Unknown) Screen.NotFound("Page not found.") else Screen.Loading,
        error = null,
    )

    // Results of loading are ignored when the user has navigated elsewhere in the meantime.
    is StackListLoaded ->
        if (state.route == Route.Home) state.copy(screen = Screen.StackList(action.stacks)) else state

    is EditorLoaded ->
        if (state.route == Route.Edit(action.stack.id)) {
            state.copy(screen = Screen.Editor(action.stack, action.images, saved = true))
        } else {
            state
        }

    is PlayLoaded -> playLoaded(state, action)

    is StackMissing ->
        if (state.route == Route.Edit(action.stackId) || state.route == Route.Play(action.stackId)) {
            state.copy(screen = Screen.NotFound("This stack does not exist."))
        } else {
            state
        }

    is ErrorRaised -> state.copy(error = action.message)
    ErrorDismissed -> state.copy(error = null)

    NewStackRequested, is DeleteStackConfirmed, is ExportRequested, is ImportFileRead -> state

    is EditorAction -> updateEditor(state, action)
    is PlayAction -> updatePlay(state, action)
}

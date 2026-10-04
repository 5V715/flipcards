package dev.silas.flipcards.state

import dev.silas.flipcards.i18n.stringsFor

/** The only place state changes. Pure: no browser, no storage, no randomness. */
fun update(state: AppState, action: Action): AppState = when (action) {
    is Navigate -> AppState(
        route = action.route,
        screen = if (action.route == Route.Unknown) {
            Screen.NotFound(stringsFor(state.uiLanguage).pageNotFound)
        } else {
            Screen.Loading
        },
        error = null,
        uiLanguage = state.uiLanguage,
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
            state.copy(screen = Screen.NotFound(stringsFor(state.uiLanguage).stackMissing))
        } else {
            state
        }

    is ErrorRaised -> state.copy(error = action.message)
    ErrorDismissed -> state.copy(error = null)
    is UiLanguageChosen -> state.copy(uiLanguage = action.code)

    NewStackRequested, is DeleteStackConfirmed, is ExportRequested, is ImportFileRead -> state

    is EditorAction -> updateEditor(state, action)
    is PlayAction -> updatePlay(state, action)
}

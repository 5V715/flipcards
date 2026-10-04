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
    // An open list of samples stays open, e.g. when adding a sample reloads the stacks.
    is StackListLoaded ->
        if (state.route == Route.Home) {
            val list = state.screen as? Screen.StackList
            state.copy(screen = list?.copy(stacks = action.stacks) ?: Screen.StackList(action.stacks))
        } else {
            state
        }

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

    SamplesRequested -> withSamples(state) { SamplesPanel.Loading }
    SamplesClosed -> withSamples(state) { null }
    is SamplesLoaded -> withSamples(state) { if (it == SamplesPanel.Loading) SamplesPanel.Loaded(action.samples) else it }
    SamplesFailed -> withSamples(state) { null }.copy(error = stringsFor(state.uiLanguage).samplesUnavailable)
    is SampleChosen -> withLoadedSamples(state) { it.copy(adding = it.adding + action.sample.fileName) }
    is SampleAdded -> withLoadedSamples(state) {
        it.copy(adding = it.adding - action.fileName, added = it.added + action.fileName)
    }
    is SampleFailed -> withLoadedSamples(state) { it.copy(adding = it.adding - action.fileName) }

    is EditorAction -> updateEditor(state, action)
    is PlayAction -> updatePlay(state, action)
}

/** Changes the samples panel of the stack list; on any other screen nothing happens. */
private fun withSamples(state: AppState, change: (SamplesPanel?) -> SamplesPanel?): AppState {
    val list = state.screen as? Screen.StackList ?: return state
    return state.copy(screen = list.copy(samples = change(list.samples)))
}

private fun withLoadedSamples(state: AppState, change: (SamplesPanel.Loaded) -> SamplesPanel): AppState =
    withSamples(state) { if (it is SamplesPanel.Loaded) change(it) else it }

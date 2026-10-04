package dev.silas.flipcards.ui

import dev.silas.flipcards.browser.readFileText
import dev.silas.flipcards.i18n.Strings
import dev.silas.flipcards.i18n.uiLanguageOf
import dev.silas.flipcards.i18n.uiLanguages
import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.samples.SAMPLES_FOLDER_URL
import dev.silas.flipcards.samples.Sample
import dev.silas.flipcards.state.DeleteStackConfirmed
import dev.silas.flipcards.state.ExportRequested
import dev.silas.flipcards.state.ImportFileRead
import dev.silas.flipcards.state.NewStackRequested
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.SampleChosen
import dev.silas.flipcards.state.SamplesClosed
import dev.silas.flipcards.state.SamplesPanel
import dev.silas.flipcards.state.SamplesRequested
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.UiLanguageChosen
import dev.silas.flipcards.state.toHash
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.LI
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.ul
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement

fun FlowContent.stackListView(
    screen: Screen.StackList,
    uiLanguage: String,
    strings: Strings,
    dispatch: Dispatch,
    scope: CoroutineScope,
) {
    div("top-bar") {
        h1 { +"Flipcards" }
        uiLanguageSelect(uiLanguage, strings, dispatch)
    }
    div("actions") {
        button(classes = "primary") {
            type = ButtonType.button
            +strings.newStack
            onClickFunction = { dispatch(NewStackRequested) }
        }
        // The label looks like a button; the file input inside it is visually hidden but keyboard reachable.
        label("button file-button") {
            +strings.import
            input(type = InputType.file, classes = "visually-hidden") {
                accept = ".json,application/json"
                onChangeFunction = { event ->
                    val input = event.target as HTMLInputElement
                    val file = input.files?.item(0)
                    if (file != null) {
                        scope.launch {
                            val text = readFileText(file)
                            // Cleared so that choosing the same file again fires a change event.
                            input.value = ""
                            dispatch(ImportFileRead(text))
                        }
                    }
                }
            }
        }
        button {
            type = ButtonType.button
            id = "samples-toggle"
            attributes["aria-expanded"] = (screen.samples != null).toString()
            attributes["aria-controls"] = "samples"
            +strings.samples
            onClickFunction = { dispatch(if (screen.samples == null) SamplesRequested else SamplesClosed) }
        }
    }
    screen.samples?.let { samplesPanel(it, strings, dispatch) }
    if (screen.stacks.isEmpty()) {
        p("empty") { +strings.noStacks }
    } else {
        ul("stacks") {
            screen.stacks.forEach { stack -> li("stack") { stackItem(stack, strings, dispatch) } }
        }
    }
}

/** The interface languages by their own names. A code the app has no translation for shows as itself. */
private fun FlowContent.uiLanguageSelect(uiLanguage: String, strings: Strings, dispatch: Dispatch) {
    div("field language-field") {
        label {
            htmlFor = "ui-language"
            +strings.language
        }
        select {
            id = "ui-language"
            val current = uiLanguageOf(uiLanguage) ?: uiLanguage
            val codes = uiLanguages.keys + listOfNotNull(current.takeIf { it !in uiLanguages })
            codes.forEach { code ->
                option {
                    value = code
                    selected = code == current
                    +(uiLanguages[code]?.first ?: code)
                }
            }
            onChangeFunction = { dispatch(UiLanguageChosen((it.target as HTMLSelectElement).value)) }
        }
    }
}

/** The stack files in the repository's samples folder, each with a button that adds it. */
private fun FlowContent.samplesPanel(panel: SamplesPanel, strings: Strings, dispatch: Dispatch) {
    section("samples") {
        id = "samples"
        attributes["aria-live"] = "polite"
        h2 { +strings.samplesTitle }
        a(href = SAMPLES_FOLDER_URL, classes = "samples-source") {
            target = "_blank"
            rel = "noopener"
            +strings.samplesSource
        }
        when (panel) {
            SamplesPanel.Loading -> p("loading") { +strings.loadingSamples }
            is SamplesPanel.Loaded ->
                if (panel.samples.isEmpty()) {
                    p("empty") { +strings.noSamples }
                } else {
                    ul("sample-list") {
                        panel.samples.forEach { sample -> li("sample") { sampleItem(sample, panel, strings, dispatch) } }
                    }
                }
        }
    }
}

private fun LI.sampleItem(sample: Sample, panel: SamplesPanel.Loaded, strings: Strings, dispatch: Dispatch) {
    val adding = sample.fileName in panel.adding
    div("stack-info") {
        h3 { +sample.title }
        p("stack-meta") {
            +"${(sample.size + 1023) / 1024} KB"
            if (sample.fileName in panel.added) {
                +" · "
                span("added") { +"✓ ${strings.added}" }
            }
        }
    }
    button(classes = "primary") {
        type = ButtonType.button
        id = "add-sample-${sample.fileName}"
        // aria-disabled keeps the focus on the button while it downloads; a second click is ignored.
        if (adding) attributes["aria-disabled"] = "true"
        +(if (adding) strings.adding else strings.add)
        onClickFunction = { if (!adding) dispatch(SampleChosen(sample)) }
    }
}

private fun LI.stackItem(stack: StackSummary, strings: Strings, dispatch: Dispatch) {
    div("stack-info") {
        h2 { +stack.name }
        p("stack-meta") { +"${strings.cards(stack.cardCount)} · ${stack.languages.joinToString(", ")}" }
    }
    div("actions") {
        a(href = Route.Play(stack.id).toHash(), classes = "button primary") { +strings.play }
        a(href = Route.Edit(stack.id).toHash(), classes = "button") { +strings.edit }
        button {
            type = ButtonType.button
            +strings.export
            onClickFunction = { dispatch(ExportRequested(stack.id)) }
        }
        button(classes = "danger") {
            type = ButtonType.button
            +strings.delete
            onClickFunction = {
                if (window.confirm(strings.confirmDeleteStack(stack.name))) {
                    dispatch(DeleteStackConfirmed(stack.id))
                }
            }
        }
    }
}

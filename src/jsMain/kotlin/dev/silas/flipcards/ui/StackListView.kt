package dev.silas.flipcards.ui

import dev.silas.flipcards.browser.readFileText
import dev.silas.flipcards.i18n.Strings
import dev.silas.flipcards.i18n.uiLanguageOf
import dev.silas.flipcards.i18n.uiLanguages
import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.state.DeleteStackConfirmed
import dev.silas.flipcards.state.ExportRequested
import dev.silas.flipcards.state.ImportFileRead
import dev.silas.flipcards.state.NewStackRequested
import dev.silas.flipcards.state.Route
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
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
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
    }
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

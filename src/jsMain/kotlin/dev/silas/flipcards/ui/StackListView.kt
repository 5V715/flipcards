package dev.silas.flipcards.ui

import dev.silas.flipcards.browser.readFileText
import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.state.DeleteStackConfirmed
import dev.silas.flipcards.state.ExportRequested
import dev.silas.flipcards.state.ImportFileRead
import dev.silas.flipcards.state.NewStackRequested
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.Screen
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
import kotlinx.html.input
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import org.w3c.dom.HTMLInputElement

fun FlowContent.stackListView(screen: Screen.StackList, dispatch: Dispatch, scope: CoroutineScope) {
    h1 { +"Flipcards" }
    div("actions") {
        button(classes = "primary") {
            type = ButtonType.button
            +"New stack"
            onClickFunction = { dispatch(NewStackRequested) }
        }
        // The label looks like a button; the file input inside it is visually hidden but keyboard reachable.
        label("button file-button") {
            +"Import"
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
        p("empty") { +"No stacks yet. Create one or import a file." }
    } else {
        ul("stacks") {
            screen.stacks.forEach { stack -> li("stack") { stackItem(stack, dispatch) } }
        }
    }
}

private fun LI.stackItem(stack: StackSummary, dispatch: Dispatch) {
    div("stack-info") {
        h2 { +stack.name }
        val cards = if (stack.cardCount == 1) "1 card" else "${stack.cardCount} cards"
        p("stack-meta") { +"$cards · ${stack.languages.joinToString(", ")}" }
    }
    div("actions") {
        a(href = Route.Play(stack.id).toHash(), classes = "button primary") { +"Play" }
        a(href = Route.Edit(stack.id).toHash(), classes = "button") { +"Edit" }
        button {
            type = ButtonType.button
            +"Export"
            onClickFunction = { dispatch(ExportRequested(stack.id)) }
        }
        button(classes = "danger") {
            type = ButtonType.button
            +"Delete"
            onClickFunction = {
                if (window.confirm("Delete \"${stack.name}\" and all its cards?")) {
                    dispatch(DeleteStackConfirmed(stack.id))
                }
            }
        }
    }
}

package dev.silas.flipcards.ui

import dev.silas.flipcards.browser.downscaleToJpegDataUrl
import dev.silas.flipcards.browser.newId
import dev.silas.flipcards.i18n.Strings
import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Face
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.isComplete
import dev.silas.flipcards.model.side
import dev.silas.flipcards.state.CardAdded
import dev.silas.flipcards.state.CardDeleted
import dev.silas.flipcards.state.CardMoved
import dev.silas.flipcards.state.ImageChosen
import dev.silas.flipcards.state.ImageRejected
import dev.silas.flipcards.state.ImageRemoved
import dev.silas.flipcards.state.LanguageAdded
import dev.silas.flipcards.state.LanguageRemoved
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.SideRef
import dev.silas.flipcards.state.SideTextChanged
import dev.silas.flipcards.state.SideTextModeChanged
import dev.silas.flipcards.state.StackRenamed
import dev.silas.flipcards.state.toHash
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import kotlinx.html.js.onSubmitFunction
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.ul
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

private const val NEW_LANGUAGE_ID = "new-language"

fun incompleteBadgeId(cardId: String): String = "incomplete-$cardId"

/**
 * Brings the parts of the editor up to date that typing can change. Typing does not re-render
 * (the field would lose focus), so the store calls this after every silent action instead.
 */
fun patchEditor(screen: Screen.Editor, strings: Strings) {
    document.getElementById(SAVE_STATUS_ID)?.textContent = saveStatusText(screen.saved, strings)
    val first = screen.stack.languages.first()
    screen.stack.cards.forEach { card ->
        (document.getElementById(incompleteBadgeId(card.id)) as? HTMLElement)?.hidden = card.isComplete(first)
    }
}

fun FlowContent.editorView(screen: Screen.Editor, strings: Strings, dispatch: Dispatch, scope: CoroutineScope) {
    val stack = screen.stack

    div("top-bar") {
        a(href = Route.Home.toHash()) { +strings.stacksLink }
        a(href = Route.Play(stack.id).toHash(), classes = "button primary") { +strings.play }
    }
    h1("visually-hidden") { +strings.editStack }

    div("field") {
        label {
            htmlFor = "stack-name"
            +strings.stackName
        }
        input(type = InputType.text) {
            id = "stack-name"
            value = stack.name
            onInputFunction = { dispatch(StackRenamed((it.target as HTMLInputElement).value)) }
        }
    }
    // Typing does not re-render, so the store updates this text directly (see Store.updateSaveStatus).
    span("save-status") {
        id = SAVE_STATUS_ID
        attributes["aria-live"] = "polite"
        +saveStatusText(screen.saved, strings)
    }

    section("languages") {
        h2 { +strings.languages }
        ul("chips") {
            stack.languages.forEach { code ->
                li("chip") {
                    span { +code }
                    if (stack.languages.size > 1) {
                        button {
                            type = ButtonType.button
                            id = "remove-language-$code"
                            attributes["aria-label"] = strings.removeLanguage(code)
                            +"×"
                            onClickFunction = {
                                if (window.confirm(strings.confirmRemoveLanguage(code))) dispatch(LanguageRemoved(code))
                            }
                        }
                    }
                }
            }
        }
        // A form, so that pressing Enter in the field adds the language too.
        form(classes = "add-language") {
            onSubmitFunction = { event ->
                event.preventDefault()
                val input = document.getElementById(NEW_LANGUAGE_ID) as HTMLInputElement
                dispatch(LanguageAdded(input.value))
            }
            input(type = InputType.text) {
                id = NEW_LANGUAGE_ID
                placeholder = strings.languageCodePlaceholder
                attributes["aria-label"] = strings.languageCode
                attributes["autocapitalize"] = "off"
                attributes["autocomplete"] = "off"
            }
            button {
                type = ButtonType.submit
                +strings.addLanguage
            }
        }
    }

    stack.cards.forEachIndexed { index, card ->
        section("card-editor") {
            div("card-header") {
                h2 { +strings.cardNumber(index + 1) }
                // Always present and hidden for complete cards, so patchEditor() can toggle it.
                span("badge") {
                    id = incompleteBadgeId(card.id)
                    if (card.isComplete(stack.languages.first())) attributes["hidden"] = "hidden"
                    +strings.incomplete
                }
                div("actions") {
                    button {
                        type = ButtonType.button
                        id = "${card.id}-up"
                        attributes["aria-label"] = strings.moveUp
                        // aria-disabled, not disabled: the button stays focusable, so the focus is not
                        // lost when a card reaches the top. Moving past the end is ignored by update().
                        if (index == 0) attributes["aria-disabled"] = "true"
                        +"↑"
                        onClickFunction = { dispatch(CardMoved(card.id, -1)) }
                    }
                    button {
                        type = ButtonType.button
                        id = "${card.id}-down"
                        attributes["aria-label"] = strings.moveDown
                        if (index == stack.cards.lastIndex) attributes["aria-disabled"] = "true"
                        +"↓"
                        onClickFunction = { dispatch(CardMoved(card.id, 1)) }
                    }
                    button(classes = "danger") {
                        type = ButtonType.button
                        +strings.delete
                        onClickFunction = {
                            if (window.confirm(strings.confirmDeleteCard)) dispatch(CardDeleted(card.id))
                        }
                    }
                }
            }
            div("sides") {
                Face.entries.forEach { face -> sideEditor(screen, card, face, strings, dispatch, scope) }
            }
        }
    }

    div("actions") {
        button(classes = "primary") {
            type = ButtonType.button
            id = "add-card"
            +strings.addCard
            onClickFunction = { dispatch(CardAdded(newId())) }
        }
    }
}

private fun FlowContent.sideEditor(
    screen: Screen.Editor,
    card: Card,
    face: Face,
    strings: Strings,
    dispatch: Dispatch,
    scope: CoroutineScope,
) {
    val side = card.side(face)
    val text = side.text
    // Ids tie each label to its input; they only need to be unique on the page.
    val idPrefix = "${card.id}-${face.name.lowercase()}"

    fun FlowContent.textField(labelText: String, language: String?, value: String) {
        val inputId = "$idPrefix-text-${language ?: "same"}"
        div("field") {
            label {
                htmlFor = inputId
                +labelText
            }
            input(type = InputType.text) {
                id = inputId
                this.value = value
                onInputFunction = {
                    dispatch(SideTextChanged(card.id, face, language, (it.target as HTMLInputElement).value))
                }
            }
        }
    }

    fieldSet("side") {
        attributes["data-face"] = face.name.lowercase()
        legend { +(if (face == Face.FRONT) strings.front else strings.back) }

        label("checkbox") {
            input(type = InputType.checkBox) {
                id = "$idPrefix-translated"
                checked = text is SideText.Translated
                onChangeFunction = { event ->
                    val checkbox = event.target as HTMLInputElement
                    // One text for all languages keeps only the first language's text; ask before dropping the rest.
                    val lost = (text as? SideText.Translated)?.values.orEmpty()
                        .filter { (language, value) -> language != screen.stack.languages.first() && value.isNotBlank() }
                        .keys
                    if (checkbox.checked || lost.isEmpty() || window.confirm(strings.confirmOneText(lost.toList()))) {
                        dispatch(SideTextModeChanged(card.id, face, checkbox.checked))
                    } else {
                        checkbox.checked = true
                    }
                }
            }
            +strings.translated
        }

        if (text is SideText.Translated) {
            screen.stack.languages.forEach { language -> textField(language, language, text.values[language] ?: "") }
        } else {
            textField(strings.text, null, (text as? SideText.Same)?.value ?: "")
        }

        val imageUrl = side.imageId?.let { screen.images[it] }
        if (imageUrl != null) img(alt = "", src = imageUrl, classes = "side-image")

        div("actions") {
            label("button file-button") {
                +strings.chooseImage
                input(type = InputType.file, classes = "visually-hidden") {
                    id = "$idPrefix-image"
                    accept = "image/*"
                    onChangeFunction = { event ->
                        val file = (event.target as HTMLInputElement).files?.item(0)
                        if (file != null) {
                            scope.launch {
                                val dataUrl = downscaleToJpegDataUrl(file)
                                dispatch(
                                    if (dataUrl == null) ImageRejected(card.id, face)
                                    else ImageChosen(card.id, face, newId(), dataUrl),
                                )
                            }
                        }
                    }
                }
            }
            if (side.imageId != null) {
                button {
                    type = ButtonType.button
                    id = "$idPrefix-remove-image"
                    +strings.removeImage
                    onClickFunction = { dispatch(ImageRemoved(card.id, face)) }
                }
            }
        }

        if (screen.imageError == SideRef(card.id, face)) {
            p("field-error") { +strings.imageUnreadable }
        }
    }
}

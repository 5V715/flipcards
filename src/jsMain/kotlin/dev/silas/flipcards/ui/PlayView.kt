package dev.silas.flipcards.ui

import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.completeCards
import dev.silas.flipcards.model.resolveText
import dev.silas.flipcards.play.HintMode
import dev.silas.flipcards.play.currentCard
import dev.silas.flipcards.state.AnswerSubmitted
import dev.silas.flipcards.state.AnswerTyped
import dev.silas.flipcards.state.CardGraded
import dev.silas.flipcards.state.HintModeChosen
import dev.silas.flipcards.state.PlayLanguageChosen
import dev.silas.flipcards.state.PlayPhase
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.SessionStarted
import dev.silas.flipcards.state.toHash
import kotlinx.html.ButtonType
import kotlinx.html.CommonAttributeGroupFacade
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
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.ul
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import kotlin.random.Random

fun FlowContent.playView(screen: Screen.Play, dispatch: Dispatch) {
    when (val phase = screen.phase) {
        is PlayPhase.Setup -> setup(screen, phase, dispatch)
        is PlayPhase.Asking -> asking(screen, phase, dispatch)
        is PlayPhase.Revealed -> revealed(screen, phase, dispatch)
        is PlayPhase.Summary -> summary(screen, phase, dispatch)
    }
}

/** Marks the element that render() gives the focus to once the new screen is in place. */
private fun CommonAttributeGroupFacade.autofocus() {
    attributes["data-autofocus"] = "true"
}

/** A new seed per session: update() stays free of randomness, the click supplies it. */
private fun newSeed(): Long = Random.nextLong()

private fun FlowContent.languageSelect(screen: Screen.Play, current: String, dispatch: Dispatch) {
    div("field language-field") {
        label {
            htmlFor = "play-language"
            +"Language"
        }
        select {
            id = "play-language"
            screen.stack.languages.forEach { code ->
                option {
                    value = code
                    selected = code == current
                    +code
                }
            }
            onChangeFunction = { dispatch(PlayLanguageChosen((it.target as HTMLSelectElement).value)) }
        }
    }
}

/** One side of a card as the player sees it: the image, then the text in the play language. */
private fun FlowContent.cardFace(screen: Screen.Play, side: Side, language: String, revealed: Boolean) {
    div(if (revealed) "flipcard revealed" else "flipcard") {
        div("side") {
            side.imageId?.let { screen.images[it] }?.let { img(alt = "", src = it, classes = "side-image") }
            side.resolveText(language, screen.stack.languages.first())?.let { p("side-text") { +it } }
        }
    }
}

private fun FlowContent.setup(screen: Screen.Play, phase: PlayPhase.Setup, dispatch: Dispatch) {
    div("top-bar") {
        a(href = Route.Home.toHash()) { +"← Stacks" }
    }
    h1 { +screen.stack.name }
    languageSelect(screen, phase.language, dispatch)
    fieldSet("hint-modes") {
        legend { +"Hints" }
        listOf(
            HintMode.HINTED to "Hinted",
            HintMode.LENGTH_ONLY to "Length only",
            HintMode.NONE to "No hint",
        ).forEach { (mode, text) ->
            label("checkbox") {
                input(type = InputType.radio, name = "hint-mode") {
                    id = "hint-mode-${mode.name.lowercase()}"
                    checked = mode == phase.mode
                    onChangeFunction = { dispatch(HintModeChosen(mode)) }
                }
                +text
            }
        }
    }
    if (screen.stack.completeCards.isEmpty()) {
        p("empty") { +"This stack has no complete cards yet." }
        a(href = Route.Edit(screen.stack.id).toHash(), classes = "button") { +"Open the editor" }
    } else {
        div("actions") {
            button(classes = "primary") {
                type = ButtonType.button
                autofocus()
                +"Start"
                onClickFunction = { dispatch(SessionStarted(newSeed())) }
            }
        }
    }
}

private fun FlowContent.asking(screen: Screen.Play, phase: PlayPhase.Asking, dispatch: Dispatch) {
    val session = phase.session
    val card = session.currentCard(screen.stack)
    val backHasText = card.back.resolveText(session.language, screen.stack.languages.first()) != null

    div("play-bar") {
        p("progress") { +"${session.queue.size} of ${session.total} left" }
        languageSelect(screen, session.language, dispatch)
    }
    cardFace(screen, card.front, session.language, revealed = false)
    phase.hint?.let { p("hint") { +it } }
    form(classes = "answer-form") {
        onSubmitFunction = { event ->
            event.preventDefault()
            dispatch(AnswerSubmitted)
        }
        // An image cannot be typed, so such a card goes straight to revealing.
        if (backHasText) {
            input(type = InputType.text) {
                placeholder = "Your answer"
                attributes["aria-label"] = "Your answer"
                attributes["autocomplete"] = "off"
                attributes["autocapitalize"] = "off"
                attributes["spellcheck"] = "false"
                autofocus()
                value = phase.typed
                onInputFunction = { dispatch(AnswerTyped((it.target as HTMLInputElement).value)) }
            }
        }
        button(classes = "primary") {
            type = ButtonType.submit
            if (!backHasText) autofocus()
            +"Show answer"
        }
    }
}

private fun FlowContent.revealed(screen: Screen.Play, phase: PlayPhase.Revealed, dispatch: Dispatch) {
    val session = phase.session
    val card = session.currentCard(screen.stack)

    div("play-bar") {
        p("progress") { +"${session.queue.size} of ${session.total} left" }
        languageSelect(screen, session.language, dispatch)
    }
    cardFace(screen, card.back, session.language, revealed = true)
    if (phase.typed.isNotBlank()) p("your-answer") { +"Your answer: ${phase.typed}" }
    when (phase.suggestion) {
        true -> p("suggestion right") { +"Looks right" }
        false -> p("suggestion different") { +"Looks different" }
        null -> {}
    }
    // The suggested button gets the focus, so Enter accepts it; the player can always pick the other.
    div("actions grade") {
        button(classes = "primary") {
            type = ButtonType.button
            if (phase.suggestion == true) autofocus()
            +"Knew it"
            onClickFunction = { dispatch(CardGraded(true)) }
        }
        button {
            type = ButtonType.button
            if (phase.suggestion == false) autofocus()
            +"Didn't know"
            onClickFunction = { dispatch(CardGraded(false)) }
        }
    }
}

private fun FlowContent.summary(screen: Screen.Play, phase: PlayPhase.Summary, dispatch: Dispatch) {
    val result = phase.result
    val fallback = screen.stack.languages.first()
    fun Side.label(): String = resolveText(result.language, fallback) ?: "(image)"

    h1 { +"${result.knownFirstTime} of ${result.total} known first time" }
    if (result.missed.isNotEmpty()) {
        h2 { +"Missed cards" }
        ul("missed-cards") {
            result.missed.mapNotNull { id -> screen.stack.cards.find { it.id == id } }.forEach { card ->
                li("missed") { +"${card.front.label()} → ${card.back.label()}" }
            }
        }
    }
    div("actions") {
        button(classes = "primary") {
            type = ButtonType.button
            +"Play again"
            onClickFunction = { dispatch(SessionStarted(newSeed())) }
        }
        if (result.missed.isNotEmpty()) {
            button {
                type = ButtonType.button
                +"Play missed cards only"
                onClickFunction = { dispatch(SessionStarted(newSeed(), result.missed)) }
            }
        }
        a(href = Route.Home.toHash(), classes = "button") { +"Back to stacks" }
    }
}

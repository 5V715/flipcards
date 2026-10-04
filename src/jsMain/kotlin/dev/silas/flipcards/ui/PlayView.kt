package dev.silas.flipcards.ui

import dev.silas.flipcards.i18n.Strings
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.completeCards
import dev.silas.flipcards.model.resolveText
import dev.silas.flipcards.play.HintMode
import dev.silas.flipcards.play.Session
import dev.silas.flipcards.play.Slot
import dev.silas.flipcards.play.blankCount
import dev.silas.flipcards.play.currentCard
import dev.silas.flipcards.play.pointsPerCard
import dev.silas.flipcards.play.typedPerSlot
import dev.silas.flipcards.state.AnswerSubmitted
import dev.silas.flipcards.state.AnswerTyped
import dev.silas.flipcards.state.CardCountChosen
import dev.silas.flipcards.state.CardGraded
import dev.silas.flipcards.state.HintModeChosen
import dev.silas.flipcards.state.PlayLanguageChosen
import dev.silas.flipcards.state.PlayPhase
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.Screen
import dev.silas.flipcards.state.SecondLanguageChosen
import dev.silas.flipcards.state.SessionStarted
import dev.silas.flipcards.state.bestFor
import dev.silas.flipcards.state.toHash
import kotlin.random.Random
import kotlinx.browser.document
import kotlinx.html.ButtonType
import kotlinx.html.CommonAttributeGroupFacade
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.dom.create
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
import kotlinx.html.span
import kotlinx.html.ul
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement

fun FlowContent.playView(screen: Screen.Play, strings: Strings, dispatch: Dispatch) {
    when (val phase = screen.phase) {
        is PlayPhase.Setup -> setup(screen, phase, strings, dispatch)
        is PlayPhase.Asking -> asking(screen, phase, strings, dispatch)
        is PlayPhase.Revealed -> revealed(screen, phase, strings, dispatch)
        is PlayPhase.Summary -> summary(screen, phase, strings, dispatch)
    }
}

/** Marks the element that render() gives the focus to once the new screen is in place. */
private fun CommonAttributeGroupFacade.autofocus() {
    attributes["data-autofocus"] = "true"
}

/** A new seed per session: update() stays free of randomness, the click supplies it. */
private fun newSeed(): Long = Random.nextLong()

private fun FlowContent.languageSelect(screen: Screen.Play, current: String, strings: Strings, dispatch: Dispatch) {
    div("field language-field") {
        label {
            htmlFor = "play-language"
            +strings.language
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

/**
 * One side of a card as the player sees it: the image, then the text in the play language,
 * then, if it reads differently, the text in [secondLanguage].
 */
private fun FlowContent.cardFace(
    screen: Screen.Play,
    side: Side,
    language: String,
    revealed: Boolean,
    secondLanguage: String? = null,
) {
    val fallback = screen.stack.languages.first()
    div(if (revealed) "flipcard revealed" else "flipcard") {
        div("side") {
            side.imageId?.let { screen.images[it] }?.let { img(alt = "", src = it, classes = "side-image") }
            val text = side.resolveText(language, fallback)
            text?.let { p("side-text") { +it } }
            secondLanguage?.let { side.resolveText(it, fallback) }?.takeIf { it != text }?.let {
                p("side-text second") {
                    attributes["lang"] = secondLanguage
                    +it
                }
            }
        }
    }
}

private fun FlowContent.playBar(screen: Screen.Play, session: Session, strings: Strings, dispatch: Dispatch) {
    div("play-bar") {
        p("progress") { +strings.left(session.queue.size, session.total) }
        p("score") { +strings.score(session.score) }
        languageSelect(screen, session.language, strings, dispatch)
    }
}

const val ANSWER_INPUT_ID = "answer-input"
const val CARD_COUNT_ID = "card-count"
const val ANSWER_SLOTS_ID = "answer-slots"

/**
 * What the player has typed, drawn onto the blanks of [slots]. Without slots (no hints)
 * the typed text is shown on a single line. The actual typing goes into an invisible input on top.
 */
private fun DIV.answerSlotsContent(slots: List<Slot>?, typed: String) {
    if (slots == null) {
        span("typed") { +typed }
        span("caret") {}
        return
    }
    val chars = slots.typedPerSlot(typed)
    // The blank the next letter goes into.
    val current = slots.indices.filter { slots[it] == Slot.Blank }.getOrNull(typed.length)
    slots.forEachIndexed { i, slot ->
        when {
            slot is Slot.Fixed && slot.char.isWhitespace() -> span("slot gap") {}
            slot is Slot.Fixed -> span("slot fixed") { +slot.char.toString() }
            else -> {
                val char = chars[i]
                val classes = buildString {
                    append("slot blank")
                    if (char != null) append(" filled")
                    if (i == current) append(" current")
                }
                span(classes) { char?.let { +it.toString() } }
            }
        }
    }
}

private fun slotsClasses(slots: List<Slot>?) = if (slots == null) "answer-slots free" else "answer-slots"

/** Redraws the slots after typing, which is silent so the hidden input keeps the focus. */
fun patchPlay(screen: Screen.Play) {
    val phase = screen.phase as? PlayPhase.Asking ?: return
    val old = document.getElementById(ANSWER_SLOTS_ID) ?: return
    val fresh = document.create.div(slotsClasses(phase.hint)) {
        id = ANSWER_SLOTS_ID
        attributes["aria-hidden"] = "true"
        answerSlotsContent(phase.hint, phase.typed)
    }
    old.replaceWith(fresh)
    // The input may hold characters that cannot go into a blank, such as spaces; drop them there too.
    val input = document.getElementById(ANSWER_INPUT_ID) as? HTMLInputElement ?: return
    if (input.value != phase.typed) input.value = phase.typed
}

private fun FlowContent.setup(screen: Screen.Play, phase: PlayPhase.Setup, strings: Strings, dispatch: Dispatch) {
    val completeCount = screen.stack.completeCards.size
    div("top-bar") {
        a(href = Route.Home.toHash()) { +strings.stacksLink }
    }
    h1 { +screen.stack.name }
    screen.bestFor(phase.cardCount)?.let { p("best") { +strings.bestScore(it) } }
    languageSelect(screen, phase.language, strings, dispatch)
    if (screen.stack.languages.size > 1) {
        div("field language-field") {
            label {
                htmlFor = "second-language"
                +strings.alsoOnFront
            }
            select {
                id = "second-language"
                option {
                    value = ""
                    selected = phase.secondLanguage == null
                    +strings.nothing
                }
                screen.stack.languages.forEach { code ->
                    option {
                        value = code
                        selected = code == phase.secondLanguage
                        +code
                    }
                }
                onChangeFunction = {
                    val value = (it.target as HTMLSelectElement).value
                    dispatch(SecondLanguageChosen(value.ifEmpty { null }))
                }
            }
        }
    }
    fieldSet("hint-modes") {
        legend { +strings.hints }
        listOf(
            HintMode.HINTED to strings.hinted,
            HintMode.LENGTH_ONLY to strings.lengthOnly,
            HintMode.NONE to strings.noHint,
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
    if (completeCount > 1) {
        div("field card-count-field") {
            label {
                htmlFor = CARD_COUNT_ID
                +strings.cardsToPlay
            }
            div("card-count") {
                input(type = InputType.number) {
                    id = CARD_COUNT_ID
                    min = "1"
                    max = completeCount.toString()
                    attributes["inputmode"] = "numeric"
                    value = (phase.cardCount ?: completeCount).toString()
                    // A number that cannot be read is left alone; the next redraw shows the count in force.
                    onChangeFunction = {
                        (it.target as HTMLInputElement).value.toIntOrNull()?.let { count -> dispatch(CardCountChosen(count)) }
                    }
                }
                span { +strings.ofCards(completeCount) }
            }
        }
    }
    if (completeCount == 0) {
        p("empty") { +strings.noCompleteCards }
        a(href = Route.Edit(screen.stack.id).toHash(), classes = "button") { +strings.openEditor }
    } else {
        div("actions") {
            button(classes = "primary") {
                type = ButtonType.button
                autofocus()
                +strings.start
                onClickFunction = { dispatch(SessionStarted(newSeed())) }
            }
        }
    }
}

private fun FlowContent.asking(screen: Screen.Play, phase: PlayPhase.Asking, strings: Strings, dispatch: Dispatch) {
    val session = phase.session
    val card = session.currentCard(screen.stack)
    val backHasText = card.back.resolveText(session.language, screen.stack.languages.first()) != null

    playBar(screen, session, strings, dispatch)
    cardFace(screen, card.front, session.language, revealed = false, secondLanguage = session.secondLanguage)
    form(classes = "answer-form") {
        onSubmitFunction = { event ->
            event.preventDefault()
            dispatch(AnswerSubmitted)
        }
        // An image cannot be typed, so such a card goes straight to revealing.
        if (backHasText) {
            div("answer-entry") {
                div(slotsClasses(phase.hint)) {
                    id = ANSWER_SLOTS_ID
                    attributes["aria-hidden"] = "true"
                    answerSlotsContent(phase.hint, phase.typed)
                }
                // Invisible, on top of the slots: tapping them opens the keyboard, and typing lands here.
                input(type = InputType.text, classes = "answer-capture") {
                    id = ANSWER_INPUT_ID
                    attributes["aria-label"] = phase.hint?.let { strings.yourAnswerLetters(it.blankCount) }
                        ?: strings.yourAnswerLabel
                    attributes["autocomplete"] = "off"
                    attributes["autocorrect"] = "off"
                    attributes["autocapitalize"] = "off"
                    attributes["spellcheck"] = "false"
                    phase.hint?.let { attributes["maxlength"] = it.blankCount.toString() }
                    autofocus()
                    value = phase.typed
                    onInputFunction = { dispatch(AnswerTyped((it.target as HTMLInputElement).value)) }
                }
            }
        }
        button(classes = "primary") {
            type = ButtonType.submit
            if (!backHasText) autofocus()
            +strings.showAnswer
        }
    }
}

private fun FlowContent.revealed(screen: Screen.Play, phase: PlayPhase.Revealed, strings: Strings, dispatch: Dispatch) {
    val session = phase.session
    val card = session.currentCard(screen.stack)

    playBar(screen, session, strings, dispatch)
    cardFace(screen, card.back, session.language, revealed = true)
    if (phase.typed.isNotBlank()) p("your-answer") { +strings.yourAnswer(phase.typed) }
    when (phase.suggestion) {
        true -> p("suggestion right") { +strings.looksRight }
        false -> p("suggestion different") { +strings.looksDifferent }
        null -> {}
    }
    // The suggested button gets the focus, so Enter accepts it; the player can always pick the other.
    div("actions grade") {
        button(classes = "primary") {
            type = ButtonType.button
            if (phase.suggestion == true) autofocus()
            +strings.knewIt
            onClickFunction = { dispatch(CardGraded(true)) }
        }
        button {
            type = ButtonType.button
            if (phase.suggestion == false) autofocus()
            +strings.didntKnow
            onClickFunction = { dispatch(CardGraded(false)) }
        }
    }
}

private fun FlowContent.summary(screen: Screen.Play, phase: PlayPhase.Summary, strings: Strings, dispatch: Dispatch) {
    val result = phase.result
    val fallback = screen.stack.languages.first()
    fun Side.label(): String = resolveText(result.language, fallback) ?: strings.image

    h1 { +strings.known(result.known, result.total) }
    p("score final") { +strings.finalScore(result.score, result.total * pointsPerCard(result.mode)) }
    when {
        !result.countsForBest -> p("best") { +strings.missedOnlyDoesNotCount }
        result.score > (phase.previousBest ?: 0) -> p("best new") { +strings.newBest }
        else -> screen.bestScores[result.total]?.let { p("best") { +strings.bestScore(it) } }
    }
    if (result.missed.isNotEmpty()) {
        h2 { +strings.missedCards }
        ul("missed-cards") {
            result.missed.mapNotNull { id -> screen.stack.cards.find { it.id == id } }.forEach { card ->
                li("missed") { +"${card.front.label()} → ${card.back.label()}" }
            }
        }
    }
    div("actions") {
        button(classes = "primary") {
            type = ButtonType.button
            +strings.playAgain
            onClickFunction = { dispatch(SessionStarted(newSeed())) }
        }
        if (result.missed.isNotEmpty()) {
            button {
                type = ButtonType.button
                +strings.playMissedOnly
                onClickFunction = { dispatch(SessionStarted(newSeed(), result.missed)) }
            }
        }
        a(href = Route.Home.toHash(), classes = "button") { +strings.backToStacks }
    }
}

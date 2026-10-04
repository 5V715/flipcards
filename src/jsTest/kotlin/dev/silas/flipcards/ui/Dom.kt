package dev.silas.flipcards.ui

import dev.silas.flipcards.state.Action
import dev.silas.flipcards.state.AppState
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.EventInit

/** A rendered screen plus the actions its event handlers dispatched. */
class Mounted(val root: HTMLElement, val dispatched: MutableList<Action>) {
    val text: String get() = root.textContent ?: ""

    fun all(selector: String): List<Element> = root.querySelectorAll(selector).asList().map { it as Element }

    fun one(selector: String): HTMLElement = root.querySelector(selector) as HTMLElement

    fun exists(selector: String): Boolean = root.querySelector(selector) != null

    fun button(label: String): HTMLElement =
        all("button").first { it.textContent?.trim() == label } as HTMLElement

    fun hasButton(label: String): Boolean = all("button").any { it.textContent?.trim() == label }

    /** The input, select or textarea belonging to the label with this text. */
    fun field(label: String, within: Element = root): HTMLElement {
        val labelElement = within.querySelectorAll("label").asList()
            .map { it as HTMLElement }
            .first { it.textContent?.trim() == label }
        val id = labelElement.getAttribute("for")
        return (if (id != null) document.getElementById(id) else labelElement.querySelector("input, select"))
            as HTMLElement
    }

    fun type(input: HTMLElement, value: String) {
        (input as HTMLInputElement).value = value
        input.dispatchEvent(Event("input", EventInit(bubbles = true)))
    }
}

/**
 * An empty element attached to the document, so that labels, focus and getElementById behave as in the app.
 * Roots of earlier tests are removed first: all tests share one page, and their leftovers would
 * otherwise answer lookups by id.
 */
fun freshRoot(): HTMLElement {
    document.querySelectorAll(".test-root").asList().forEach { (it as Element).remove() }
    val root = document.createElement("div") as HTMLElement
    root.className = "test-root"
    document.body!!.appendChild(root)
    return root
}

fun mount(state: AppState): Mounted {
    val root = freshRoot()
    val dispatched = mutableListOf<Action>()
    render(root, state, { dispatched += it }, MainScope())
    return Mounted(root, dispatched)
}

/** Makes window.confirm answer [answer] without showing a dialog, and records the question. */
fun stubConfirm(answer: Boolean): MutableList<String> {
    val questions = mutableListOf<String>()
    window.asDynamic().confirm = { question: String ->
        questions += question
        answer
    }
    return questions
}

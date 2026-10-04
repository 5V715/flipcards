package dev.silas.flipcards

import kotlinx.browser.document
import kotlinx.html.dom.append
import kotlinx.html.h1
import org.w3c.dom.HTMLElement

fun main() {
    val root = document.getElementById("root") as HTMLElement
    root.append { h1 { +"Flipcards" } }
}

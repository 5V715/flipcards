package dev.silas.flipcards.browser

import dev.silas.flipcards.effects.Env
import dev.silas.flipcards.i18n.uiLanguageOf
import dev.silas.flipcards.model.FlipJson
import dev.silas.flipcards.state.Route
import dev.silas.flipcards.state.toHash
import dev.silas.flipcards.storage.Storage
import kotlin.coroutines.resume
import kotlin.js.Promise
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File

fun newId(): String {
    val crypto = window.asDynamic().crypto
    // randomUUID only exists on https and localhost, not when a phone opens the dev server over plain http.
    return if (crypto?.randomUUID != undefined) {
        crypto.randomUUID() as String
    } else {
        List(4) { Random.nextInt().toUInt().toString(16).padStart(8, '0') }.joinToString("")
    }
}

/** Starts a download of [text] as a file, by clicking a temporary link to an in-memory blob. */
fun downloadText(fileName: String, text: String) {
    val url = URL.createObjectURL(Blob(arrayOf(text), BlobPropertyBag(type = "application/json")))
    val link = document.createElement("a") as HTMLAnchorElement
    link.href = url
    link.download = fileName
    document.body!!.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
}

suspend fun readFileText(file: File): String = (file.asDynamic().text() as Promise<String>).await()

/**
 * Shrinks an image so its longest side is at most [maxSide] pixels and returns it as a JPEG data URL.
 * Returns null if the browser cannot read the file as an image.
 */
suspend fun downscaleToJpegDataUrl(file: File, maxSide: Int = 800, quality: Double = 0.85): String? {
    val url = URL.createObjectURL(file)
    try {
        val image = loadImage(url) ?: return null
        val width = image.naturalWidth
        val height = image.naturalHeight
        if (width == 0 || height == 0) return null
        val scale = min(1.0, maxSide.toDouble() / max(width, height))
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = max(1, (width * scale).roundToInt())
        canvas.height = max(1, (height * scale).roundToInt())
        val context = canvas.getContext("2d") as CanvasRenderingContext2D
        // JPEG has no transparency; without this, transparent areas would turn black.
        context.fillStyle = "#ffffff"
        context.fillRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
        context.drawImage(image, 0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
        return canvas.toDataURL("image/jpeg", quality)
    } finally {
        URL.revokeObjectURL(url)
    }
}

private suspend fun loadImage(url: String): HTMLImageElement? = suspendCancellableCoroutine { continuation ->
    val image = document.createElement("img") as HTMLImageElement
    image.onload = { continuation.resume(image) }
    image.onerror = { _, _, _, _, _ -> continuation.resume(null) }
    image.src = url
}

private const val UI_LANGUAGE_KEY = "flipcards.uiLanguage"

/** The interface language picked last; before the first pick, the browser's language if the app has it. */
fun loadStoredUiLanguage(): String? {
    val stored = try {
        localStorage.getItem(UI_LANGUAGE_KEY)
    } catch (e: Throwable) {
        null
    }
    return stored ?: uiLanguageOf(window.navigator.language)
}

/** The real [Env]: IndexedDB, downloads, the URL hash and localStorage. */
class BrowserEnv(override val storage: Storage) : Env {
    override fun newId(): String = dev.silas.flipcards.browser.newId()

    override suspend fun fetchText(url: String): String {
        val response = window.fetch(url).await()
        if (!response.ok) throw IllegalStateException("HTTP ${response.status}")
        return response.text().await()
    }

    override fun download(fileName: String, text: String) = downloadText(fileName, text)

    override fun navigate(route: Route) {
        window.location.hash = route.toHash()
    }

    // localStorage can be blocked in private modes; the remembered language is only a convenience.
    override fun loadPlayLanguage(stackId: String): String? =
        try {
            localStorage.getItem(playLanguageKey(stackId))
        } catch (e: Throwable) {
            null
        }

    override fun savePlayLanguage(stackId: String, language: String) {
        try {
            localStorage.setItem(playLanguageKey(stackId), language)
        } catch (e: Throwable) {
        }
    }

    override fun loadBestScores(stackId: String): Map<Int, Int> =
        try {
            localStorage.getItem(bestScoresKey(stackId))?.let { FlipJson.decodeFromString<Map<Int, Int>>(it) }.orEmpty()
        } catch (e: Throwable) {
            emptyMap()
        }

    override fun saveBestScores(stackId: String, scores: Map<Int, Int>) {
        try {
            localStorage.setItem(bestScoresKey(stackId), FlipJson.encodeToString(scores))
        } catch (e: Throwable) {
        }
    }

    override fun loadUiLanguage(): String? = loadStoredUiLanguage()

    override fun saveUiLanguage(code: String) {
        try {
            localStorage.setItem(UI_LANGUAGE_KEY, code)
        } catch (e: Throwable) {
        }
    }

    private fun playLanguageKey(stackId: String) = "flipcards.playLanguage.$stackId"

    private fun bestScoresKey(stackId: String) = "flipcards.bestScores.$stackId"
}

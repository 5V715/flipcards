package dev.silas.flipcards.transfer

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.FlipJson
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.completeCards
import dev.silas.flipcards.model.isComplete
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

const val EXPORT_FORMAT = "flipcards-stack"
const val EXPORT_VERSION = 1

/** The content of an exported `.flipcards.json` file: one stack and the images its cards use. */
@Serializable
data class ExportFile(
    val format: String = EXPORT_FORMAT,
    val version: Int = EXPORT_VERSION,
    val stack: Stack,
    /** Image id to data URL. */
    val images: Map<String, String>,
)

class ImportException(message: String) : Exception(message)

// format and version have default values, which are only written when encodeDefaults is on.
private val exportJson = Json(FlipJson) { encodeDefaults = true }

private val Card.imageIds: List<String> get() = listOfNotNull(front.imageId, back.imageId)

/** Incomplete cards are left out, and so are images no exported card uses. */
fun buildExport(stack: Stack, images: Map<String, String>): ExportFile {
    val cards = stack.completeCards
    val used = cards.flatMap { it.imageIds }.toSet()
    return ExportFile(stack = stack.copy(cards = cards), images = images.filterKeys { it in used })
}

fun encodeExport(file: ExportFile): String = exportJson.encodeToString(file)

private const val NOT_A_STACK_FILE = "This is not a Flipcards stack file."

/** Checks the whole file before anything is stored. Throws [ImportException] with a message for the user. */
fun parseImport(text: String): ExportFile {
    val json = try {
        FlipJson.parseToJsonElement(text) as? JsonObject
    } catch (e: Exception) {
        null
    } ?: throw ImportException(NOT_A_STACK_FILE)

    if ((json["format"] as? JsonPrimitive)?.content != EXPORT_FORMAT) throw ImportException(NOT_A_STACK_FILE)
    val version = (json["version"] as? JsonPrimitive)?.intOrNull ?: throw ImportException(NOT_A_STACK_FILE)
    // Checked before decoding: a newer format may not fit this version's model at all.
    if (version > EXPORT_VERSION) throw ImportException("This file was made by a newer version of the app.")
    if (version < 1) throw ImportException(NOT_A_STACK_FILE)

    val file = try {
        FlipJson.decodeFromJsonElement<ExportFile>(json)
    } catch (e: Exception) {
        throw ImportException(NOT_A_STACK_FILE)
    }
    validate(file)
    return file
}

private fun validate(file: ExportFile) {
    val stack = file.stack
    if (stack.name.isBlank()) throw ImportException("The stack has no name.")
    if (stack.languages.isEmpty()) throw ImportException("The stack has no languages.")
    // Codes with surrounding spaces are refused too: " en" would later sit next to a typed "en".
    val codes = stack.languages
    if (codes.any { it.isEmpty() || it != it.trim() } || codes.toSet().size != codes.size) {
        throw ImportException("The stack has invalid language codes.")
    }
    stack.cards.forEachIndexed { index, card ->
        val number = index + 1
        if (!card.isComplete(stack.languages.first())) throw ImportException("Card $number is incomplete.")
        if (card.imageIds.any { it !in file.images }) throw ImportException("Card $number refers to a missing image.")
    }
    // Only embedded images are allowed, so a file cannot bring in links or script URLs.
    if (file.images.values.any { !it.startsWith("data:image/") }) {
        throw ImportException("The file contains invalid image data.")
    }
}

/** Gives the stack, its cards and its images new ids, so an import never overwrites anything. */
fun withFreshIds(file: ExportFile, newId: () -> String): ExportFile {
    val images = mutableMapOf<String, String>()
    fun Side.fresh(): Side {
        val oldId = imageId ?: return this
        val id = newId()
        images[id] = file.images.getValue(oldId)
        return copy(imageId = id)
    }
    val cards = file.stack.cards.map { Card(newId(), it.front.fresh(), it.back.fresh()) }
    return file.copy(stack = file.stack.copy(id = newId(), cards = cards), images = images)
}

/** "Name", or "Name (2)", "Name (3)", ... if that is already taken. */
fun uniqueName(name: String, taken: Set<String>): String {
    if (name !in taken) return name
    var n = 2
    while ("$name ($n)" in taken) n++
    return "$name ($n)"
}

private val notFileNameSafe = Regex("[^a-z0-9]+")

fun exportFileName(stackName: String): String {
    val slug = stackName.lowercase().replace(notFileNameSafe, "-").trim('-')
    return slug.ifEmpty { "stack" } + ".flipcards.json"
}

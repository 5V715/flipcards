package dev.silas.flipcards.samples

import dev.silas.flipcards.model.FlipJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The samples folder on the main branch, as GitHub's API lists it. New files there show up in the app. */
const val SAMPLES_LISTING_URL = "https://api.github.com/repos/5V715/flipcards/contents/samples?ref=main"

/** Where people can look at the samples themselves. */
const val SAMPLES_FOLDER_URL = "https://github.com/5V715/flipcards/tree/main/samples"

private const val STACK_FILE_SUFFIX = ".flipcards.json"

/** A stack file in the samples folder. [url] is where its content can be downloaded. */
data class Sample(val fileName: String, val url: String, val size: Long) {
    /** "country-shapes.flipcards.json" becomes "Country shapes". */
    val title: String
        get() = fileName.removeSuffix(STACK_FILE_SUFFIX).replace('-', ' ').replace('_', ' ')
            .replaceFirstChar { it.uppercaseChar() }
}

@Serializable
private data class ListingEntry(
    val name: String,
    val type: String,
    @SerialName("download_url") val downloadUrl: String? = null,
    val size: Long = 0,
)

/** The stack files in a GitHub folder listing, sorted by name. Other files and folders are left out. */
fun parseSampleListing(json: String): List<Sample> =
    FlipJson.decodeFromString<List<ListingEntry>>(json)
        .filter { it.type == "file" && it.name.endsWith(STACK_FILE_SUFFIX) && it.downloadUrl != null }
        .map { Sample(it.name, it.downloadUrl!!, it.size) }
        .sortedBy { it.fileName }

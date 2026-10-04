package dev.silas.flipcards.samples

import kotlin.test.Test
import kotlin.test.assertEquals

class SamplesTest {
    private val listing = """
        [
          {"name": "spanish-basic-words.flipcards.json", "type": "file", "size": 10700,
           "download_url": "https://raw.example/spanish-basic-words.flipcards.json", "sha": "x"},
          {"name": "README.md", "type": "file", "size": 10, "download_url": "https://raw.example/README.md"},
          {"name": "tools", "type": "dir", "size": 0, "download_url": null},
          {"name": "country-shapes.flipcards.json", "type": "file", "size": 435000,
           "download_url": "https://raw.example/country-shapes.flipcards.json"}
        ]
    """.trimIndent()

    @Test fun onlyStackFilesSortedByName() =
        assertEquals(
            listOf(
                Sample("country-shapes.flipcards.json", "https://raw.example/country-shapes.flipcards.json", 435000),
                Sample("spanish-basic-words.flipcards.json", "https://raw.example/spanish-basic-words.flipcards.json", 10700),
            ),
            parseSampleListing(listing),
        )

    @Test fun titleComesFromTheFileName() {
        assertEquals("Country shapes", Sample("country-shapes.flipcards.json", "", 0).title)
        assertEquals("My new set", Sample("my-new_set.flipcards.json", "", 0).title)
    }
}

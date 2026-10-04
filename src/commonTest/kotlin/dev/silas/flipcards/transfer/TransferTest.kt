package dev.silas.flipcards.transfer

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TransferTest {
    private val vienna = Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien")))
    private val stack = Stack(
        "s", "Capitals", listOf("en", "de"),
        listOf(
            Card("a", Side(imageId = "i1"), vienna),
            Card("b", Side(SideText.Same("x")), Side()),
        ),
    )
    private val images = mapOf("i1" to "data:image/jpeg;base64,AAAA", "unused" to "data:image/jpeg;base64,BBBB")
    private val valid = buildExport(stack, images)

    private fun messageFor(text: String) = assertFailsWith<ImportException> { parseImport(text) }.message
    private fun messageFor(file: ExportFile) = messageFor(encodeExport(file))

    @Test fun exportDropsIncompleteCardsAndUnusedImages() {
        assertEquals(listOf("a"), valid.stack.cards.map { it.id })
        assertEquals(setOf("i1"), valid.images.keys)
    }

    @Test fun encodedFileCarriesFormatAndVersion() {
        val text = encodeExport(valid)
        assertTrue("\"format\":\"flipcards-stack\"" in text && "\"version\":1" in text)
    }

    @Test fun exportThenImportIsEqual() = assertEquals(valid, parseImport(encodeExport(valid)))

    @Test fun freshIdsReplaceEveryIdAndKeepLinks() {
        var n = 0
        val fresh = withFreshIds(valid) { "new${n++}" }
        assertNotEquals("s", fresh.stack.id)
        assertNotEquals("a", fresh.stack.cards[0].id)
        val imageId = fresh.stack.cards[0].front.imageId!!
        assertNotEquals("i1", imageId)
        assertEquals("data:image/jpeg;base64,AAAA", fresh.images[imageId])
        assertEquals(1, fresh.images.size)
    }

    @Test fun uniqueNames() {
        assertEquals("Capitals", uniqueName("Capitals", emptySet()))
        assertEquals("Capitals (2)", uniqueName("Capitals", setOf("Capitals")))
        assertEquals("Capitals (3)", uniqueName("Capitals", setOf("Capitals", "Capitals (2)")))
    }

    @Test fun fileNames() {
        assertEquals("spanish-easy-words.flipcards.json", exportFileName("Spanish easy words"))
        assertEquals("stack.flipcards.json", exportFileName("  ??  "))
    }

    @Test fun rejectsGarbage() {
        val notAFile = "This is not a Flipcards stack file."
        assertEquals(notAFile, messageFor("hello"))
        assertEquals(notAFile, messageFor("[]"))
        assertEquals(notAFile, messageFor("{}"))
        assertEquals(notAFile, messageFor(valid.copy(format = "other")))
        assertEquals(notAFile, messageFor(valid.copy(version = 0)))
        assertEquals(notAFile, messageFor("""{"format":"flipcards-stack","version":1,"stack":42}"""))
    }

    @Test fun rejectsNewerVersion() =
        assertEquals(
            "This file was made by a newer version of the app.",
            messageFor("""{"format":"flipcards-stack","version":2,"somethingNew":true}"""),
        )

    @Test fun rejectsBlankName() =
        assertEquals("The stack has no name.", messageFor(valid.copy(stack = valid.stack.copy(name = "  "))))

    @Test fun rejectsNoLanguages() =
        assertEquals(
            "The stack has no languages.",
            messageFor(valid.copy(stack = valid.stack.copy(languages = emptyList()))),
        )

    @Test fun rejectsBadLanguageCodes() { // Review Focus 5
        val invalid = "The stack has invalid language codes."
        assertEquals(invalid, messageFor(valid.copy(stack = valid.stack.copy(languages = listOf("en", " ")))))
        assertEquals(invalid, messageFor(valid.copy(stack = valid.stack.copy(languages = listOf("en", "en ")))))
    }

    @Test fun rejectsIncompleteCard() {
        val cards = valid.stack.cards + Card("b", Side(SideText.Same("x")), Side())
        assertEquals("Card 2 is incomplete.", messageFor(valid.copy(stack = valid.stack.copy(cards = cards))))
    }

    @Test fun rejectsMissingImage() =
        assertEquals("Card 1 refers to a missing image.", messageFor(valid.copy(images = emptyMap())))

    @Test fun rejectsNonImageData() {
        val invalid = "The file contains invalid image data."
        assertEquals(invalid, messageFor(valid.copy(images = mapOf("i1" to "javascript:alert(1)"))))
        assertEquals(invalid, messageFor(valid.copy(images = mapOf("i1" to "https://example.com/a.png"))))
    }
}

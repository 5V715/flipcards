package dev.silas.flipcards.state

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Face
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorUpdateTest {
    private val stack = Stack(
        "s1", "S", listOf("en", "de"),
        listOf(
            Card(
                "a",
                Side(SideText.Same("x"), imageId = "i1"),
                Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien"))),
            ),
            Card("b"),
            Card("c"),
        ),
    )
    private val images = mapOf("i1" to "data:image/jpeg;base64,AA")

    private fun editing(stack: Stack, images: Map<String, String> = emptyMap()) =
        AppState(Route.Edit(stack.id), Screen.Editor(stack, images, saved = true))

    private val AppState.editor get() = screen as Screen.Editor

    @Test fun renameMarksUnsaved() {
        val s = update(editing(stack), StackRenamed("New"))
        assertEquals("New", s.editor.stack.name)
        assertFalse(s.editor.saved)
    }

    @Test fun addLanguage() =
        assertEquals(listOf("en", "de", "es"), update(editing(stack), LanguageAdded(" es ")).editor.stack.languages)

    @Test fun ignoresBlankAndDuplicateLanguages() { // Review Focus 5
        for (code in listOf("", "  ", "en", " en ")) {
            val s = update(editing(stack), LanguageAdded(code))
            assertEquals(listOf("en", "de"), s.editor.stack.languages)
            assertTrue(s.editor.saved)
        }
    }

    @Test fun removeLanguageDropsItsTexts() {
        val s = update(editing(stack), LanguageRemoved("de")).editor.stack
        assertEquals(listOf("en"), s.languages)
        assertEquals(SideText.Translated(mapOf("en" to "Vienna")), s.cards[0].back.text)
    }

    @Test fun cannotRemoveLastLanguage() {
        val one = stack.copy(languages = listOf("en"))
        assertEquals(editing(one), update(editing(one), LanguageRemoved("en")))
    }

    @Test fun addDeleteMoveCards() {
        assertEquals(
            listOf("a", "b", "c", "d"),
            update(editing(stack), CardAdded("d")).editor.stack.cards.map { it.id },
        )
        assertEquals(listOf("b", "a", "c"), update(editing(stack), CardMoved("a", 1)).editor.stack.cards.map { it.id })
        assertEquals(editing(stack), update(editing(stack), CardMoved("a", -1)))
        assertEquals(editing(stack), update(editing(stack), CardMoved("c", 1)))
        val deleted = update(editing(stack, images), CardDeleted("a")).editor
        assertEquals(listOf("b", "c"), deleted.stack.cards.map { it.id })
        assertEquals(emptyMap(), deleted.images)
    }

    @Test fun switchTextMode() {
        val toTranslated = update(editing(stack), SideTextModeChanged("a", Face.FRONT, true)).editor.stack.cards[0].front
        assertEquals(SideText.Translated(mapOf("en" to "x")), toTranslated.text)
        val toSame = update(editing(stack), SideTextModeChanged("a", Face.BACK, false)).editor.stack.cards[0].back
        assertEquals(SideText.Same("Vienna"), toSame.text)
        assertEquals(
            SideText.Same(""),
            update(editing(stack), SideTextModeChanged("b", Face.FRONT, false)).editor.stack.cards[1].front.text,
        )
        assertEquals(
            SideText.Translated(emptyMap()),
            update(editing(stack), SideTextModeChanged("b", Face.FRONT, true)).editor.stack.cards[1].front.text,
        )
    }

    @Test fun editTexts() {
        assertEquals(
            SideText.Same("y"),
            update(editing(stack), SideTextChanged("a", Face.FRONT, null, "y")).editor.stack.cards[0].front.text,
        )
        assertEquals(
            SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien!")),
            update(editing(stack), SideTextChanged("a", Face.BACK, "de", "Wien!")).editor.stack.cards[0].back.text,
        )
        assertEquals(
            SideText.Translated(mapOf("de" to "Haus")),
            update(editing(stack), SideTextChanged("b", Face.FRONT, "de", "Haus")).editor.stack.cards[1].front.text,
        )
    }

    @Test fun chooseImageReplacesPrevious() {
        val s = update(editing(stack, images), ImageChosen("a", Face.FRONT, "i2", "data:image/jpeg;base64,CC")).editor
        assertEquals("i2", s.stack.cards[0].front.imageId)
        assertEquals(mapOf("i2" to "data:image/jpeg;base64,CC"), s.images)
        assertFalse(s.saved)
    }

    @Test fun removeImage() {
        val s = update(editing(stack, images), ImageRemoved("a", Face.FRONT)).editor
        assertNull(s.stack.cards[0].front.imageId)
        assertEquals(emptyMap(), s.images)
    }

    @Test fun rejectedImageShowsErrorOnThatSide() {
        val rejected = update(editing(stack), ImageRejected("b", Face.BACK))
        assertEquals(SideRef("b", Face.BACK), rejected.editor.imageError)
        assertTrue(rejected.editor.saved)
        val chosen = update(rejected, ImageChosen("b", Face.BACK, "i3", "data:image/jpeg;base64,DD"))
        assertNull(chosen.editor.imageError)
    }

    @Test fun failedImageSaveUnlinksTheImage() { // Review Focus 4
        val s = update(editing(stack, images), ImageSaveFailed("a", Face.FRONT, "i1"))
        assertNull(s.editor.stack.cards[0].front.imageId)
        assertEquals(emptyMap(), s.editor.images)
        assertEquals("Could not store the image.", s.error)
    }

    @Test fun savedOnlyWhenNothingChangedSince() {
        val edited = update(editing(stack), StackRenamed("New"))
        assertFalse(update(edited, StackSaved(stack)).editor.saved)
        assertTrue(update(edited, StackSaved(edited.editor.stack)).editor.saved)
    }

    @Test fun saveFailure() {
        val s = update(editing(stack), StackSaveFailed("Could not save: full"))
        assertFalse(s.editor.saved)
        assertEquals("Could not save: full", s.error)
    }

    @Test fun editorActionsIgnoredElsewhere() {
        val list = AppState(Route.Home, Screen.StackList(emptyList()))
        assertEquals(list, update(list, CardAdded("x")))
    }
}

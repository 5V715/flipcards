package dev.silas.flipcards.storage

import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class IndexedDbStorageTest {
    private val stackA = Stack(
        "a", "Alpha", listOf("en"),
        listOf(Card("c1", Side(imageId = "i1"), Side(SideText.Same("x"))), Card("c2")),
    )
    private val stackB = Stack("b", "beta", listOf("en"), emptyList())

    /** Every test gets a database of its own. */
    private suspend fun open() = IndexedDbStorage.open("test-" + Random.nextLong())

    @Test fun savesAndLoadsStacks() = runTest {
        val db = open()
        db.saveStack(stackB)
        db.saveStack(stackA)
        assertEquals(stackA, db.loadStack("a"))
        assertNull(db.loadStack("nope"))
        assertEquals(listOf("Alpha", "beta"), db.loadStackSummaries().map { it.name })
        assertEquals(stackA.cards.size, db.loadStackSummaries()[0].cardCount)
    }

    @Test fun saveOverwrites() = runTest {
        val db = open()
        db.saveStack(stackA)
        db.saveStack(stackA.copy(name = "Renamed"))
        assertEquals("Renamed", db.loadStack("a")!!.name)
        assertEquals(1, db.loadStackSummaries().size)
    }

    @Test fun imagesAreScopedToTheirStack() = runTest {
        val db = open()
        db.saveImage("a", "i1", "data:image/jpeg;base64,AA")
        db.saveImage("b", "i2", "data:image/jpeg;base64,BB")
        assertEquals(mapOf("i1" to "data:image/jpeg;base64,AA"), db.loadImages("a"))
        db.deleteImage("i1")
        assertEquals(emptyMap(), db.loadImages("a"))
    }

    @Test fun deletingAStackDeletesItsImages() = runTest {
        val db = open()
        db.saveStack(stackA)
        db.saveImage("a", "i1", "data:image/jpeg;base64,AA")
        db.saveImage("b", "i2", "data:image/jpeg;base64,BB")
        db.deleteStack("a")
        assertNull(db.loadStack("a"))
        assertEquals(emptyMap(), db.loadImages("a"))
        assertEquals(1, db.loadImages("b").size)
    }

    @Test fun importWritesStackAndImages() = runTest {
        val db = open()
        db.importStack(stackA, mapOf("i1" to "data:image/jpeg;base64,AA"))
        assertEquals(stackA, db.loadStack("a"))
        assertEquals(setOf("i1"), db.loadImages("a").keys)
    }

    @Test fun failedImportStoresNothing() = runTest {
        val db = open()
        db.saveImage("other", "i1", "data:image/jpeg;base64,ZZ")
        assertFails {
            db.importStack(stackA, mapOf("i0" to "data:image/jpeg;base64,AA", "i1" to "data:image/jpeg;base64,BB"))
        }
        assertNull(db.loadStack("a"))
        assertEquals(emptyMap(), db.loadImages("a"))
        assertEquals(mapOf("i1" to "data:image/jpeg;base64,ZZ"), db.loadImages("other"))
    }
}

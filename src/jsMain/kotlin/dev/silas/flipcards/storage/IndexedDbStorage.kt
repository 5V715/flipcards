package dev.silas.flipcards.storage

import dev.silas.flipcards.model.FlipJson
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.model.summary
import kotlinx.browser.window

private const val STACKS = "stacks"
private const val IMAGES = "images"
private const val BY_STACK = "stackId"

/**
 * Stacks and images in the browser's IndexedDB.
 *
 * Store `stacks` holds `{ id, json }`, store `images` holds `{ id, stackId, dataUrl }`.
 */
class IndexedDbStorage private constructor(private val db: IDBDatabase) : Storage {

    /**
     * Runs [block] in one transaction and waits until it is committed.
     *
     * A transaction closes itself as soon as the browser is idle with no request pending,
     * and resuming a coroutine takes longer than that. So [block] must start all its
     * requests without suspending; their results are read after the transaction is done.
     */
    private suspend fun <T> transaction(mode: String, vararg stores: String, block: (IDBTransaction) -> T): T {
        val transaction = db.transaction(arrayOf(*stores), mode)
        val result = block(transaction)
        transaction.awaitComplete()
        return result
    }

    private suspend fun <T> read(store: String, block: (IDBObjectStore) -> IDBRequest<T>): T =
        transaction("readonly", store) { block(it.objectStore(store)) }.result

    override suspend fun loadStackSummaries(): List<StackSummary> =
        read(STACKS) { it.getAll() }
            .map { decodeStack(it).summary() }
            .sortedBy { it.name.lowercase() }

    override suspend fun loadStack(id: String): Stack? {
        // get() yields undefined when there is no such record.
        val record: Any? = read(STACKS) { it.get(id) }
        return record?.let { decodeStack(it) }
    }

    override suspend fun saveStack(stack: Stack) {
        transaction("readwrite", STACKS) { it.objectStore(STACKS).put(stackRecord(stack)) }
    }

    override suspend fun deleteStack(id: String) {
        transaction("readwrite", STACKS, IMAGES) { transaction ->
            transaction.objectStore(STACKS).delete(id)
            val images = transaction.objectStore(IMAGES)
            val keys = images.index(BY_STACK).getAllKeys(id)
            // The follow-up deletes are started inside the callback, while the transaction is still open.
            keys.onsuccess = { keys.result.forEach { images.delete(it) } }
        }
    }

    override suspend fun loadImages(stackId: String): Map<String, String> =
        read(IMAGES) { it.index(BY_STACK).getAll(stackId) }
            .associate { (it.id as String) to (it.dataUrl as String) }

    override suspend fun saveImage(stackId: String, imageId: String, dataUrl: String) {
        transaction("readwrite", IMAGES) { it.objectStore(IMAGES).put(imageRecord(stackId, imageId, dataUrl)) }
    }

    override suspend fun deleteImage(imageId: String) {
        transaction("readwrite", IMAGES) { it.objectStore(IMAGES).delete(imageId) }
    }

    override suspend fun importStack(stack: Stack, images: Map<String, String>) {
        transaction("readwrite", STACKS, IMAGES) { transaction ->
            // add() fails on an existing id, which rolls back the whole transaction.
            transaction.objectStore(STACKS).add(stackRecord(stack))
            val store = transaction.objectStore(IMAGES)
            images.forEach { (imageId, dataUrl) -> store.add(imageRecord(stack.id, imageId, dataUrl)) }
        }
    }

    private fun decodeStack(record: dynamic): Stack = FlipJson.decodeFromString(record.json as String)

    private fun stackRecord(stack: Stack): dynamic {
        val record = js("({})")
        record.id = stack.id
        record.json = FlipJson.encodeToString(stack)
        return record
    }

    private fun imageRecord(stackId: String, imageId: String, dataUrl: String): dynamic {
        val record = js("({})")
        record.id = imageId
        record.stackId = stackId
        record.dataUrl = dataUrl
        return record
    }

    companion object {
        /** Opens the database, creating its stores on first use. Throws if IndexedDB is unavailable. */
        suspend fun open(name: String = "flipcards"): IndexedDbStorage {
            val factory = window.asDynamic().indexedDB.unsafeCast<IDBFactory?>()
                ?: throw IllegalStateException("IndexedDB is not available")
            val request = factory.open(name, 1)
            request.onupgradeneeded = {
                val db = request.result
                val keyById = js("({ keyPath: 'id' })")
                db.createObjectStore(STACKS, keyById)
                db.createObjectStore(IMAGES, keyById).createIndex(BY_STACK, BY_STACK)
            }
            return IndexedDbStorage(request.await())
        }
    }
}

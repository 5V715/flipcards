package dev.silas.flipcards.storage

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Kotlin's standard library has no IndexedDB declarations, so the few parts of the
// browser API that the app uses are declared here by hand. An `external` declaration
// only tells the compiler what exists in JavaScript; it generates no code.

external interface IDBFactory {
    fun open(name: String, version: Int): IDBOpenDBRequest
}

external interface IDBRequest<T> {
    val result: T
    val error: Throwable?
    var onsuccess: ((dynamic) -> Unit)?
    var onerror: ((dynamic) -> Unit)?
}

external interface IDBOpenDBRequest : IDBRequest<IDBDatabase> {
    var onupgradeneeded: ((dynamic) -> Unit)?
}

external interface IDBDatabase {
    fun createObjectStore(name: String, options: dynamic): IDBObjectStore
    fun transaction(storeNames: Array<String>, mode: String): IDBTransaction
}

external interface IDBTransaction {
    val error: Throwable?
    var oncomplete: ((dynamic) -> Unit)?
    var onerror: ((dynamic) -> Unit)?
    var onabort: ((dynamic) -> Unit)?
    fun objectStore(name: String): IDBObjectStore
}

external interface IDBObjectStore {
    /** Inserts or replaces. */
    fun put(value: dynamic): IDBRequest<dynamic>

    /** Inserts; fails if the key already exists. */
    fun add(value: dynamic): IDBRequest<dynamic>
    fun get(key: String): IDBRequest<dynamic>
    fun getAll(): IDBRequest<Array<dynamic>>
    fun delete(key: String): IDBRequest<dynamic>
    fun createIndex(name: String, keyPath: String): IDBIndex
    fun index(name: String): IDBIndex
}

external interface IDBIndex {
    fun getAll(key: String): IDBRequest<Array<dynamic>>
    fun getAllKeys(key: String): IDBRequest<Array<String>>
}

/** Suspends until the request has succeeded and returns its result. */
suspend fun <T> IDBRequest<T>.await(): T = suspendCancellableCoroutine { continuation ->
    onsuccess = { continuation.resume(result) }
    onerror = { continuation.resumeWithException(Exception(error?.message ?: "IndexedDB request failed")) }
}

/** Suspends until everything in the transaction has been written, or throws if it was rolled back. */
suspend fun IDBTransaction.awaitComplete(): Unit = suspendCancellableCoroutine { continuation ->
    oncomplete = { continuation.resume(Unit) }
    // A failed request fires onerror and then onabort; only the first may resume the coroutine.
    val fail: (dynamic) -> Unit = {
        if (continuation.isActive) {
            continuation.resumeWithException(Exception(error?.message ?: "IndexedDB transaction failed"))
        }
    }
    onerror = fail
    onabort = fail
}

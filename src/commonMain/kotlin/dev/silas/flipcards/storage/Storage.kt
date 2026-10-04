package dev.silas.flipcards.storage

import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.StackSummary

/** Where stacks and their images are kept. The app uses IndexedDB; tests use an in-memory version. */
interface Storage {
    /** Sorted by name, ignoring case. */
    suspend fun loadStackSummaries(): List<StackSummary>

    suspend fun loadStack(id: String): Stack?

    suspend fun saveStack(stack: Stack)

    /** Also deletes the stack's images. */
    suspend fun deleteStack(id: String)

    /** Image id to data URL. */
    suspend fun loadImages(stackId: String): Map<String, String>

    suspend fun saveImage(stackId: String, imageId: String, dataUrl: String)

    suspend fun deleteImage(imageId: String)

    /** Stores a new stack with its images, all or nothing. Fails if any of the ids already exists. */
    suspend fun importStack(stack: Stack, images: Map<String, String>)
}

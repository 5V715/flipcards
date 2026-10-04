package dev.silas.flipcards.storage

import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.StackSummary
import dev.silas.flipcards.model.summary

class InMemoryStorage : Storage {
    /** When true, saving fails the way a full disk would. */
    var failSaves = false
    val stacks = mutableMapOf<String, Stack>()

    /** Image id to (stack id, data URL). */
    val images = mutableMapOf<String, Pair<String, String>>()

    private fun checkCanSave() {
        if (failSaves) throw IllegalStateException("full")
    }

    override suspend fun loadStackSummaries(): List<StackSummary> =
        stacks.values.map { it.summary() }.sortedBy { it.name.lowercase() }

    override suspend fun loadStack(id: String): Stack? = stacks[id]

    override suspend fun saveStack(stack: Stack) {
        checkCanSave()
        stacks[stack.id] = stack
    }

    override suspend fun deleteStack(id: String) {
        stacks.remove(id)
        images.entries.removeAll { it.value.first == id }
    }

    override suspend fun loadImages(stackId: String): Map<String, String> =
        images.filterValues { it.first == stackId }.mapValues { it.value.second }

    override suspend fun saveImage(stackId: String, imageId: String, dataUrl: String) {
        checkCanSave()
        images[imageId] = stackId to dataUrl
    }

    override suspend fun deleteImage(imageId: String) {
        images.remove(imageId)
    }

    override suspend fun importStack(stack: Stack, images: Map<String, String>) {
        checkCanSave()
        check(stack.id !in stacks && images.keys.none { it in this.images }) { "id already exists" }
        stacks[stack.id] = stack
        images.forEach { (id, dataUrl) -> this.images[id] = stack.id to dataUrl }
    }
}

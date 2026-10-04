package dev.silas.flipcards.state

import dev.silas.flipcards.i18n.stringsFor
import dev.silas.flipcards.model.Card
import dev.silas.flipcards.model.Face
import dev.silas.flipcards.model.Side
import dev.silas.flipcards.model.SideText
import dev.silas.flipcards.model.Stack
import dev.silas.flipcards.model.side

internal fun updateEditor(state: AppState, action: EditorAction): AppState {
    val editor = state.screen as? Screen.Editor ?: return state
    val stack = editor.stack

    /** A changed stack means there is something to save. */
    fun edited(stack: Stack, images: Map<String, String> = editor.images): AppState =
        if (stack == editor.stack && images == editor.images) {
            state
        } else {
            state.copy(screen = editor.copy(stack = stack, images = images, saved = false))
        }

    return when (action) {
        is StackRenamed -> edited(stack.copy(name = action.name))

        is LanguageAdded -> {
            val code = action.code.trim()
            if (code.isEmpty() || code in stack.languages) state
            else edited(stack.copy(languages = stack.languages + code))
        }

        is LanguageRemoved ->
            if (stack.languages.size <= 1 || action.code !in stack.languages) {
                state
            } else {
                edited(
                    stack.copy(
                        languages = stack.languages - action.code,
                        cards = stack.cards.map { card ->
                            card.copy(
                                front = card.front.withoutLanguage(action.code),
                                back = card.back.withoutLanguage(action.code),
                            )
                        },
                    ),
                )
            }

        is CardAdded -> edited(stack.copy(cards = stack.cards + Card(action.cardId)))

        is CardDeleted -> {
            val card = stack.cards.find { it.id == action.cardId } ?: return state
            edited(
                stack.copy(cards = stack.cards - card),
                editor.images - setOfNotNull(card.front.imageId, card.back.imageId),
            )
        }

        is CardMoved -> {
            val from = stack.cards.indexOfFirst { it.id == action.cardId }
            val to = from + action.delta
            if (from < 0 || to !in stack.cards.indices) return state
            val cards = stack.cards.toMutableList()
            cards[from] = stack.cards[to]
            cards[to] = stack.cards[from]
            edited(stack.copy(cards = cards))
        }

        is SideTextModeChanged -> {
            val first = stack.languages.first()
            edited(
                stack.withSide(action.cardId, action.face) { side ->
                    val text = side.text
                    side.copy(
                        text = when {
                            action.translated && text is SideText.Translated -> text
                            action.translated ->
                                SideText.Translated((text as? SideText.Same)?.let { mapOf(first to it.value) } ?: emptyMap())
                            text is SideText.Same -> text
                            else -> SideText.Same((text as? SideText.Translated)?.values?.get(first) ?: "")
                        },
                    )
                },
            )
        }

        is SideTextChanged -> edited(
            stack.withSide(action.cardId, action.face) { side ->
                val language = action.language
                side.copy(
                    text = if (language == null) {
                        SideText.Same(action.value)
                    } else {
                        val values = (side.text as? SideText.Translated)?.values ?: emptyMap()
                        SideText.Translated(values + (language to action.value))
                    },
                )
            },
        )

        is ImageChosen -> chooseImage(state, editor, action)

        is ImageRemoved -> {
            val imageId = stack.sideOrNull(action.cardId, action.face)?.imageId ?: return state
            edited(
                stack.withSide(action.cardId, action.face) { it.copy(imageId = null) },
                editor.images - imageId,
            )
        }

        is ImageRejected -> state.copy(screen = editor.copy(imageError = SideRef(action.cardId, action.face)))

        is ImageSaveFailed -> {
            val failed = state.copy(error = stringsFor(state.uiLanguage).couldNotStoreImage)
            if (stack.sideOrNull(action.cardId, action.face)?.imageId != action.imageId) {
                failed
            } else {
                failed.copy(
                    screen = editor.copy(
                        stack = stack.withSide(action.cardId, action.face) { it.copy(imageId = null) },
                        images = editor.images - action.imageId,
                        saved = false,
                    ),
                )
            }
        }

        // A save that finished after further edits does not make the editor "saved".
        is StackSaved -> state.copy(screen = editor.copy(saved = editor.stack == action.stack))

        is StackSaveFailed -> state.copy(screen = editor.copy(saved = false), error = action.message)
    }
}

/** The new image replaces whatever image the side had before. */
private fun chooseImage(state: AppState, editor: Screen.Editor, action: ImageChosen): AppState {
    val side = editor.stack.sideOrNull(action.cardId, action.face) ?: return state
    val images = editor.images - setOfNotNull(side.imageId) + (action.imageId to action.dataUrl)
    return state.copy(
        screen = editor.copy(
            stack = editor.stack.withSide(action.cardId, action.face) { it.copy(imageId = action.imageId) },
            images = images,
            saved = false,
            imageError = null,
        ),
    )
}

private fun Side.withoutLanguage(code: String): Side = when (val text = text) {
    is SideText.Translated -> copy(text = SideText.Translated(text.values - code))
    else -> this
}

private fun Stack.sideOrNull(cardId: String, face: Face): Side? = cards.find { it.id == cardId }?.side(face)

private fun Stack.withSide(cardId: String, face: Face, change: (Side) -> Side): Stack =
    copy(
        cards = cards.map { card ->
            when {
                card.id != cardId -> card
                face == Face.FRONT -> card.copy(front = change(card.front))
                else -> card.copy(back = change(card.back))
            }
        },
    )

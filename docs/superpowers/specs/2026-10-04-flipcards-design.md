# Flipcards: design

Date: 2026-10-04

## Purpose

Flipcards is a learning project. Its goal is to learn Kotlin/JS and kotlinx.html by
building a small but complete flashcard app. Success means:

- the app runs on GitHub Pages as a static single-page application,
- the whole UI is built with kotlinx.html, with no JS UI framework,
- the author understands every part of it, so simplicity is preferred over features.

## Requirements

- A **stack** is a named collection of cards, for example "Spanish easy words".
- Stacks can be created, edited, deleted, exported to a file and imported from a file.
- A **card** has a front and a back. Each side has an optional image and an optional
  text. Example: front is a flag image, back is the capital's name.
- A stack has one or more languages (`en`, `de`, ...). A side's text is either the same
  in every language or translated per language.
- The player picks one play language and can switch it at any time, including mid-session.
- Playing is typed-answer with self-grading: the app suggests right or wrong, the player
  has the last word.
- Three hint modes: hinted (`_ i _ _ _ a`), length only (`_ _ _ _ _ _`), no hint.
- All data is stored in the browser. There is no backend.
- Images are uploaded from the device and travel inside the export file.

Out of scope: progress stored between sessions, spaced repetition, accounts or sync,
image URLs, a localized app interface (the interface is English), importing several files
at once.

## Architecture

Unidirectional state with full screen re-render.

1. An event handler calls `dispatch(action)`.
2. A pure function `update(state, action): AppState` returns the new state. It has no
   browser or storage access.
3. The store clears the root element and rebuilds the current screen with kotlinx.html
   from the new state.
4. Asynchronous work (IndexedDB, reading files, downscaling images) runs in a coroutine
   beside the store and dispatches a follow-up action when it finishes, for example
   `StackLoaded` or `ImageStored`.

Actions that come from typing in a text field are marked so the store updates state but
skips the re-render. This keeps focus and cursor position while typing.

Routing uses the URL hash, so GitHub Pages needs no server rewrites. A `hashchange`
listener turns the URL into a `Navigate` action, so the back button and bookmarks work.

### Technology

| Area | Choice |
|---|---|
| Build | Kotlin Multiplatform Gradle plugin, single JS (IR) browser target |
| UI | kotlinx.html (`kotlinx.html.dom`) |
| Serialization | kotlinx.serialization (JSON) |
| Async | kotlinx.coroutines |
| Storage | IndexedDB through hand-written `external` declarations wrapped in `suspend` functions |
| Styling | one handwritten `styles.css`, no CSS framework |
| Deploy | GitHub Actions to GitHub Pages |

Exact dependency versions are pinned in the implementation plan.

## Data model

```kotlin
@Serializable
data class Stack(
    val id: String,              // random UUID
    val name: String,
    val languages: List<String>, // at least one; the first is the fallback language
    val cards: List<Card>,
)

@Serializable
data class Card(val id: String, val front: Side, val back: Side)

@Serializable
data class Side(val text: SideText? = null, val imageId: String? = null)

@Serializable
sealed interface SideText {
    data class Same(val value: String) : SideText
    data class Translated(val values: Map<String, String>) : SideText
}
```

Rules:

- Language codes are free text typed by the user. There is no fixed list.
- **Resolving a text** for a play language: `Same` gives its value. `Translated` gives the
  entry for the play language, or the entry for the stack's first language if that is
  missing or blank.
- A side is **complete** if it has an image, or a `Same` text that is not blank, or a
  `Translated` text whose entry for the stack's first language is not blank. A card is
  complete if both sides are.
- Incomplete cards are stored (the editor saves automatically) and flagged in the editor.
  They are left out of play and out of exports.
- A stack always has at least one language; the last one cannot be removed.
- Removing a language deletes its entries from every `Translated` text, after a
  confirmation. If the first language is removed, the next one becomes the fallback.
- An image belongs to exactly one card side. Images are not shared between cards.

## Storage

One IndexedDB database with two object stores.

| Store | Key | Value |
|---|---|---|
| `stacks` | stack id | the `Stack` as JSON |
| `images` | image id | `{ id, stackId, dataUrl }` |

- Images are stored separately so listing stacks does not load images. They are loaded
  when a stack is opened in the editor or for play.
- An image is stored as a JPEG data URL string. This is the same form the export file
  uses and can be put straight into an `<img src>`.
- Deleting a card deletes its images. Deleting a stack deletes all its images.
- The play language last used per stack is kept in localStorage.

All access goes through one interface, implemented by `IndexedDbStorage` in the app and
by an in-memory version in tests:

```kotlin
interface Storage {
    suspend fun loadStackSummaries(): List<StackSummary>
    suspend fun loadStack(id: String): Stack?
    suspend fun saveStack(stack: Stack)
    suspend fun deleteStack(id: String)              // also deletes its images
    suspend fun loadImages(stackId: String): Map<String, String>  // image id -> data URL
    suspend fun saveImage(stackId: String, imageId: String, dataUrl: String)
    suspend fun deleteImage(imageId: String)
    suspend fun importStack(stack: Stack, images: Map<String, String>)  // one transaction
}
```

`StackSummary` holds id, name, languages and card count.

## Screens

| Route | Screen | Actions available |
|---|---|---|
| `#/` | Stack list | Shows name, card count and languages per stack. New stack, import. Per stack: play, edit, export, delete (with confirmation). |
| `#/stack/<id>/edit` | Editor | Rename the stack. Add and remove languages. Add, move up/down and delete cards. Per side: choose "same in all languages" or "translated", edit the text(s), upload or remove an image. |
| `#/stack/<id>/play` | Play | Setup, session, summary. |
| anything else | Not found | Link back to the list. |

- "New stack" creates a stack named "New stack" with the language `en` and no cards, and
  opens it in the editor.
- The editor saves automatically 500 ms after the last change and shows "Saved" or
  "Not saved". There is no Save button.

### State

```kotlin
data class AppState(val screen: Screen, val error: String? = null)

sealed interface Screen {
    data object Loading : Screen
    data class StackList(val stacks: List<StackSummary>) : Screen
    data class Editor(val stack: Stack, val images: Map<String, String>, val saved: Boolean) : Screen
    data class Play(val stack: Stack, val images: Map<String, String>, val phase: PlayPhase) : Screen
    data class NotFound(val message: String) : Screen
}
```

`error` is shown as a dismissible banner at the top of whatever screen is open.

## Play session

```kotlin
sealed interface PlayPhase {
    data class Setup(val language: String, val mode: HintMode) : PlayPhase
    data class Asking(val session: Session, val hint: String?, val typed: String) : PlayPhase
    data class Revealed(val session: Session, val typed: String, val suggestion: Boolean?) : PlayPhase
    data class Summary(val result: SessionResult) : PlayPhase
}

enum class HintMode { HINTED, LENGTH_ONLY, NONE }
```

**Setup.** The player chooses the play language (preselected: the one last used for this
stack, otherwise the stack's first) and the hint mode. If the stack has no complete
cards, a message and a link to the editor replace the Start button.

**Asking.** The complete cards are shuffled. The screen shows the front (image, text or
both), the hint for the back, and a text input. Enter submits the typed answer. A "Show
answer" button reveals without typing. A language selector at the top switches the play
language at any point; the current card redraws in the new language with a fresh hint.

**Revealed.** The back is shown with the typed answer next to the correct one. The app
suggests right or wrong by comparing them after normalizing both: trim, collapse runs of
whitespace, ignore case, strip accents. So "bogota" matches "Bogotá". Two buttons, "Knew
it" and "Didn't know", are offered with the suggested one focused so Enter accepts it.
If nothing was typed, or the back has no text, there is no suggestion and no button is
preselected.

**Queue and score.**

- A card marked "Didn't know" goes to the end of the queue. The session ends when every
  card has been marked "Knew it".
- The score counts first attempts only, for example "14 of 20 known first time".
- The summary lists the missed cards with their answers and offers "Play again", "Play
  missed cards only" and "Back to stacks".
- Leaving mid-session discards it. Nothing about a session is stored.

**Hints.** The hint is built from the back's text resolved in the play language.

- Letters and digits are hidden as `_`. Spaces and punctuation stay visible. Characters
  are separated by spaces for readability.
- `LENGTH_ONLY` hides every letter and digit.
- `HINTED` reveals `max(1, n / 3)` of the `n` hidden characters (integer division),
  chosen at random. If `n` is 1, nothing is revealed.
- `NONE` shows no hint. A back without text has no hint in any mode.
- The hint is generated when the card appears and kept in the state. A card that returns
  to the queue gets a new one.

**Randomness.** Shuffling and hint letters use a seed carried in the action
(`SessionStarted(seed)`), so `update` stays pure and tests are deterministic.

## Import and export

**Export.** The Export button downloads one file named after the stack, for example
`spanish-easy-words.flipcards.json`:

```json
{
  "format": "flipcards-stack",
  "version": 1,
  "stack": { "id": "...", "name": "...", "languages": ["en", "de"], "cards": [] },
  "images": { "<imageId>": "data:image/jpeg;base64,..." }
}
```

`stack` is the same serialized `Stack` that IndexedDB stores, minus incomplete cards.
`images` holds every image the exported cards use.

**Import.** The Import button opens a file picker for one file. The whole file is
validated before anything is stored:

- `format` is `flipcards-stack` and `version` is 1. A higher version is rejected with
  "this file was made by a newer version of the app".
- The stack decodes into the model, has a non-blank name and at least one language, and
  every card is complete.
- Every image id a card refers to is present in `images`, and every image value starts
  with `data:image/`.

A valid stack gets fresh ids for itself, its cards and its images, so importing never
overwrites anything. If the name is already used, the copy is named "Name (2)", then
"Name (3)", and so on. Stack and images are written in one IndexedDB transaction.

**Image upload.** Any file the browser can decode as an image is accepted. It is drawn on
a canvas, downscaled so its longest side is at most 800 px, and encoded as JPEG on a
white background. A file that cannot be decoded shows a message next to that card side
and changes nothing.

## Error handling

| Situation | Behaviour |
|---|---|
| Import file invalid | Error banner with the specific reason; nothing stored |
| Saving fails (for example storage full) | Error banner; the editor shows "Not saved" and the next change retries |
| IndexedDB cannot be opened | Full-screen message that the app needs local storage |
| Route names a stack that does not exist | Not found screen with a link to the list |
| Uploaded file is not a decodable image | Message next to the card side; nothing changes |

All text from stacks is inserted as text nodes by kotlinx.html, never as raw HTML, so an
imported stack cannot inject markup or scripts.

## Project layout

Browser-free logic lives in `commonMain`, so the compiler guarantees it does not touch
the DOM.

```
flipcards/
├── build.gradle.kts, settings.gradle.kts, gradle wrapper
├── .github/workflows/deploy.yml
└── src/
    ├── commonMain/kotlin/flipcards/
    │   ├── model/      Stack, Card, Side, SideText, text resolution, completeness
    │   ├── state/      AppState, Screen, Action, update()
    │   ├── play/       Session, hint generation, answer comparison
    │   ├── transfer/   export file format, validation, fresh ids, name deduplication
    │   └── storage/    Storage interface
    ├── commonTest/     tests for the above, plus InMemoryStorage
    ├── jsMain/kotlin/flipcards/
    │   ├── Main.kt     opens storage, creates the store, starts routing
    │   ├── Store.kt    holds state, runs update(), re-renders, launches async work
    │   ├── Router.kt   hash <-> route
    │   ├── storage/    IndexedDbStorage and the external declarations
    │   ├── browser/    file download, file reading, image downscaling
    │   └── ui/         StackListView, EditorView, PlayView, layout, error banner
    ├── jsMain/resources/   index.html, styles.css
    └── jsTest/         IndexedDbStorage tests
```

Accent stripping needs the browser's `String.normalize`. It is supplied to the common
code through an `expect`/`actual` function.

The layout works on a phone as well as a desktop. Revealing the answer plays a short CSS
flip animation on the card.

## Testing

Everything in `commonMain` is developed test-first.

| What | Where | Covers |
|---|---|---|
| Model | commonTest | text resolution with fallback, completeness rules |
| `update()` | commonTest | every action: navigation, editor changes, removing a language, error banner |
| Play logic | commonTest | queue order with a fixed seed, missed cards returning, first-attempt score, hints in all three modes, answer comparison |
| Transfer | commonTest | export then import yields an equal stack with new ids, each validation rule rejects what it should, name deduplication, incomplete cards left out |
| `IndexedDbStorage` | jsTest, headless Chrome | save, load and delete of stacks and images, cascade delete, all-or-nothing import |
| Views | manual | a short checklist in the README |

## Deployment

- A GitHub Actions workflow runs on every push to `main`: run the tests, build the
  production bundle, publish it to GitHub Pages. A failing test stops the deploy.
- `index.html` uses relative paths so the app works under
  `https://<user>.github.io/flipcards/`.
- Manual steps for the repository owner: create the GitHub repository and push to it,
  and set Pages -> Source to "GitHub Actions" once in the repository settings.

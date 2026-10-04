# Flipcards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a flashcard single-page app in Kotlin/JS with a kotlinx.html UI, browser-local storage, stack import/export and a typed-answer play mode, deployed to GitHub Pages.

**Architecture:** One `AppState`, a pure `update(state, action)` function, and a store that rebuilds the current screen with kotlinx.html after each action. Asynchronous work (IndexedDB, downloads) lives in an `Effects` class that reacts to actions and dispatches follow-up actions. Everything except DOM, IndexedDB and file handling is in `commonMain` and is developed test-first.

**Tech Stack:** Kotlin Multiplatform 2.4.20 (JS IR browser target), Gradle 9.8.0, kotlinx-html 0.12.0, kotlinx-serialization-json 1.11.0, kotlinx-coroutines 1.11.0, kotlin.test with Karma.

**Spec:** `docs/superpowers/specs/2026-10-04-flipcards-design.md`

## Global Constraints

- The UI is built only with kotlinx.html (`kotlinx.html.dom`). No JS UI framework, no CSS framework.
- Dependencies are exactly: kotlinx-html, kotlinx-serialization-json, kotlinx-coroutines (plus kotlin-test and kotlinx-coroutines-test for tests). IndexedDB is reached through hand-written `external` declarations.
- `commonMain` never references `kotlinx.browser`, `org.w3c.*` or `kotlinx.html`.
- All stack text reaches the page through kotlinx.html text nodes (`+text`), never `unsafe { }` or `innerHTML`.
- Root package is `dev.silas.flipcards`. All code under `src/<sourceSet>/kotlin/dev/silas/flipcards/`.
- The app interface is English. User-visible copy given in this plan is exact.
- Mobile first: base CSS targets a 320 px wide portrait phone; wider layouts only via `min-width` media queries; touch targets at least 44 px high; nothing depends on hover.
- Editor autosave delay: 500 ms. Image downscale: longest side at most 800 px, JPEG, white background.
- Export format marker `flipcards-stack`, version `1`.
- Commit after every task, with the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never add `.idea/`.
- Nothing is pushed and no GitHub repository is created by this plan.

## Deviations from the spec (decided while planning)

- **Test browser.** This machine has Firefox but no Chrome. Karma uses headless Firefox by default and headless Chrome when run with `-PtestBrowser=chrome`, which is what CI does.
- **`AppState.route`.** `AppState` gets a `route` field so that load results arriving after the user has navigated elsewhere can be ignored.
- **`Screen.Editor.imageError`.** One extra field carries "this file is not an image" for a card side.
- **`Effects` and `Env`.** The spec's "async work beside the store" is an `Effects` class in `commonMain`, talking to the browser through an `Env` interface, so it can be tested with `InMemoryStorage`.
- **All tests run in the browser.** With a single JS browser target, `commonTest` and `jsTest` both run under Karma via `./gradlew jsBrowserTest`.

## Review Focus

1. **A load result arrives after the user navigated away** (slow IndexedDB, quick taps). The stale result must be ignored, not replace the current screen. Pinned in Task 6.
2. **The stored play language is no longer one of the stack's languages** (it was removed in the editor). Play setup must fall back to the stack's first language. Pinned in Task 8.
3. **Leaving the editor within 500 ms of the last edit.** The pending change must be saved, not dropped. Pinned in Task 10.
4. **Storing an image fails** (storage full). The card must not keep pointing at an image that was never stored. Pinned in Tasks 7 and 10.
5. **Sloppy language codes**: blank, duplicate, or differing only by surrounding spaces, typed in the editor or present in an imported file. They must not create a second "en". Pinned in Tasks 5 and 7.

## File map

```
build.gradle.kts, settings.gradle.kts, gradle.properties, .gitignore, README.md
.github/workflows/deploy.yml
src/commonMain/kotlin/dev/silas/flipcards/
  model/Model.kt            Stack, Card, Side, SideText, Face, StackSummary, resolve/complete helpers, FlipJson
  play/Answer.kt            normalizeAnswer, suggest, expect stripAccents
  play/Hint.kt              HintMode, hint()
  play/Session.kt           Session, SessionResult, startSession, grade, hintFor
  transfer/Transfer.kt      ExportFile, buildExport, encodeExport, parseImport, withFreshIds, uniqueName, exportFileName
  state/Route.kt            Route, parseRoute, toHash
  state/AppState.kt         AppState, Screen, PlayPhase, SideRef
  state/Action.kt           Action and all its subtypes
  state/Update.kt           update() and navigation/list/error cases
  state/EditorUpdate.kt     editor cases
  state/PlayUpdate.kt       play cases
  storage/Storage.kt        Storage interface
  effects/Env.kt            Env interface
  effects/Effects.kt        Effects
src/commonTest/kotlin/dev/silas/flipcards/   one test file per file above, plus storage/InMemoryStorage.kt, effects/FakeEnv.kt
src/jsMain/kotlin/dev/silas/flipcards/
  Main.kt, Store.kt
  play/StripAccents.kt      actual stripAccents
  storage/IndexedDb.kt      external declarations + await helpers
  storage/IndexedDbStorage.kt
  browser/Browser.kt        newId, downloadText, readFileText, downscaleToJpegDataUrl, BrowserEnv
  ui/Layout.kt              page frame, error banner, not found, fatal
  ui/StackListView.kt, ui/EditorView.kt, ui/PlayView.kt
src/jsMain/resources/index.html, styles.css
src/jsTest/kotlin/dev/silas/flipcards/storage/IndexedDbStorageTest.kt
```

Commands used throughout:

- All tests: `./gradlew jsBrowserTest`
- One class: `./gradlew jsBrowserTest --tests "dev.silas.flipcards.model.ModelTest"`
- Dev server with reload: `./gradlew jsBrowserDevelopmentRun --continuous`
- Production bundle: `./gradlew jsBrowserDistribution` (output in `build/dist/js/productionExecutable`)

---

### Task 1: Project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, Gradle wrapper files
- Create: `src/jsMain/resources/index.html`, `src/jsMain/resources/styles.css`
- Create: `src/jsMain/kotlin/dev/silas/flipcards/Main.kt`
- Test: `src/commonTest/kotlin/dev/silas/flipcards/SmokeTest.kt`

**Interfaces:**
- Produces: a building project; `index.html` with `<div id="root">`, loading `styles.css` and `flipcards.js` by relative path.

- [ ] **Step 1: Generate the Gradle wrapper.** No Gradle is on the PATH; a 9.7.1 distribution is cached. From the project root:

```bash
touch settings.gradle.kts
~/.gradle/wrapper/dists/gradle-9.7.1-bin/*/gradle-9.7.1/bin/gradle wrapper --gradle-version 9.8.0
./gradlew --version
```

Expected: prints `Gradle 9.8.0`.

- [ ] **Step 2: Write the build files.**

`settings.gradle.kts`:

```kotlin
rootProject.name = "flipcards"
```

`gradle.properties`:

```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2g
```

`build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

group = "flipcards"
version = "1.0"

repositories { mavenCentral() }

kotlin {
    js {
        browser {
            commonWebpackConfig { outputFileName = "flipcards.js" }
            testTask {
                useKarma {
                    if (providers.gradleProperty("testBrowser").orNull == "chrome") useChromeHeadless()
                    else useFirefoxHeadless()
                }
            }
        }
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        jsMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-html:0.12.0")
        }
    }
}
```

`.gitignore`: `.gradle/`, `.kotlin/`, `build/`, `.idea/`. The generated `kotlin-js-store/` directory (yarn lock) is committed.

- [ ] **Step 3: Write `index.html`.** `lang="en"`, `<meta charset="utf-8">`, `<meta name="viewport" content="width=device-width, initial-scale=1">`, `<title>Flipcards</title>`, `<link rel="stylesheet" href="styles.css">`, body with `<div id="root"></div>` and `<script src="flipcards.js"></script>`. All paths relative. `styles.css` starts with only `body { margin: 0; font-family: system-ui, sans-serif; }`.

- [ ] **Step 4: Write the smoke test** in `SmokeTest.kt`:

```kotlin
class SmokeTest {
    @Test fun testsRun() = assertEquals(4, 2 + 2)
}
```

- [ ] **Step 5: Write `Main.kt`.** `fun main()` finds `#root` and appends an `h1` with the text `Flipcards` using `root.append { h1 { +"Flipcards" } }`.

- [ ] **Step 6: Verify tests run in the browser.**

Run: `./gradlew jsBrowserTest`
Expected: `BUILD SUCCESSFUL`, one test passed.

If Firefox fails to start with a profile error (it is a snap and may not see `/tmp`), run with `TMPDIR=$HOME/.cache/flipcards-tmp` (create the directory) and record that in the README's "Running tests" section. If Gradle 9.8.0 or a library version is rejected by the Kotlin plugin, stop and report; do not silently change versions.

- [ ] **Step 7: Verify the app builds and shows the heading.**

Run: `./gradlew jsBrowserDistribution`
Expected: `build/dist/js/productionExecutable/` contains `index.html`, `styles.css`, `flipcards.js`.

- [ ] **Step 8: Commit** (`git add` the build files, wrapper, `src/`, `kotlin-js-store/`, `.gitignore`): `chore: scaffold Kotlin/JS project`.

---

### Task 2: Model

**Files:**
- Create: `src/commonMain/kotlin/dev/silas/flipcards/model/Model.kt`
- Test: `src/commonTest/kotlin/dev/silas/flipcards/model/ModelTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Serializable data class Stack(val id: String, val name: String, val languages: List<String>, val cards: List<Card>)
@Serializable data class Card(val id: String, val front: Side = Side(), val back: Side = Side())
@Serializable data class Side(val text: SideText? = null, val imageId: String? = null)
@Serializable sealed interface SideText {
    @Serializable @SerialName("same") data class Same(val value: String) : SideText
    @Serializable @SerialName("translated") data class Translated(val values: Map<String, String>) : SideText
}
enum class Face { FRONT, BACK }
data class StackSummary(val id: String, val name: String, val languages: List<String>, val cardCount: Int)

val FlipJson: Json                                  // Json { ignoreUnknownKeys = true }
fun Card.side(face: Face): Side
fun Side.resolveText(language: String, fallback: String): String?   // null when there is no non-blank text
fun Side.isComplete(firstLanguage: String): Boolean
fun Card.isComplete(firstLanguage: String): Boolean
val Stack.completeCards: List<Card>
fun Stack.summary(): StackSummary                   // cardCount counts all cards, complete or not
```

- [ ] **Step 1: Write the failing tests.**

```kotlin
private val vienna = Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien")))

@Test fun resolvesPlayLanguage() = assertEquals("Wien", vienna.resolveText("de", "en"))
@Test fun fallsBackWhenMissing() = assertEquals("Vienna", vienna.resolveText("es", "en"))
@Test fun fallsBackWhenBlank() =
    assertEquals("Vienna", Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "  "))).resolveText("de", "en"))
@Test fun sameIgnoresLanguage() = assertEquals("casa", Side(SideText.Same("casa")).resolveText("de", "en"))
@Test fun noTextResolvesToNull() {
    assertNull(Side().resolveText("en", "en"))
    assertNull(Side(SideText.Same(" ")).resolveText("en", "en"))
}

@Test fun completeness() {
    assertTrue(Side(imageId = "i1").isComplete("en"))
    assertTrue(Side(SideText.Same("casa")).isComplete("en"))
    assertTrue(vienna.isComplete("en"))
    assertFalse(Side().isComplete("en"))
    assertFalse(Side(SideText.Same("")).isComplete("en"))
    assertFalse(Side(SideText.Translated(mapOf("de" to "Wien"))).isComplete("en"))   // first language missing
}

@Test fun completeCardsSkipsIncomplete() {
    val stack = Stack("s", "S", listOf("en"), listOf(Card("a", vienna, vienna), Card("b", vienna, Side())))
    assertEquals(listOf("a"), stack.completeCards.map { it.id })
    assertEquals(2, stack.summary().cardCount)
}

@Test fun jsonRoundTrip() {
    val stack = Stack("s", "S", listOf("en", "de"), listOf(Card("a", Side(imageId = "i1"), vienna)))
    assertEquals(stack, FlipJson.decodeFromString<Stack>(FlipJson.encodeToString(stack)))
}
```

- [ ] **Step 2: Run** `./gradlew jsBrowserTest --tests "dev.silas.flipcards.model.ModelTest"`. Expected: compilation fails, unresolved references.
- [ ] **Step 3: Implement `Model.kt`** with the declarations above.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat: add stack model`.

---

### Task 3: Answer comparison and hints

**Files:**
- Create: `src/commonMain/kotlin/dev/silas/flipcards/play/Answer.kt`, `src/commonMain/kotlin/dev/silas/flipcards/play/Hint.kt`
- Create: `src/jsMain/kotlin/dev/silas/flipcards/play/StripAccents.kt`
- Test: `src/commonTest/kotlin/dev/silas/flipcards/play/AnswerTest.kt`, `src/commonTest/kotlin/dev/silas/flipcards/play/HintTest.kt`

**Interfaces:**
- Produces:

```kotlin
expect fun stripAccents(text: String): String       // js actual: normalize("NFD") then remove \p{M} characters
fun normalizeAnswer(text: String): String           // trim, collapse whitespace runs to one space, lowercase, stripAccents
fun suggest(typed: String, correct: String?): Boolean?   // null when typed is blank or correct is null

enum class HintMode { HINTED, LENGTH_ONLY, NONE }
fun hint(answer: String?, mode: HintMode, random: Random): String?
```

- [ ] **Step 1: Write the failing tests.**

```kotlin
// AnswerTest
@Test fun matchesIgnoringCaseAccentsAndSpaces() {
    assertEquals(true, suggest("  bogota ", "Bogotá"))
    assertEquals(true, suggest("new   delhi", "New Delhi"))
    assertEquals(true, suggest("SAO TOME", "São Tomé"))
    assertEquals(false, suggest("Wien", "Vienna"))
}
@Test fun noSuggestionWithoutInputOrAnswer() {
    assertNull(suggest("", "Vienna"))
    assertNull(suggest("   ", "Vienna"))
    assertNull(suggest("Vienna", null))
}

// HintTest
@Test fun lengthOnly() = assertEquals("_ _ _ _ _ _", hint("Vienna", HintMode.LENGTH_ONLY, Random(1)))
@Test fun keepsSpacesAndPunctuation() {
    assertEquals("_ _ _   _ _ _ _ _", hint("New Delhi", HintMode.LENGTH_ONLY, Random(1)))
    assertEquals("_ _ . _ _ _ _", hint("St.John", HintMode.LENGTH_ONLY, Random(1)))
}
@Test fun hidesNonLatinLetters() = assertEquals("_ _ _ _", hint("Київ", HintMode.LENGTH_ONLY, Random(1)))
@Test fun hintedRevealsAThird() {
    val h = hint("Vienna", HintMode.HINTED, Random(1))!!
    val shown = h.split(" ")
    assertEquals(6, shown.size)
    assertEquals(2, shown.count { it != "_" })                      // max(1, 6 / 3)
    shown.forEachIndexed { i, c -> if (c != "_") assertEquals("Vienna"[i].toString(), c) }
}
@Test fun hintedRevealsAtLeastOne() = assertEquals(1, hint("Rom", HintMode.HINTED, Random(1))!!.count { it.isLetter() })
@Test fun hintedSingleCharacterRevealsNothing() = assertEquals("_", hint("A", HintMode.HINTED, Random(1)))
@Test fun sameSeedSameHint() =
    assertEquals(hint("Vienna", HintMode.HINTED, Random(7)), hint("Vienna", HintMode.HINTED, Random(7)))
@Test fun noHint() {
    assertNull(hint("Vienna", HintMode.NONE, Random(1)))
    assertNull(hint(null, HintMode.HINTED, Random(1)))
    assertNull(hint("  ", HintMode.LENGTH_ONLY, Random(1)))
}
```

- [ ] **Step 2: Run** `./gradlew jsBrowserTest --tests "dev.silas.flipcards.play.*"`. Expected: compilation fails.
- [ ] **Step 3: Implement.** In `hint`, a character is hidden when `isLetterOrDigit()` is true. Reveal count is `if (n == 1) 0 else max(1, n / 3)`, positions picked with `random`. Output characters are joined with a single space.
- [ ] **Step 4: Run again.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat: add answer comparison and hints`.

---

### Task 4: Session

**Files:**
- Create: `src/commonMain/kotlin/dev/silas/flipcards/play/Session.kt`
- Test: `src/commonTest/kotlin/dev/silas/flipcards/play/SessionTest.kt`

**Interfaces:**
- Consumes: `Stack`, `Card`, `Face`, `resolveText` (Task 2); `HintMode`, `hint` (Task 3).
- Produces:

```kotlin
data class Session(
    val language: String,
    val mode: HintMode,
    val queue: List<String>,     // card ids; the first is the current card
    val total: Int,
    val missed: List<String>,    // ids marked "Didn't know" at least once, in first-miss order
    val seed: Long,
    val step: Int = 0,           // increases by one with every grade
)
data class SessionResult(val language: String, val mode: HintMode, val total: Int, val missed: List<String>) {
    val knownFirstTime: Int      // total - missed.size
}
fun startSession(cardIds: List<String>, language: String, mode: HintMode, seed: Long): Session  // shuffles with Random(seed)
fun Session.grade(knew: Boolean): Session
val Session.isFinished: Boolean
fun Session.result(): SessionResult
fun Session.currentCard(stack: Stack): Card
fun hintFor(stack: Stack, session: Session): String?   // hint for the current card's back text, Random(seed + step)
```

- [ ] **Step 1: Write the failing tests.**

```kotlin
private val ids = listOf("a", "b", "c", "d")
private fun start(seed: Long = 1) = startSession(ids, "en", HintMode.HINTED, seed)

@Test fun shufflesDeterministically() {
    assertEquals(start(1).queue, start(1).queue)
    assertEquals(ids.toSet(), start(1).queue.toSet())
    assertEquals(4, start(1).total)
}
@Test fun knownCardLeavesQueue() {
    val s = start(); val next = s.grade(true)
    assertEquals(s.queue.drop(1), next.queue)
    assertEquals(emptyList(), next.missed)
    assertEquals(1, next.step)
}
@Test fun missedCardGoesToEnd() {
    val s = start(); val next = s.grade(false)
    assertEquals(s.queue.drop(1) + s.queue.first(), next.queue)
    assertEquals(listOf(s.queue.first()), next.missed)
}
@Test fun missedTwiceCountsOnce() {
    var s = startSession(listOf("a"), "en", HintMode.NONE, 1)
    s = s.grade(false).grade(false)
    assertEquals(listOf("a"), s.missed)
    assertFalse(s.isFinished)
    s = s.grade(true)
    assertTrue(s.isFinished)
    assertEquals(0, s.result().knownFirstTime)
}
@Test fun scoreCountsFirstAttempts() {
    var s = start()
    s = s.grade(true).grade(false).grade(true).grade(true).grade(true)   // 4 cards, one missed then known
    assertTrue(s.isFinished)
    assertEquals(3, s.result().knownFirstTime)
    assertEquals(4, s.result().total)
}
@Test fun hintChangesWhenACardReturns() {
    val stack = Stack("s", "S", listOf("en"),
        listOf(Card("a", Side(SideText.Same("Austria")), Side(SideText.Same("Vienna Vienna Vienna")))))
    val first = startSession(listOf("a"), "en", HintMode.HINTED, 3)
    val hints = generateSequence(first) { it.grade(false) }.take(6).map { hintFor(stack, it) }.toSet()
    assertTrue(hints.size > 1)
}
@Test fun hintUsesPlayLanguageWithFallback() {
    val back = Side(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien")))
    val stack = Stack("s", "S", listOf("en", "de"), listOf(Card("a", Side(imageId = "i"), back)))
    assertEquals("_ _ _ _", hintFor(stack, startSession(listOf("a"), "de", HintMode.LENGTH_ONLY, 1)))
    assertEquals("_ _ _ _ _ _", hintFor(stack, startSession(listOf("a"), "es", HintMode.LENGTH_ONLY, 1)))
}
```

- [ ] **Step 2: Run** `./gradlew jsBrowserTest --tests "dev.silas.flipcards.play.SessionTest"`. Expected: compilation fails.
- [ ] **Step 3: Implement `Session.kt`.**
- [ ] **Step 4: Run again.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat: add play session logic`.

---

### Task 5: Import and export format

**Files:**
- Create: `src/commonMain/kotlin/dev/silas/flipcards/transfer/Transfer.kt`
- Test: `src/commonTest/kotlin/dev/silas/flipcards/transfer/TransferTest.kt`

**Interfaces:**
- Consumes: model (Task 2).
- Produces:

```kotlin
@Serializable data class ExportFile(
    val format: String = "flipcards-stack", val version: Int = 1,
    val stack: Stack, val images: Map<String, String>,
)
class ImportException(message: String) : Exception(message)

fun buildExport(stack: Stack, images: Map<String, String>): ExportFile  // complete cards only; only images those cards use
fun encodeExport(file: ExportFile): String          // must write format and version even though they are defaults
fun parseImport(text: String): ExportFile           // throws ImportException
fun withFreshIds(file: ExportFile, newId: () -> String): ExportFile
fun uniqueName(name: String, taken: Set<String>): String
fun exportFileName(stackName: String): String
```

Validation order and exact messages in `parseImport`. Read the text as a `JsonObject` first, check 1 and 2, then decode.

| # | Condition | Message |
|---|---|---|
| 1 | not JSON, not an object, `format` is not `flipcards-stack`, `version` missing or below 1, or the rest does not decode | `This is not a Flipcards stack file.` |
| 2 | `version` above 1 | `This file was made by a newer version of the app.` |
| 3 | stack name blank | `The stack has no name.` |
| 4 | no languages | `The stack has no languages.` |
| 5 | a language code is blank, or two are equal after trimming | `The stack has invalid language codes.` |
| 6 | card N (1-based) is not complete | `Card N is incomplete.` |
| 7 | card N refers to an image id not in `images` | `Card N refers to a missing image.` |
| 8 | any image value does not start with `data:image/` | `The file contains invalid image data.` |

- [ ] **Step 1: Write the failing tests.** Fixture: `stack` named `Capitals`, languages `en`, `de`, cards `a` (front image `i1`, back Translated Vienna/Wien), `b` (front Same "x", back empty), `images = mapOf("i1" to "data:image/jpeg;base64,AAAA", "unused" to "data:image/jpeg;base64,BBBB")`.

```kotlin
@Test fun exportDropsIncompleteCardsAndUnusedImages() {
    val file = buildExport(stack, images)
    assertEquals(listOf("a"), file.stack.cards.map { it.id })
    assertEquals(setOf("i1"), file.images.keys)
}
@Test fun encodedFileCarriesFormatAndVersion() {
    val text = encodeExport(buildExport(stack, images))
    assertTrue("\"format\":\"flipcards-stack\"" in text && "\"version\":1" in text)
}
@Test fun exportThenImportIsEqual() {
    val file = buildExport(stack, images)
    assertEquals(file, parseImport(encodeExport(file)))
}
@Test fun freshIdsReplaceEveryIdAndKeepLinks() {
    var n = 0
    val fresh = withFreshIds(buildExport(stack, images)) { "new${n++}" }
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
```

Rejection tests, one per table row, each asserting `assertFailsWith<ImportException> { parseImport(text) }.message` equals the row's message. Build each input by encoding a valid file and changing one thing:

| Test | Input |
|---|---|
| `rejectsGarbage` | `"hello"`, `"[]"`, `"{}"`, and a valid file with `format` set to `other` |
| `rejectsNewerVersion` | `{"format":"flipcards-stack","version":2,"somethingNew":true}` |
| `rejectsBlankName` | name `"  "` |
| `rejectsNoLanguages` | languages `[]` |
| `rejectsBadLanguageCodes` | languages `["en", " "]`; languages `["en", "en "]` |
| `rejectsIncompleteCard` | second card with an empty back, message `Card 2 is incomplete.` |
| `rejectsMissingImage` | `images` emptied, message `Card 1 refers to a missing image.` |
| `rejectsNonImageData` | image value `javascript:alert(1)`; image value `https://example.com/a.png` |

- [ ] **Step 2: Run** `./gradlew jsBrowserTest --tests "dev.silas.flipcards.transfer.TransferTest"`. Expected: compilation fails.
- [ ] **Step 3: Implement `Transfer.kt`.** `exportFileName`: lowercase, each run of characters other than `a-z0-9` becomes `-`, trim `-` from both ends, use `stack` if empty, append `.flipcards.json`. `encodeExport` uses a `Json` with `encodeDefaults = true`.
- [ ] **Step 4: Run again.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat: add stack export and import format`.

---

### Task 6: Routes, state, actions and core update

**Files:**
- Create: `src/commonMain/kotlin/dev/silas/flipcards/state/Route.kt`, `AppState.kt`, `Action.kt`, `Update.kt`
- Test: `src/commonTest/kotlin/dev/silas/flipcards/state/RouteTest.kt`, `UpdateTest.kt`

**Interfaces:**
- Consumes: model, `HintMode`, `Session`, `SessionResult`.
- Produces (this task declares every type; Tasks 7 and 8 fill in the editor and play cases of `update`):

```kotlin
sealed interface Route {
    data object Home : Route
    data class Edit(val stackId: String) : Route
    data class Play(val stackId: String) : Route
    data object Unknown : Route
}
fun parseRoute(hash: String): Route      // "", "#", "#/" -> Home; "#/stack/<id>/edit"; "#/stack/<id>/play"; else Unknown
fun Route.toHash(): String               // Home -> "#/"; Unknown -> "#/"

data class AppState(val route: Route = Route.Home, val screen: Screen = Screen.Loading, val error: String? = null)
data class SideRef(val cardId: String, val face: Face)

sealed interface Screen {
    data object Loading : Screen
    data class StackList(val stacks: List<StackSummary>) : Screen
    data class Editor(val stack: Stack, val images: Map<String, String>, val saved: Boolean,
                      val imageError: SideRef? = null) : Screen
    data class Play(val stack: Stack, val images: Map<String, String>, val phase: PlayPhase) : Screen
    data class NotFound(val message: String) : Screen
}
sealed interface PlayPhase {
    data class Setup(val language: String, val mode: HintMode) : PlayPhase
    data class Asking(val session: Session, val hint: String?, val typed: String) : PlayPhase
    data class Revealed(val session: Session, val typed: String, val suggestion: Boolean?) : PlayPhase
    data class Summary(val result: SessionResult) : PlayPhase
}

sealed interface Action { val silent: Boolean get() = false }

// navigation and loading
data class Navigate(val route: Route) : Action
data class StackListLoaded(val stacks: List<StackSummary>) : Action
data class EditorLoaded(val stack: Stack, val images: Map<String, String>) : Action
data class PlayLoaded(val stack: Stack, val images: Map<String, String>, val storedLanguage: String?) : Action
data class StackMissing(val stackId: String) : Action
data class ErrorRaised(val message: String) : Action
data object ErrorDismissed : Action

// stack list
data object NewStackRequested : Action
data class DeleteStackConfirmed(val stackId: String) : Action
data class ExportRequested(val stackId: String) : Action
data class ImportFileRead(val text: String) : Action

// editor
data class StackRenamed(val name: String) : Action { override val silent get() = true }
data class LanguageAdded(val code: String) : Action
data class LanguageRemoved(val code: String) : Action
data class CardAdded(val cardId: String) : Action
data class CardDeleted(val cardId: String) : Action
data class CardMoved(val cardId: String, val delta: Int) : Action            // -1 up, +1 down
data class SideTextModeChanged(val cardId: String, val face: Face, val translated: Boolean) : Action
data class SideTextChanged(val cardId: String, val face: Face, val language: String?, val value: String) : Action {
    override val silent get() = true                                          // language is null for a Same text
}
data class ImageChosen(val cardId: String, val face: Face, val imageId: String, val dataUrl: String) : Action
data class ImageRemoved(val cardId: String, val face: Face) : Action
data class ImageRejected(val cardId: String, val face: Face) : Action
data class ImageSaveFailed(val cardId: String, val face: Face, val imageId: String) : Action
data class StackSaved(val stack: Stack) : Action
data class StackSaveFailed(val message: String) : Action

// play
data class PlayLanguageChosen(val language: String) : Action
data class HintModeChosen(val mode: HintMode) : Action
data class SessionStarted(val seed: Long, val cardIds: List<String>? = null) : Action   // null: all complete cards
data class AnswerTyped(val value: String) : Action { override val silent get() = true }
data object AnswerSubmitted : Action
data class CardGraded(val knew: Boolean) : Action

fun update(state: AppState, action: Action): AppState
```

Cases `update` handles in this task. Any action not listed returns `state` unchanged for now.

| Action | Result |
|---|---|
| `Navigate(route)` | `route` set, `error` cleared, `screen` is `NotFound("Page not found.")` for `Unknown`, else `Loading` |
| `StackListLoaded` | `StackList` if `route == Home`, else unchanged |
| `EditorLoaded` | `Editor(stack, images, saved = true)` if `route == Edit(stack.id)`, else unchanged |
| `PlayLoaded` | handled in Task 8 |
| `StackMissing(id)` | `NotFound("This stack does not exist.")` if route is `Edit(id)` or `Play(id)`, else unchanged |
| `ErrorRaised(m)` | `error = m` |
| `ErrorDismissed` | `error = null` |
| stack list actions | unchanged (they only trigger effects) |

- [ ] **Step 1: Write the failing tests.**

```kotlin
// RouteTest
@Test fun parses() {
    assertEquals(Route.Home, parseRoute(""));  assertEquals(Route.Home, parseRoute("#/"))
    assertEquals(Route.Edit("abc"), parseRoute("#/stack/abc/edit"))
    assertEquals(Route.Play("abc"), parseRoute("#/stack/abc/play"))
    assertEquals(Route.Unknown, parseRoute("#/stack/abc"))
    assertEquals(Route.Unknown, parseRoute("#/nope"))
}
@Test fun roundTrips() = listOf(Route.Home, Route.Edit("a"), Route.Play("a")).forEach {
    assertEquals(it, parseRoute(it.toHash()))
}

// UpdateTest
private val stack = Stack("s1", "S", listOf("en"), emptyList())

@Test fun navigateShowsLoadingAndClearsError() {
    val s = update(AppState(error = "boom"), Navigate(Route.Edit("s1")))
    assertEquals(AppState(Route.Edit("s1"), Screen.Loading, null), s)
}
@Test fun unknownRouteIsNotFound() =
    assertEquals(Screen.NotFound("Page not found."), update(AppState(), Navigate(Route.Unknown)).screen)
@Test fun listLoads() =
    assertEquals(Screen.StackList(emptyList()), update(AppState(), StackListLoaded(emptyList())).screen)
@Test fun editorLoads() {
    val s = update(AppState(Route.Edit("s1")), EditorLoaded(stack, emptyMap()))
    assertEquals(Screen.Editor(stack, emptyMap(), saved = true), s.screen)
}
@Test fun staleLoadsAreIgnored() {                                     // Review Focus 1
    val onList = AppState(Route.Home, Screen.StackList(emptyList()))
    assertEquals(onList, update(onList, EditorLoaded(stack, emptyMap())))
    val onOther = AppState(Route.Edit("other"))
    assertEquals(onOther, update(onOther, EditorLoaded(stack, emptyMap())))
    val onEditor = AppState(Route.Edit("s1"))
    assertEquals(onEditor, update(onEditor, StackListLoaded(emptyList())))
    assertEquals(onEditor, update(onEditor, StackMissing("other")))
}
@Test fun missingStack() =
    assertEquals(Screen.NotFound("This stack does not exist."),
        update(AppState(Route.Play("s1")), StackMissing("s1")).screen)
@Test fun errorBanner() {
    assertEquals("x", update(AppState(), ErrorRaised("x")).error)
    assertNull(update(AppState(error = "x"), ErrorDismissed).error)
}
```

- [ ] **Step 2: Run** `./gradlew jsBrowserTest --tests "dev.silas.flipcards.state.*"`. Expected: compilation fails.
- [ ] **Step 3: Implement the four files.** `update` is a `when` over the action; editor actions delegate to `updateEditor(state, action)` in `EditorUpdate.kt` and play actions to `updatePlay(state, action)` in `PlayUpdate.kt`. Create both now as functions that return `state`.
- [ ] **Step 4: Run again.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat: add routes, state and core update`.

---

### Task 7: Editor update

**Files:**
- Modify: `src/commonMain/kotlin/dev/silas/flipcards/state/EditorUpdate.kt`
- Test: `src/commonTest/kotlin/dev/silas/flipcards/state/EditorUpdateTest.kt`

**Interfaces:**
- Consumes: Task 6 types.
- Produces: `internal fun updateEditor(state: AppState, action: Action): AppState`. Every case is a no-op unless `state.screen` is `Screen.Editor`. Every case that changes the stack sets `saved = false`.

| Action | Behaviour |
|---|---|
| `StackRenamed(name)` | sets the name as typed |
| `LanguageAdded(code)` | trims `code`; ignored if blank or already present; appended |
| `LanguageRemoved(code)` | ignored if it is the only language; removed from `languages` and from every `Translated.values` |
| `CardAdded(id)` | appends `Card(id)` |
| `CardDeleted(id)` | removes the card and its image ids from `images` |
| `CardMoved(id, delta)` | swaps with the neighbour; no-op at either end |
| `SideTextModeChanged(.., translated = true)` | `Same(v)` becomes `Translated(mapOf(firstLanguage to v))`; no text becomes `Translated(emptyMap())` |
| `SideTextModeChanged(.., translated = false)` | `Translated` becomes `Same(value for first language, or "")`; no text becomes `Same("")` |
| `SideTextChanged(.., language = null, value)` | text becomes `Same(value)` |
| `SideTextChanged(.., language = l, value)` | sets `values[l]` on the `Translated` text (creating it if the side has no text) |
| `ImageChosen(card, face, imageId, dataUrl)` | sets the side's `imageId`, adds the data URL to `images`, removes the side's previous image from `images`, clears `imageError` |
| `ImageRemoved(card, face)` | clears `imageId`, removes it from `images` |
| `ImageRejected(card, face)` | `imageError = SideRef(card, face)`; stack unchanged, `saved` unchanged |
| `ImageSaveFailed(card, face, imageId)` | if that side still has `imageId`: clears it, removes it from `images`; sets `error = "Could not store the image."` |
| `StackSaved(stack)` | `saved = (screen.stack == stack)` |
| `StackSaveFailed(m)` | `saved = false`, `error = m` |

- [ ] **Step 1: Write the failing tests.** Helper: `fun editing(stack: Stack, images: Map<String,String> = emptyMap()) = AppState(Route.Edit(stack.id), Screen.Editor(stack, images, saved = true))` and `val AppState.editor get() = screen as Screen.Editor`. Base stack: id `s1`, languages `en`, `de`, cards `a` (front `Same("x")` with image `i1`, back `Translated(en=Vienna, de=Wien)`), `b`, `c` (empty cards); images `i1`.

```kotlin
@Test fun renameMarksUnsaved() {
    val s = update(editing(stack), StackRenamed("New"))
    assertEquals("New", s.editor.stack.name); assertFalse(s.editor.saved)
}
@Test fun addLanguage() =
    assertEquals(listOf("en", "de", "es"), update(editing(stack), LanguageAdded(" es ")).editor.stack.languages)
@Test fun ignoresBlankAndDuplicateLanguages() {                        // Review Focus 5
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
    assertEquals(listOf("a", "b", "c", "d"), update(editing(stack), CardAdded("d")).editor.stack.cards.map { it.id })
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
    assertEquals(SideText.Same(""), update(editing(stack), SideTextModeChanged("b", Face.FRONT, false)).editor.stack.cards[1].front.text)
}
@Test fun editTexts() {
    assertEquals(SideText.Same("y"),
        update(editing(stack), SideTextChanged("a", Face.FRONT, null, "y")).editor.stack.cards[0].front.text)
    assertEquals(SideText.Translated(mapOf("en" to "Vienna", "de" to "Wien!")),
        update(editing(stack), SideTextChanged("a", Face.BACK, "de", "Wien!")).editor.stack.cards[0].back.text)
    assertEquals(SideText.Translated(mapOf("de" to "Haus")),
        update(editing(stack), SideTextChanged("b", Face.FRONT, "de", "Haus")).editor.stack.cards[1].front.text)
}
@Test fun chooseImageReplacesPrevious() {
    val s = update(editing(stack, images), ImageChosen("a", Face.FRONT, "i2", "data:image/jpeg;base64,CC")).editor
    assertEquals("i2", s.stack.cards[0].front.imageId)
    assertEquals(mapOf("i2" to "data:image/jpeg;base64,CC"), s.images)
}
@Test fun removeImage() {
    val s = update(editing(stack, images), ImageRemoved("a", Face.FRONT)).editor
    assertNull(s.stack.cards[0].front.imageId); assertEquals(emptyMap(), s.images)
}
@Test fun rejectedImageShowsErrorOnThatSide() {
    val rejected = update(editing(stack), ImageRejected("b", Face.BACK))
    assertEquals(SideRef("b", Face.BACK), rejected.editor.imageError); assertTrue(rejected.editor.saved)
    val chosen = update(rejected, ImageChosen("b", Face.BACK, "i3", "data:image/jpeg;base64,DD"))
    assertNull(chosen.editor.imageError)
}
@Test fun failedImageSaveUnlinksTheImage() {                           // Review Focus 4
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
    assertFalse(s.editor.saved); assertEquals("Could not save: full", s.error)
}
@Test fun editorActionsIgnoredElsewhere() {
    val list = AppState(Route.Home, Screen.StackList(emptyList()))
    assertEquals(list, update(list, CardAdded("x")))
}
```

- [ ] **Step 2: Run** `./gradlew jsBrowserTest --tests "dev.silas.flipcards.state.EditorUpdateTest"`. Expected: FAIL.
- [ ] **Step 3: Implement `updateEditor`.**
- [ ] **Step 4: Run again.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat: add editor state updates`.

---

### Task 8: Play update

**Files:**
- Modify: `src/commonMain/kotlin/dev/silas/flipcards/state/PlayUpdate.kt`, `Update.kt` (the `PlayLoaded` case)
- Test: `src/commonTest/kotlin/dev/silas/flipcards/state/PlayUpdateTest.kt`

**Interfaces:**
- Consumes: Task 6 types; `startSession`, `grade`, `isFinished`, `result`, `currentCard`, `hintFor` (Task 4); `suggest` (Task 3).
- Produces: `internal fun updatePlay(state: AppState, action: Action): AppState`. Every case is a no-op unless `state.screen` is `Screen.Play`.

| Action | Phase | Behaviour |
|---|---|---|
| `PlayLoaded(stack, images, stored)` | any, `route == Play(stack.id)` | `Play(stack, images, Setup(language, HINTED))`, `language` is `stored` if the stack has it, else the first language |
| `PlayLanguageChosen(l)` | any | ignored if the stack lacks `l`. `Setup`: language set. `Asking`: session language set, hint recomputed with `hintFor`. `Revealed`: session language set. `Summary`: ignored |
| `HintModeChosen(m)` | `Setup` | mode set |
| `SessionStarted(seed, cardIds)` | `Setup` or `Summary` | language and mode from the setup or from the result. Cards: ids of `stack.completeCards`, filtered to `cardIds` when given. No cards: unchanged. Else `Asking(session, hintFor(..), typed = "")` |
| `AnswerTyped(v)` | `Asking` | `typed = v` |
| `AnswerSubmitted` | `Asking` | `Revealed(session, typed, suggest(typed, back text in play language))` |
| `CardGraded(knew)` | `Revealed` | `session.grade(knew)`; finished: `Summary(result)`; else `Asking` with a new hint and `typed = ""` |

- [ ] **Step 1: Write the failing tests.** Fixture: stack `s1`, languages `en`, `de`; card `a` front image `i`, back `Translated(en=Vienna, de=Wien)`; card `b` front `Same("Spain")`, back `Same("Madrid")`; card `c` front `Same("?")`, back image `j`; card `d` incomplete. Helper `fun setup(language = "en", mode = HintMode.LENGTH_ONLY) = AppState(Route.Play("s1"), Screen.Play(stack, images, PlayPhase.Setup(language, mode)))` and `val AppState.phase get() = (screen as Screen.Play).phase`.

```kotlin
@Test fun loadedUsesStoredLanguage() =
    assertEquals(PlayPhase.Setup("de", HintMode.HINTED),
        update(AppState(Route.Play("s1")), PlayLoaded(stack, images, "de")).phase)
@Test fun loadedFallsBackWhenStoredLanguageIsGone() {                  // Review Focus 2
    assertEquals(PlayPhase.Setup("en", HintMode.HINTED),
        update(AppState(Route.Play("s1")), PlayLoaded(stack, images, "fr")).phase)
    assertEquals(PlayPhase.Setup("en", HintMode.HINTED),
        update(AppState(Route.Play("s1")), PlayLoaded(stack, images, null)).phase)
}
@Test fun stalePlayLoadIgnored() {
    val home = AppState(Route.Home, Screen.StackList(emptyList()))
    assertEquals(home, update(home, PlayLoaded(stack, images, null)))
}
@Test fun setupChoices() {
    assertEquals(PlayPhase.Setup("de", HintMode.LENGTH_ONLY), update(setup(), PlayLanguageChosen("de")).phase)
    assertEquals(setup(), update(setup(), PlayLanguageChosen("fr")))
    assertEquals(PlayPhase.Setup("en", HintMode.NONE), update(setup(), HintModeChosen(HintMode.NONE)).phase)
}
@Test fun startSkipsIncompleteCards() {
    val asking = update(setup(), SessionStarted(1)).phase as PlayPhase.Asking
    assertEquals(setOf("a", "b", "c"), asking.session.queue.toSet())
    assertEquals(3, asking.session.total)
    assertEquals("", asking.typed)
}
@Test fun startWithNoCompleteCardsDoesNothing() {
    val empty = AppState(Route.Play("s1"),
        Screen.Play(stack.copy(cards = emptyList()), emptyMap(), PlayPhase.Setup("en", HintMode.NONE)))
    assertEquals(empty, update(empty, SessionStarted(1)))
}
@Test fun typeSubmitAndGrade() {
    var s = update(setup(), SessionStarted(1, listOf("b")))
    assertEquals("_ _ _ _ _ _", (s.phase as PlayPhase.Asking).hint)
    s = update(s, AnswerTyped("madrid"))
    s = update(s, AnswerSubmitted)
    assertEquals(true, (s.phase as PlayPhase.Revealed).suggestion)
    assertEquals("madrid", (s.phase as PlayPhase.Revealed).typed)
    s = update(s, CardGraded(true))
    assertEquals(SessionResult("en", HintMode.LENGTH_ONLY, 1, emptyList()), (s.phase as PlayPhase.Summary).result)
}
@Test fun wrongAndEmptyAnswers() {
    val asked = update(setup(), SessionStarted(1, listOf("b")))
    assertEquals(false, (update(update(asked, AnswerTyped("Lisbon")), AnswerSubmitted).phase as PlayPhase.Revealed).suggestion)
    assertNull((update(asked, AnswerSubmitted).phase as PlayPhase.Revealed).suggestion)
}
@Test fun imageOnlyBackHasNoHintAndNoSuggestion() {
    val asked = update(setup(), SessionStarted(1, listOf("c")))
    assertNull((asked.phase as PlayPhase.Asking).hint)
    assertNull((update(update(asked, AnswerTyped("x")), AnswerSubmitted).phase as PlayPhase.Revealed).suggestion)
}
@Test fun missedCardComesBack() {
    var s = update(setup(), SessionStarted(1, listOf("b")))
    s = update(update(s, AnswerSubmitted), CardGraded(false))
    val again = s.phase as PlayPhase.Asking
    assertEquals(listOf("b"), again.session.queue); assertEquals("", again.typed)
    s = update(update(s, AnswerSubmitted), CardGraded(true))
    assertEquals(listOf("b"), (s.phase as PlayPhase.Summary).result.missed)
}
@Test fun switchLanguageMidSession() {
    var s = update(setup(), SessionStarted(1, listOf("a")))
    assertEquals("_ _ _ _ _ _", (s.phase as PlayPhase.Asking).hint)
    s = update(update(s, AnswerTyped("wi")), PlayLanguageChosen("de"))
    val asking = s.phase as PlayPhase.Asking
    assertEquals("_ _ _ _", asking.hint); assertEquals("de", asking.session.language); assertEquals("wi", asking.typed)
    s = update(update(s, AnswerTyped("wien")), AnswerSubmitted)
    assertEquals(true, (s.phase as PlayPhase.Revealed).suggestion)
}
@Test fun replayFromSummary() {
    var s = update(setup("de", HintMode.NONE), SessionStarted(1, listOf("a", "b")))
    repeat(2) { s = update(update(s, AnswerSubmitted), CardGraded(it == 0)) }   // first known, second missed
    s = update(update(s, AnswerSubmitted), CardGraded(true))
    val missed = (s.phase as PlayPhase.Summary).result.missed
    assertEquals(1, missed.size)
    val again = update(s, SessionStarted(2, missed)).phase as PlayPhase.Asking
    assertEquals(missed, again.session.queue)
    assertEquals("de", again.session.language); assertEquals(HintMode.NONE, again.session.mode)
    assertEquals(2, (update(s, SessionStarted(2)).phase as PlayPhase.Asking).session.total)
}
```

- [ ] **Step 2: Run** `./gradlew jsBrowserTest --tests "dev.silas.flipcards.state.PlayUpdateTest"`. Expected: FAIL.
- [ ] **Step 3: Implement `updatePlay` and the `PlayLoaded` case.**
- [ ] **Step 4: Run all tests:** `./gradlew jsBrowserTest`. Expected: PASS.
- [ ] **Step 5: Commit:** `feat: add play state updates`.

---

### Task 9: Storage

**Files:**
- Create: `src/commonMain/kotlin/dev/silas/flipcards/storage/Storage.kt`
- Create: `src/commonTest/kotlin/dev/silas/flipcards/storage/InMemoryStorage.kt`
- Create: `src/jsMain/kotlin/dev/silas/flipcards/storage/IndexedDb.kt`, `IndexedDbStorage.kt`
- Test: `src/jsTest/kotlin/dev/silas/flipcards/storage/IndexedDbStorageTest.kt`

**Interfaces:**
- Produces:

```kotlin
interface Storage {
    suspend fun loadStackSummaries(): List<StackSummary>          // sorted by name, case-insensitive
    suspend fun loadStack(id: String): Stack?
    suspend fun saveStack(stack: Stack)
    suspend fun deleteStack(id: String)                           // also deletes its images
    suspend fun loadImages(stackId: String): Map<String, String>  // image id -> data URL
    suspend fun saveImage(stackId: String, imageId: String, dataUrl: String)
    suspend fun deleteImage(imageId: String)
    suspend fun importStack(stack: Stack, images: Map<String, String>)   // one transaction; fails if any id exists
}

class InMemoryStorage : Storage {            // commonTest
    var failSaves: Boolean                   // when true, saveStack and saveImage throw IllegalStateException("full")
    val stacks: MutableMap<String, Stack>
    val images: MutableMap<String, Pair<String, String>>   // image id -> (stackId, dataUrl)
}

class IndexedDbStorage private constructor(/* db */) : Storage {
    companion object { suspend fun open(name: String = "flipcards"): IndexedDbStorage }   // throws if IndexedDB is unavailable
}
```

IndexedDB layout: database version 1. Object store `stacks`, keyPath `id`, records `{ id, json }` where `json` is `FlipJson.encodeToString(stack)`. Object store `images`, keyPath `id`, records `{ id, stackId, dataUrl }`, with an index `stackId` on `stackId`.

`IndexedDb.kt` holds minimal `external` declarations for only what is used (`indexedDB`, `IDBFactory.open`, `IDBOpenDBRequest.onupgradeneeded`, `IDBDatabase.createObjectStore/transaction`, `IDBTransaction.objectStore/oncomplete/onerror/onabort`, `IDBObjectStore.put/add/get/getAll/delete/createIndex/index`, `IDBIndex.getAll/getAllKeys`, `IDBRequest.onsuccess/onerror/result/error`) plus two helpers: `suspend fun <T> IDBRequest<T>.await(): T` and `suspend fun IDBTransaction.awaitComplete()`, both built on `suspendCancellableCoroutine`. `importStack` uses `add` (not `put`) inside one read-write transaction over both stores, so a duplicate id aborts everything.

- [ ] **Step 1: Write the failing test.** Each test opens its own database: `IndexedDbStorage.open("test-" + Random.nextLong())`. Tests use `runTest`.

```kotlin
@Test fun savesAndLoadsStacks() = runTest {
    val db = open()
    db.saveStack(stackB); db.saveStack(stackA)
    assertEquals(stackA, db.loadStack("a"))
    assertNull(db.loadStack("nope"))
    assertEquals(listOf("Alpha", "beta"), db.loadStackSummaries().map { it.name })
    assertEquals(stackA.cards.size, db.loadStackSummaries()[0].cardCount)
}
@Test fun saveOverwrites() = runTest {
    val db = open(); db.saveStack(stackA); db.saveStack(stackA.copy(name = "Renamed"))
    assertEquals("Renamed", db.loadStack("a")!!.name); assertEquals(1, db.loadStackSummaries().size)
}
@Test fun imagesAreScopedToTheirStack() = runTest {
    val db = open()
    db.saveImage("a", "i1", "data:image/jpeg;base64,AA"); db.saveImage("b", "i2", "data:image/jpeg;base64,BB")
    assertEquals(mapOf("i1" to "data:image/jpeg;base64,AA"), db.loadImages("a"))
    db.deleteImage("i1")
    assertEquals(emptyMap(), db.loadImages("a"))
}
@Test fun deletingAStackDeletesItsImages() = runTest {
    val db = open(); db.saveStack(stackA); db.saveImage("a", "i1", "data:image/jpeg;base64,AA")
    db.saveImage("b", "i2", "data:image/jpeg;base64,BB")
    db.deleteStack("a")
    assertNull(db.loadStack("a")); assertEquals(emptyMap(), db.loadImages("a"))
    assertEquals(1, db.loadImages("b").size)
}
@Test fun importWritesStackAndImages() = runTest {
    val db = open(); db.importStack(stackA, mapOf("i1" to "data:image/jpeg;base64,AA"))
    assertEquals(stackA, db.loadStack("a")); assertEquals(setOf("i1"), db.loadImages("a").keys)
}
@Test fun failedImportStoresNothing() = runTest {
    val db = open(); db.saveImage("other", "i1", "data:image/jpeg;base64,ZZ")
    assertFails { db.importStack(stackA, mapOf("i0" to "data:image/jpeg;base64,AA", "i1" to "data:image/jpeg;base64,BB")) }
    assertNull(db.loadStack("a")); assertEquals(emptyMap(), db.loadImages("a"))
    assertEquals(mapOf("i1" to "data:image/jpeg;base64,ZZ"), db.loadImages("other"))
}
```

- [ ] **Step 2: Run** `./gradlew jsBrowserTest --tests "dev.silas.flipcards.storage.IndexedDbStorageTest"`. Expected: compilation fails.
- [ ] **Step 3: Implement `Storage.kt`, `IndexedDb.kt`, `IndexedDbStorage.kt` and `InMemoryStorage.kt`.** `InMemoryStorage.importStack` also rejects existing ids, so it behaves like the real one.
- [ ] **Step 4: Run again.** Expected: PASS.
- [ ] **Step 5: Commit:** `feat: add IndexedDB storage`.

---

### Task 10: Effects

**Files:**
- Create: `src/commonMain/kotlin/dev/silas/flipcards/effects/Env.kt`, `Effects.kt`
- Create: `src/commonTest/kotlin/dev/silas/flipcards/effects/FakeEnv.kt`
- Test: `src/commonTest/kotlin/dev/silas/flipcards/effects/EffectsTest.kt`

**Interfaces:**
- Consumes: `Storage` (Task 9), actions and state (Tasks 6 to 8), transfer functions (Task 5).
- Produces:

```kotlin
interface Env {
    val storage: Storage
    fun newId(): String
    fun download(fileName: String, text: String)
    fun navigate(route: Route)                       // sets the URL hash; the hashchange listener dispatches Navigate
    fun loadPlayLanguage(stackId: String): String?
    fun savePlayLanguage(stackId: String, language: String)
}

class Effects(
    private val env: Env,
    private val scope: CoroutineScope,
    private val state: () -> AppState,               // the store's current state
    private val dispatch: (Action) -> Unit,
) {
    fun handle(action: Action, before: AppState, after: AppState)   // called by the store after update()
}

class FakeEnv(override val storage: InMemoryStorage = InMemoryStorage()) : Env {   // commonTest
    val downloads: MutableList<Pair<String, String>>; val navigations: MutableList<Route>
    val playLanguages: MutableMap<String, String>     // newId() returns "id1", "id2", ...
}
```

What `handle` does:

| Trigger | Effect |
|---|---|
| `Navigate(Home)` | `StackListLoaded(loadStackSummaries())` |
| `Navigate(Edit(id))` | stack missing: `StackMissing(id)`; else `EditorLoaded(stack, loadImages(id))` |
| `Navigate(Play(id))` | stack missing: `StackMissing(id)`; else `PlayLoaded(stack, loadImages(id), env.loadPlayLanguage(id))` |
| any `Navigate` while `before.screen` is an `Editor` with `saved == false` | cancel the pending autosave and save `before`'s stack first |
| `NewStackRequested` | save `Stack(env.newId(), "New stack", listOf("en"), emptyList())`, then `env.navigate(Route.Edit(id))` |
| `DeleteStackConfirmed(id)` | `deleteStack(id)`, then `StackListLoaded(loadStackSummaries())` |
| `ExportRequested(id)` | `env.download(exportFileName(stack.name), encodeExport(buildExport(stack, images)))` |
| `ImportFileRead(text)` | `parseImport`; on `ImportException` dispatch `ErrorRaised(e.message)`. Else `withFreshIds(file, env::newId)`, name through `uniqueName` against existing names, `importStack`, then `StackListLoaded(...)` |
| `before` and `after` are both `Editor` and their `stack` differs | restart a 500 ms timer; when it fires, `saveStack(state()'s editor stack)` then `StackSaved(thatStack)`; on failure `StackSaveFailed("Could not save: ${e.message}")` |
| `ImageChosen` | `saveImage(stackId, imageId, dataUrl)`; on failure `ImageSaveFailed(cardId, face, imageId)` |
| image ids in `before`'s editor `images` that are absent from `after`'s | `deleteImage` for each |
| `PlayLanguageChosen(l)` when `after.screen` is `Play` and the stack has `l` | `env.savePlayLanguage(stackId, l)` |
| any other storage exception | `ErrorRaised("Storage error: ${e.message}")` |

- [ ] **Step 1: Write the failing tests.** Use `runTest`; build `Effects(env, backgroundScope or this, { current }, { dispatched += it })` with a `StandardTestDispatcher`, and a helper `fun run(action)` that applies `update`, stores the new state in `current` and calls `handle`. Use `advanceUntilIdle()` and `advanceTimeBy()`.

```kotlin
@Test fun navigateHomeLoadsSummaries()        // storage has stackA -> dispatched == [StackListLoaded([summary])]
@Test fun navigateToMissingStack()            // Navigate(Edit("x")) -> [StackMissing("x")]
@Test fun navigateToEditorLoadsStackAndImages()
@Test fun navigateToPlayPassesStoredLanguage()   // env.playLanguages["a"] = "de" -> PlayLoaded(stackA, images, "de")
@Test fun newStackIsSavedAndOpened()          // storage.stacks["id1"] == Stack("id1","New stack",["en"],[]); navigations == [Edit("id1")]
@Test fun deleteReloadsList()
@Test fun exportDownloadsFile()               // downloads[0].first == "alpha.flipcards.json"; parseImport(downloads[0].second).stack.name == "Alpha"
@Test fun importStoresACopyWithNewIdsAndUniqueName()
    // storage already has "Alpha"; import an export of it -> a second stack named "Alpha (2)", ids differ, its image stored under the new stack id
@Test fun invalidImportRaisesErrorAndStoresNothing()   // ImportFileRead("nope") -> [ErrorRaised("This is not a Flipcards stack file.")]; storage unchanged
@Test fun editsAreSavedAfter500ms() {
    // in editor; run(StackRenamed("X")); advanceTimeBy(499) -> storage still has old name
    // advanceTimeBy(2) -> storage has "X"; dispatched contains StackSaved(stack named "X")
}
@Test fun rapidEditsSaveOnce()                // three renames 100 ms apart -> exactly one StackSaved, with the last name
@Test fun failedSaveReportsError()            // storage.failSaves = true -> StackSaveFailed("Could not save: full")
@Test fun leavingTheEditorSavesPendingChanges() {                     // Review Focus 3
    // run(StackRenamed("X")); run(Navigate(Route.Home)) with no time advanced beyond runCurrent()
    // -> storage.stacks["a"].name == "X"
}
@Test fun chosenImageIsStored()               // storage.images["i9"] == ("a", dataUrl)
@Test fun failedImageSaveIsReported()         // failSaves = true -> dispatched contains ImageSaveFailed("c1", Face.FRONT, "i9")   // Review Focus 4
@Test fun replacedAndRemovedImagesAreDeleted()   // ImageChosen over an existing image, ImageRemoved, CardDeleted each remove the old id from storage
@Test fun playLanguageIsRemembered()          // PlayLanguageChosen("de") in Setup -> env.playLanguages["a"] == "de"
```

Each named test asserts exactly what its comment states.

- [ ] **Step 2: Run** `./gradlew jsBrowserTest --tests "dev.silas.flipcards.effects.EffectsTest"`. Expected: compilation fails.
- [ ] **Step 3: Implement `Env.kt`, `Effects.kt`, `FakeEnv.kt`.** The autosave timer is a `Job` held by `Effects`, cancelled and relaunched on each stack change, using `delay(500)`.
- [ ] **Step 4: Run all tests:** `./gradlew jsBrowserTest`. Expected: PASS.
- [ ] **Step 5: Commit:** `feat: add effects`.

---

### Task 11: Store, browser layer and stack list screen

**Files:**
- Create: `src/jsMain/kotlin/dev/silas/flipcards/Store.kt`, `browser/Browser.kt`, `ui/Layout.kt`, `ui/StackListView.kt`
- Modify: `src/jsMain/kotlin/dev/silas/flipcards/Main.kt`

**Interfaces:**
- Consumes: everything in `commonMain`; `IndexedDbStorage.open()`.
- Produces:

```kotlin
// browser/Browser.kt
fun newId(): String                                          // crypto.randomUUID()
fun downloadText(fileName: String, text: String)             // Blob (application/json) + object URL + temporary <a download>
suspend fun readFileText(file: File): String
suspend fun downscaleToJpegDataUrl(file: File, maxSide: Int = 800, quality: Double = 0.85): String?   // null if not decodable
class BrowserEnv(override val storage: Storage) : Env        // localStorage key "flipcards.playLanguage.<stackId>"; navigate sets window.location.hash

// Store.kt
class Store(private val root: HTMLElement, env: Env, private val scope: CoroutineScope) {
    var state: AppState
    fun dispatch(action: Action)   // update, render unless action.silent, then effects.handle(action, before, after)
}

// ui/Layout.kt
typealias Dispatch = (Action) -> Unit
fun render(root: HTMLElement, state: AppState, dispatch: Dispatch, scope: CoroutineScope)  // clears root; banner; screen
fun renderFatal(root: HTMLElement)

// ui/StackListView.kt
fun TagConsumer<HTMLElement>.stackListView(screen: Screen.StackList, dispatch: Dispatch, scope: CoroutineScope)
```

Behaviour and copy:

- `render` writes `<main class="page">`. When `state.error` is set, a `div.banner` with `role="alert"` shows the message and a `Dismiss` button (`ErrorDismissed`). Then one of: `Loading` shows `Loading…`; `NotFound` shows the message and a link `Back to stacks` to `#/`; the other screens call their view (editor and play views arrive in Tasks 12 and 13; until then show their screen name).
- After rendering, `render` focuses the first element with the attribute `data-autofocus`, if any.
- `renderFatal` shows: `Flipcards needs local storage (IndexedDB), which is not available in this browser mode.`
- Stack list: `h1` `Flipcards`; buttons `New stack` (`NewStackRequested`) and `Import`. `Import` clicks a hidden `<input type="file" accept=".json,application/json">`; on change, read the file with `readFileText` and dispatch `ImportFileRead(text)`, then clear the input's value so the same file can be picked again.
- Empty list: `No stacks yet. Create one or import a file.`
- Each stack is an `li.stack`: the name, a line `N cards · en, de` (`1 card` in the singular), and actions: links `Play` (`#/stack/<id>/play`) and `Edit` (`#/stack/<id>/edit`), buttons `Export` (`ExportRequested`) and `Delete`. `Delete` asks `window.confirm("Delete \"<name>\" and all its cards?")` and on yes dispatches `DeleteStackConfirmed`.
- `Main.kt`: create `MainScope()`. Try `IndexedDbStorage.open()`; on failure call `renderFatal` and stop. Otherwise create the `Store`, dispatch `Navigate(parseRoute(window.location.hash))`, and do the same on every `hashchange` event.

- [ ] **Step 1: Implement the files above.**
- [ ] **Step 2: Run all tests** to confirm nothing broke: `./gradlew jsBrowserTest`. Expected: PASS.
- [ ] **Step 3: Check by hand.** Run `./gradlew jsBrowserDevelopmentRun --continuous`, open the printed URL in a 390 px wide browser window and confirm each line:
  - the empty message shows; `New stack` changes the URL to `#/stack/<id>/edit`; going back shows `New stack`, `0 cards · en`
  - `Export` downloads `new-stack.flipcards.json`; importing that file adds `New stack (2)`
  - importing any other `.json` shows the banner `This is not a Flipcards stack file.` and `Dismiss` removes it
  - `Delete` asks for confirmation and removes the stack; a reload keeps the remaining stacks
  - `#/nope` shows `Page not found.`
- [ ] **Step 4: Commit:** `feat: add store, browser layer and stack list`.

---

### Task 12: Editor screen

**Files:**
- Create: `src/jsMain/kotlin/dev/silas/flipcards/ui/EditorView.kt`
- Modify: `src/jsMain/kotlin/dev/silas/flipcards/ui/Layout.kt` (call the view)

**Interfaces:**
- Consumes: `Screen.Editor`, editor actions, `newId`, `downscaleToJpegDataUrl`, `isComplete`.
- Produces: `fun TagConsumer<HTMLElement>.editorView(screen: Screen.Editor, dispatch: Dispatch, scope: CoroutineScope)`

Layout and copy, top to bottom:

- Link `← Stacks` to `#/`, and a link `Play` to `#/stack/<id>/play`.
- Text input labelled `Stack name`; `input` event dispatches `StackRenamed`.
- Status `span.save-status` with the id `save-status`: `Saved` or `Not saved`. Because silent actions skip the re-render, the store must also update this element's text directly after every dispatch while an editor is open. Add that to `Store.dispatch`.
- Section `Languages`: each language as a chip with a `×` button (`aria-label="Remove <code>"`), hidden when only one language is left. It asks `window.confirm("Remove language \"<code>\"? Its texts are deleted from every card.")` then dispatches `LanguageRemoved`. A text input with placeholder `Language code, e.g. de` and a button `Add language` dispatch `LanguageAdded`.
- One `section.card-editor` per card: heading `Card N`; a badge `Incomplete` when `!card.isComplete(firstLanguage)`; buttons `↑` (`aria-label="Move up"`), `↓` (`aria-label="Move down"`) and `Delete` (confirm `Delete this card?`).
- Inside each card, two `fieldset.side` elements with legends `Front` and `Back`, each containing:
  - a checkbox `Translated` (`SideTextModeChanged`), checked when the text is `Translated`
  - not translated: one text input labelled `Text`; translated: one text input per stack language, labelled with the language code. `input` events dispatch `SideTextChanged`.
  - if the side has an image: `img.side-image` with `src` from `screen.images[imageId]` and an empty `alt`, and a button `Remove image`
  - `<input type="file" accept="image/*">` labelled `Choose image`. On change: `downscaleToJpegDataUrl(file)`; null dispatches `ImageRejected(cardId, face)`, otherwise `ImageChosen(cardId, face, newId(), dataUrl)`
  - when `screen.imageError` is this side: `p.field-error` with `This file is not an image the browser can read.`
- Button `Add card` dispatches `CardAdded(newId())`.

The `Incomplete` badge only updates on the next re-render (typing is silent). That is accepted.

- [ ] **Step 1: Implement `EditorView.kt`, wire it into `render`, add the save-status update to `Store.dispatch`.**
- [ ] **Step 2: Run all tests:** `./gradlew jsBrowserTest`. Expected: PASS.
- [ ] **Step 3: Check by hand** at 390 px width with the dev server:
  - typing in the name keeps focus and cursor; the status goes `Not saved` then `Saved` within a second; a reload shows the new name
  - add language `de`; tick `Translated` on a side and both inputs appear, with the existing text under `en`
  - adding ` en ` or an empty code changes nothing
  - choose a photo: a preview appears; choose a `.txt` file renamed to `.png`: the error text appears under that side only
  - a card with an empty back shows `Incomplete` after the next re-render
  - `↑`, `↓`, `Delete` and `Remove image` work; removing `de` asks first and removes the `de` inputs
  - rename the stack and immediately click `← Stacks`: the list shows the new name
- [ ] **Step 4: Commit:** `feat: add stack editor`.

---

### Task 13: Play screen

**Files:**
- Create: `src/jsMain/kotlin/dev/silas/flipcards/ui/PlayView.kt`
- Modify: `src/jsMain/kotlin/dev/silas/flipcards/ui/Layout.kt` (call the view)

**Interfaces:**
- Consumes: `Screen.Play`, `PlayPhase`, play actions, `currentCard`, `resolveText`, `completeCards`.
- Produces: `fun TagConsumer<HTMLElement>.playView(screen: Screen.Play, dispatch: Dispatch)`

Shared pieces:

- A side is drawn as `div.side`: the image if present (`img.side-image`, empty `alt`), then the resolved text if present.
- Language `<select>` labelled `Language` with one option per stack language; `change` dispatches `PlayLanguageChosen`. Shown in Setup, Asking and Revealed.
- New seeds come from `Random.nextLong()` in the event handler.

Per phase:

- **Setup:** `h1` with the stack name. Language select. Radio group `Hints` with `Hinted`, `Length only`, `No hint` (`HintModeChosen`). Button `Start` (`SessionStarted(seed)`, `data-autofocus`). If `stack.completeCards` is empty, replace the button with `This stack has no complete cards yet.` and a link `Open the editor`. Link `← Stacks`.
- **Asking:** line `N of M left` (`queue.size` of `total`). `div.flipcard` with the front side. If `hint` is set, `p.hint` with the hint in a monospace font. A `<form>` with a text input (placeholder `Your answer`, `autocomplete="off"`, `autocapitalize="off"`, `spellcheck="false"`, `data-autofocus`, value `typed`, `input` dispatches `AnswerTyped`) and a submit button `Show answer`. Submitting dispatches `AnswerSubmitted` and prevents the default. If the back has no text, omit the input.
- **Revealed:** `div.flipcard.revealed` with the back side. If `typed` is not blank: `Your answer: <typed>`. If `suggestion` is set: `Looks right` or `Looks different`. Buttons `Knew it` (`CardGraded(true)`) and `Didn't know` (`CardGraded(false)`); `data-autofocus` goes on `Knew it` when `suggestion == true`, on `Didn't know` when `false`, on neither when `null`.
- **Summary:** `h1` `<knownFirstTime> of <total> known first time`. If any were missed: heading `Missed cards` and a list, one `li` per missed card showing the front (text, or `(image)` when it has no text), `→`, and the back the same way, in the session language. Buttons `Play again` (`SessionStarted(seed)`), `Play missed cards only` (`SessionStarted(seed, missed)`, only when some were missed), link `Back to stacks`.

- [ ] **Step 1: Implement `PlayView.kt` and wire it into `render`.**
- [ ] **Step 2: Run all tests:** `./gradlew jsBrowserTest`. Expected: PASS.
- [ ] **Step 3: Check by hand** at 390 px width, with a stack that has a flag card (image front, translated back), a text card and an incomplete card:
  - Setup preselects the last used language after a reload
  - each hint mode shows the expected pattern; the incomplete card never appears
  - the answer input has focus on every new card; Enter reveals; typing `bogota` for `Bogotá` gives `Looks right` and Enter then accepts `Knew it`
  - a wrong answer focuses `Didn't know`; choosing `Knew it` anyway is possible
  - switching language mid-card changes the hint and the revealed answer
  - a missed card comes back; the summary counts it as missed and `Play missed cards only` plays just that card
  - a card with an image-only back has no input and no suggestion
- [ ] **Step 4: Commit:** `feat: add play screen`.

---

### Task 14: Mobile-first styles and README

**Files:**
- Modify: `src/jsMain/resources/styles.css`
- Create: `README.md`

**Interfaces:**
- Consumes: class names from Tasks 11 to 13: `page`, `banner`, `stack`, `save-status`, `card-editor`, `side`, `side-image`, `field-error`, `flipcard`, `revealed`, `hint`.

Rules the stylesheet must satisfy:

- Base styles are for a 320 px portrait phone: one column, `box-sizing: border-box` everywhere, `.page` with 16 px padding and `max-width: 720px` centred.
- Every `button`, `a` that acts as a button, `input`, `select` and file input label has `min-height: 44px`. Text inputs are 16 px font or larger (prevents iOS zoom on focus).
- Buttons in a row wrap; no element causes horizontal scrolling at 320 px. `.side-image` is `max-width: 100%`, `max-height: 40vh`, `object-fit: contain`.
- On the play screen at 320 × 568, the card, the hint, the answer input and the action buttons are visible together without scrolling.
- `.hint` is monospace with `white-space: pre-wrap` so the triple spaces between words survive.
- `.flipcard.revealed` plays a 300 ms `rotateY` keyframe animation once; inside `@media (prefers-reduced-motion: reduce)` the animation is off.
- `@media (min-width: 720px)`: the two `.side` fieldsets of a card sit side by side, and `.stack` shows its actions on the same row as its name.
- No `:hover`-only behaviour. Visible `:focus-visible` outlines. Colours meet WCAG AA contrast; support `prefers-color-scheme: dark` through CSS custom properties on `:root`.

README sections: what the app is (two sentences); `Running` (the dev, test and build commands, plus the `TMPDIR` note if Task 1 needed it, and `-PtestBrowser=chrome`); `Deploying` (the two manual GitHub steps from the spec); `Manual checklist` (the hand checks from Tasks 11, 12 and 13 copied as one checkbox list, with the instruction to run it at 320 px, 390 px and 1280 px widths).

- [ ] **Step 1: Write `styles.css` and `README.md`.**
- [ ] **Step 2: Check by hand** using the browser's responsive design mode at 320 × 568, 390 × 844 and 1280 × 800: run the README checklist at each size; confirm no horizontal scrollbar on any screen, the play screen fits without scrolling at 320 × 568, and the editor shows front and back side by side at 1280 px.
- [ ] **Step 3: Run all tests and the production build:** `./gradlew jsBrowserTest jsBrowserDistribution`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 4: Commit:** `feat: add mobile-first styles and README`.

---

### Task 15: GitHub Pages deployment

**Files:**
- Create: `.github/workflows/deploy.yml`

**Interfaces:**
- Consumes: `./gradlew jsBrowserTest -PtestBrowser=chrome jsBrowserDistribution`, output directory `build/dist/js/productionExecutable`.

- [ ] **Step 1: Write the workflow.**

```yaml
name: Deploy to GitHub Pages

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew jsBrowserTest -PtestBrowser=chrome jsBrowserDistribution
      - uses: actions/configure-pages@v6
      - uses: actions/upload-pages-artifact@v5
        with:
          path: build/dist/js/productionExecutable

  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v5
```

- [ ] **Step 2: Verify locally what the workflow will publish.** Run `./gradlew jsBrowserDistribution`, then serve the output from a sub-path to mimic Pages:

```bash
mkdir -p build/pages-check && rm -rf build/pages-check/flipcards
cp -r build/dist/js/productionExecutable build/pages-check/flipcards
python3 -m http.server 8099 --directory build/pages-check
```

Open `http://localhost:8099/flipcards/`. Expected: the app loads with styles, and creating a stack and reloading keeps it. Stop the server.

- [ ] **Step 3: Commit:** `ci: deploy to GitHub Pages`.
- [ ] **Step 4: Report the manual steps to the repository owner.** The workflow cannot be verified until they create the GitHub repository, push `main`, and set Settings → Pages → Source to "GitHub Actions". Do not push.

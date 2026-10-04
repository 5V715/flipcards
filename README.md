# Flipcards

A flashcard app that runs entirely in the browser. Cards live in stacks, can show text or
images, can be translated into several languages, and are stored only on your device.

It is a learning project for Kotlin/JS: the whole interface is built with
[kotlinx.html](https://github.com/Kotlin/kotlinx.html), with no JavaScript UI framework.

## Running

You need a JDK (17 or newer). Everything else is downloaded by the Gradle wrapper.

| What | Command |
|---|---|
| Development server with reload | `./gradlew jsBrowserDevelopmentRun --continuous` |
| All tests | `./gradlew jsBrowserTest` |
| One test class | `./gradlew jsBrowserTest --tests "dev.silas.flipcards.model.ModelTest"` |
| Production build | `./gradlew jsBrowserDistribution` (output in `build/dist/js/productionExecutable`) |

The tests run in a real browser through Karma, in headless Firefox, both locally and on
CI. The build points `TMPDIR` at `build/firefox-tmp`, because a Firefox installed as a
snap cannot read `/tmp`.

## Sample stacks

`samples/` has two stacks to import, both with English and German:

- `european-capitals.flipcards.json`: flags and country names, answer with the capital
- `spanish-basic-words.flipcards.json`: 30 everyday words, answer in Spanish

## How it is built

- `src/commonMain` holds everything that needs no browser: the model, the play logic, the
  import/export format, the app state and the `update(state, action)` function that is the
  only place state changes. `Effects` does the asynchronous work (loading, autosave,
  import, export) and reports back with new actions.
- `src/jsMain` holds the browser side: the `Store`, IndexedDB storage, file and image
  handling, and the views in `ui/`, which are functions from state to DOM.
- After every action the store rebuilds the current screen. Typing is the exception: those
  actions are marked `silent`, so the field keeps its focus and cursor.

The design is described in `docs/superpowers/specs/2026-10-04-flipcards-design.md`.

## Deploying

`.github/workflows/deploy.yml` tests, builds and publishes the app to GitHub Pages on
every push to `main`. Two things have to be done once by hand:

1. Create the GitHub repository and push `main` to it.
2. In the repository, set **Settings → Pages → Source** to **GitHub Actions**.

## Manual checklist

The automated tests cover the logic and what each screen renders. Check the following by
hand in the browser's responsive design mode at 320 px, 390 px and 1280 px width.

Stack list

- [ ] With no stacks, the empty message shows. `New stack` opens the editor; going back shows `New stack`, `0 cards · en`.
- [ ] `Export` downloads `new-stack.flipcards.json`; importing that file adds `New stack (2)`.
- [ ] Importing any other `.json` file shows `This is not a Flipcards stack file.`, and `Dismiss` removes it.
- [ ] `Delete` asks for confirmation and removes the stack. After a reload the remaining stacks are still there.
- [ ] `#/nope` shows `Page not found.`

Editor

- [ ] Typing in the name keeps focus and cursor. The status goes `Not saved`, then `Saved` within a second. A reload shows the new name.
- [ ] Add the language `de`. Ticking `Translated` on a side shows one field per language, with the existing text under `en`.
- [ ] Adding ` en ` or an empty code changes nothing.
- [ ] Choosing a photo shows a preview. Choosing a text file renamed to `.png` shows the error under that side only.
- [ ] A card with an empty back shows `Incomplete` (after the next change that redraws the page).
- [ ] `↑`, `↓`, `Delete` and `Remove image` work. Removing `de` asks first and removes the `de` fields.
- [ ] Rename the stack and immediately tap `← Stacks`: the list shows the new name.

Play (use a stack with a flag card, a text card and an incomplete card)

- [ ] Setup preselects the language used last time, also after a reload.
- [ ] Each hint mode shows the expected pattern. The incomplete card never appears.
- [ ] The answer field has the focus on every new card. Enter reveals. Typing `bogota` for `Bogotá` gives `Looks right`, and Enter then accepts `Knew it`.
- [ ] A wrong answer puts the focus on `Didn't know`; choosing `Knew it` anyway works.
- [ ] Switching the language in the middle of a card changes the hint and the revealed answer.
- [ ] A missed card comes back. The summary counts it as missed, and `Play missed cards only` plays just that card.
- [ ] A card whose back is only an image has no answer field and no suggestion.

Layout

- [ ] No screen scrolls sideways at 320 px.
- [ ] At 320 × 568 the play screen shows card, hint, answer field and buttons without scrolling.
- [ ] At 1280 px the editor shows front and back side by side.
- [ ] The app is readable in dark mode.

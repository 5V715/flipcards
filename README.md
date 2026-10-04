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

`samples/` has four stacks. In the app, `Samples` on the stack list shows every
`.flipcards.json` file in the `samples/` folder on the `main` branch on GitHub, and `Add` imports
one. A file pushed there shows up the next time the list is opened, without a new release of the
app. The list comes from GitHub's API, which allows 60 requests an hour per network without a login.
The files can also be imported by hand:

- `country-shapes.flipcards.json`: the outlines of 165 countries, answer with the name, in English,
  German, French or Spanish. Pick how many cards to play in the setup.
- `world-capitals.flipcards.json`: all 193 UN member states, with the country's name and outline,
  answer with the capital, in English, German, French or Spanish. For countries with more than one
  capital it asks for the official one (Sucre, Porto-Novo, Sri Jayawardenepura Kotte), else the seat
  of government (Mbabane).
- `european-capitals.flipcards.json`: flags and country names, answer with the capital, in English,
  German, French or Spanish
- `spanish-basic-words.flipcards.json`: 30 everyday words in English, German or French, answer in Spanish

The country outlines come from [Natural Earth](https://www.naturalearthdata.com/) (public domain)
through the [world-atlas](https://github.com/topojson/world-atlas) package. `samples/tools/` has the
scripts that made the country shapes and world capitals stacks; how to run them is at the top of
`country-shapes-svg.mjs` and `world-capitals-stack.cjs`.

## Languages

The interface is in English, German, Spanish or French. It follows the language picked last, on the
stack list or as the play language, and is remembered. Before the first pick it follows the browser.
A play language without a translation, such as `it`, shows the interface in English. All the texts
are in `src/commonMain/kotlin/dev/silas/flipcards/i18n/Strings.kt`; a new language is one more
`Strings` there plus an entry in `uiLanguages`.

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

- [ ] Picking `Deutsch` turns every label into German, also after a reload.
- [ ] `Samples` lists the stack files in `samples/` on `main`. `Add` shows `Adding…`, then `✓ Added`, and the stack appears below. Offline, a message says the list could not be loaded.
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
- [ ] Picking `de` as the play language turns the whole interface German, also the stack list afterwards.
- [ ] `Cards to play` starts at the number of complete cards. Setting it to 5 plays 5 random cards; `Play again` plays 5 again.
- [ ] Each hint mode shows the expected blanks. The incomplete card never appears.
- [ ] On every new card, typing fills the blanks straight away; on a phone, tapping the blanks opens the keyboard. Revealed letters and spaces are skipped, Backspace removes the last letter. Enter reveals. Typing `bogota` for `Bogotá` gives `Looks right`, and Enter then accepts `Knew it`.
- [ ] With `No hint`, the typed answer appears on one line with a caret.
- [ ] `Also show on the front` set to `de` shows the German text under the front text, and nothing extra when both read the same.
- [ ] Each card known the first time scores 10, 20 or 30 points, by hint mode. After a round the summary shows `New best score!` when it beat the best for that number of cards, and setup shows `Best score` for the chosen number, also after a reload. `Play missed cards only` never changes the best score.
- [ ] A wrong answer puts the focus on `Didn't know`; choosing `Knew it` anyway works.
- [ ] Switching the language in the middle of a card changes the hint and the revealed answer.
- [ ] A missed card comes back. The summary counts it as missed, and `Play missed cards only` plays just that card.
- [ ] A card whose back is only an image has no answer field and no suggestion.

Layout

- [ ] No screen scrolls sideways at 320 px.
- [ ] At 320 × 568 the play screen shows card, hint, answer field and buttons without scrolling.
- [ ] At 1280 px the editor shows front and back side by side.
- [ ] The app is readable in dark mode.

# Sausage

Sausage is an Android runtime for AI-generated graphical journeys built as extended SVG documents.

The first development slice loads a bundled [`first-card.svge`](app/src/main/assets/first-card.svge) document in a full-screen WebView. Tapping the card reveals a second state using only SVG, CSS and simple JavaScript.

The second slice adds a small runtime home screen and Android document picker. A user can select a self-contained `.svge` file, which Sausage reads through Android's URI grant, validates as UTF-8 SVG XML, and renders through the same controlled WebView path as the bundled example.

The third slice adds the first narrow host API. `sausage.storage` provides promise-based `get`, `set` and `remove` operations backed by application-isolated Android storage. The bundled Journey Card remembers whether it has been revealed after leaving or restarting Sausage, and resetting the card removes that saved progress.

Storage is scoped by the Sausage manifest application ID. Documents without a manifest receive a content-derived private scope for this early slice. Values must be JSON-compatible, individual values are limited to 64 KB, and the current per-application allowance is 256 KB.

The fourth slice introduces the first document-flow control. [`dream-note.svge`](app/src/main/assets/dream-note.svge) declares an SVG graphical slice followed by a full-width text area. Sausage renders that declaration as a real browser control in a controlled HTML shell, preserving platform cursor, selection, clipboard, keyboard, spellcheck and accessibility behaviour. The runtime uses Android keyboard insets to keep the focused control visible, and the note is automatically saved through `sausage.storage`.

The fifth slice adds one declarative `app:button`. Sausage renders a standard accessible browser button, validates its named action and invokes the matching document function. Dream Note's “Hold this dream” action scrolls back to its SVG slice and runs a repeatable SVG capture animation.

The sixth slice replaces the fixed flow shape with an ordered slice model. A screen can declare multiple `app:graphic`, `app:text-area` and `app:button` slices in any useful sequence; consecutive controls share a polished control card while graphical slices remain independent SVG regions. Dream Note now ends with a second animated confirmation graphic after its controls.

The seventh slice adds multiple declarative screens. A button with `target-screen` changes the active screen with a restrained transition, while Sausage maintains scroll-aware screen history and gives that history first refusal on Android Back. Dream Note can now move from its scrolling note journey to a separate review screen and return without losing the saved text.

The eighth slice adds the semantic `sausage.controls` API. Document scripts synchronously read and write existing standard-control values by declared key without depending on runtime-generated HTML. Dream Note reads its text area through `getValue`, counts the captured words and updates existing SVG content on the review screen.

The ninth slice adds the first typed value control: `app:choice`. Sausage turns a concise list of options into a polished, accessible browser radio group, persists the selection automatically, and exposes its selected string (or `null`) through the same `sausage.controls` API. Dream Note records how vivid a memory felt and includes that choice in its graphical review.

Current external-document limits:

- maximum size of 5 MB;
- UTF-8 XML with an SVG root;
- no document type declaration;
- self-contained resources only.

## Build

The project uses Android Gradle Plugin 9.0.1, Gradle 9.1 and Java 17. It compiles against the Android 36.1 SDK.

```shell
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

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

The tenth slice adds semantic control change events. `sausage.controls.onChange(key, listener)` reports typed values without exposing runtime-generated HTML and returns an idempotent unsubscribe function. Dream Note uses the restored choice as its initial state, then changes its existing SVG moon glow and status badge immediately when vividness changes.

The eleventh slice adds `app:switch`, the first Boolean standard control. Sausage renders a real browser checkbox with accessible switch semantics, while its semantic API strictly reads, writes, persists and reports `true` or `false`. Dream Note records whether the dream was lucid, reveals a live animated SVG aura when enabled, and includes lucidity in its review.

The twelfth slice adds `app:slider`, the first numeric standard control. Its finite bounds, step and initial value are validated before rendering; the semantic API strictly reads, writes and reports JavaScript numbers. Dragging updates the displayed value and Dream Note artwork continuously, while persistence waits for the committed change at release.

The thirteenth slice adds an application-isolated SQLite database through promise-based `sausage.db.execute(sql, parameters)` and `sausage.db.query(sql, parameters)`. Calls accept one parameterised statement at a time and return ordinary JavaScript objects and values without exposing Android database classes. Dream Note now creates its own journal table, stores a typed snapshot whenever a dream is held, queries the persisted entry count and displays that history on the review screen.

The fourteenth slice proves navigation between separate Sausage documents. Dream Note opens a sibling [`dream-journal.svge`](app/src/main/assets/dream-journal.svge) through `sausage.navigation.open(path)`; Sausage validates the relative path and matching manifest application ID, gives the journal a fresh SVG DOM and JavaScript context, and places the previous document on a native back stack. The journal queries the same private SQLite database and fills three existing graphical memory cards. `sausage.navigation.back()` and Android Back return to the previous document.

The fifteenth slice adds the first device capability through the semantic `app:photo` control. Sausage opens Android's system image chooser, keeps the selected file private to the current renderer session, places it into a declared SVG `<image>` target through a temporary blob URL and reports safe file metadata through `sausage.controls.onChange`. The bundled [`dream-token.svge`](app/src/main/assets/dream-token.svge) demonstrates the result with a restrained SVG photo reveal. Cancellation leaves the page unchanged, non-images and files over 15 MB are rejected, and no broad storage permission is requested.

The sixteenth slice adds two explicitly declared host capabilities. `sausage.location.current({ accuracy })` requests one foreground position, returns coordinates, metre accuracy, timestamp and whether a cached fallback was used, then detaches its Android listener. `sausage.notifications.show(options)`, `schedule(options)` and `cancel(id)` provide immediate and one-off local reminders through an application-scoped Android notification channel. Scheduled reminders use battery-conscious inexact alarms, survive the renderer closing but not a device reboot, and may be delivered later than their requested time. [`night-beacon.svge`](app/src/main/assets/night-beacon.svge) exercises both APIs, with Android runtime permission prompts occurring only at first use.

The seventeenth slice adds three small manifest-gated device services. `sausage.clipboard.readText()` and `writeText(text)` exchange bounded plain text with Android's clipboard, `sausage.share.text({ title, text })` opens the system share sheet without revealing target applications to the document, and `sausage.haptics.perform(pattern)` offers only the bounded `light`, `medium` and `success` feedback patterns. Night Beacon demonstrates all three; arbitrary clipboard formats, direct sharing targets and custom vibration waveforms remain unavailable.

Current external-document limits:

- maximum size of 5 MB;
- UTF-8 XML with an SVG root;
- no document type declaration;
- self-contained resources only.

Relative multi-document navigation is currently proven only for documents bundled inside Sausage. User-selected directory roots remain a later slice.

## Build

The project uses Android Gradle Plugin 9.0.1, Gradle 9.1 and Java 17. It compiles against the Android 36.1 SDK.

```shell
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

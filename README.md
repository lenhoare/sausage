# Sausage

Sausage is an Android runtime for AI-generated graphical journeys built as extended SVG documents.

The first development slice loads a bundled [`first-card.svge`](app/src/main/assets/first-card.svge) document in a full-screen WebView. Tapping the card reveals a second state using only SVG, CSS and simple JavaScript.

The second slice adds a small runtime home screen and Android document picker. A user can select a self-contained `.svge` file, which Sausage reads through Android's URI grant, validates as UTF-8 SVG XML, and renders through the same controlled WebView path as the bundled example.

The third slice adds the first narrow host API. `sausage.storage` provides promise-based `get`, `set` and `remove` operations backed by application-isolated Android storage. The bundled Journey Card remembers whether it has been revealed after leaving or restarting Sausage, and resetting the card removes that saved progress.

Storage is scoped by the Sausage manifest application ID. Documents without a manifest receive a content-derived private scope for this early slice. Values must be JSON-compatible, individual values are limited to 64 KB, and the current per-application allowance is 256 KB.

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

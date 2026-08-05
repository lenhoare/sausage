# Sausage Android Runtime — General Specification

**Document:** `project_spec.md`  
**Status:** Draft v0.1  
**Target:** Android runtime/viewer  
**File extension:** `.svge`  
**Runtime/product name:** Sausage  

> **Sausage is an SVG application runtime for polished graphical mobile experiences, extended with native controls and device capabilities where they are useful.**

> **Making beautiful mobile apps as easy for anyone with AI to generate and run as writing an SVG.**

---

## 1. Status conventions

This document uses the following labels:

- **DECIDED** — agreed project direction.
- **PROPOSED** — recommended initial design, not yet final.
- **OPEN** — requires a decision.
- **DEFERRED** — deliberately outside version 1.

Normative terms such as **MUST**, **SHOULD**, and **MAY** describe intended runtime behaviour.

---

## 2. Product definition

### 2.1 Core concept — DECIDED

Sausage defines an **SVG application profile plus an extension namespace**.

A Sausage application is fundamentally an SVG document. Standard SVG renderers display the standard SVG content and ignore application-specific semantics. The Sausage runtime interprets the additional application semantics.

Sausage is not:

- “SVG 3”;
- a fork or modification of the SVG standard;
- an HTML application packaged inside an SVG;
- a general replacement for Android;
- a new visual layout language parallel to SVG.

Sausage is primarily for graphical journeys, attractive content-led applications, animated experiences and simple interactive tools. It is not intended to compete with full application frameworks for complex software.

### 2.2 Design principle — DECIDED

SVG remains responsible for:

- the visual coordinate system;
- layout;
- shapes, paths, images, text and gradients;
- transforms and animation;
- visual styling;
- pointer hit regions;
- most visual buttons and custom controls.

The application extension adds only the application facilities that SVG does not naturally provide:

- editable native controls;
- application lifecycle;
- navigation;
- device capabilities;
- permissions;
- networking;
- persistence and local databases;
- notifications;
- access to Android system services.

### 2.3 Primary audience — DECIDED

The primary author is an AI coding agent acting for a user who may have no development experience. Humans must also be able to read and edit Sausage files directly.

The format SHOULD optimise for:

1. minimal boilerplate;
2. one-file applications;
3. predictable semantics;
4. easy generation and repair by language models;
5. useful rendering in ordinary SVG viewers;
6. a small, stable API rather than a large framework;
7. an easy path from an AI-generated file to a running application.

### 2.4 Reference application style — DECIDED

Sausage SHOULD be especially good at applications in which most of the experience is visual design, content and animation, with a modest amount of interaction.

Reference applications include:

- visually rich mindfulness, wellbeing and habit journeys;
- a polished lucid-dreaming companion with attractive guidance, a dream journal, progress displays, reminders and simple settings;
- an animated learning journey built from index cards, with graphical transitions, lightweight choices and saved progress;
- visual guides, interactive stories, personal journals, calculators and small dashboards.

These reference applications should guide implementation decisions. Features for complicated general-purpose programs SHOULD NOT be added until a real application demonstrates the need.

### 2.5 Simplicity and development approach — DECIDED

Sausage SHOULD choose the simplest model that supports the reference applications.

Version 1 is the eventual complete first profile, not a single development milestone. It is expected to be built through many small, usable and testable slices. Early slices MAY implement only a narrow subset of the v1 profile without removing broader capabilities from the intended v1 scope.

---

## 3. Terminology

- **Sausage** — the Android runtime/viewer and the overall project.
- **`.svge`** — the file extension for a Sausage application document.
- **Sausage document** — an SVG document using Sausage application semantics.
- **Host** — the native Android runtime.
- **SVG renderer** — the component rendering normal SVG content.
- **Application script** — JavaScript contained in or loaded by the Sausage document.
- **Native control** — an Android control composited over the SVG surface.
- **Capability** — a host-provided service such as camera, location or notifications.
- **Fallback graphic** — ordinary SVG content shown by standard renderers in place of a native application control.

---

## 4. File identity and namespace

### 4.1 File extension — PROPOSED

Sausage applications SHOULD use:

```text
.svge
```

The runtime MAY also open `.svg` files containing the Sausage application namespace.

A `.svge` file is XML-based SVG unless a future package format is introduced.

### 4.2 MIME type — PROPOSED

```text
application/svge+xml
```

This is provisional and not yet registered.

### 4.3 Application namespace — OPEN

Provisional example:

```xml
xmlns:app="https://sausage.dev/ns/app/1"
```

The `.svge` suffix is only the file extension. XML application semantics use the `app:` prefix.

The namespace URI identifies the vocabulary; it does not need to be dereferenced at runtime.

The prefix `app` is conventional but not semantically significant.

### 4.4 Root document — PROPOSED

A minimal document:

```xml
<svg xmlns="http://www.w3.org/2000/svg"
     xmlns:app="https://sausage.dev/ns/app/1"
     viewBox="0 0 360 800">
  <text x="24" y="48">Hello from Sausage</text>
</svg>
```

---

## 5. Extension shape

### 5.1 Hybrid namespace model — PROPOSED

The Sausage application profile SHOULD use:

- **namespaced attributes on normal SVG elements** for visual behaviour, controls, events, navigation and bindings;
- **namespaced elements inside `<metadata>`** for non-visual declarations such as manifest data, permissions and network policy;
- ordinary SVG `<script>` elements for application JavaScript.

This improves fallback rendering because normal SVG elements remain visible in standard renderers.

Example:

```xml
<svg xmlns="http://www.w3.org/2000/svg"
     xmlns:app="https://sausage.dev/ns/app/1"
     viewBox="0 0 360 800">

  <metadata>
    <app:manifest
        id="com.example.temperature"
        name="Temperature Converter"
        version="1.0">
      <app:permission name="storage" />
    </app:manifest>
  </metadata>

  <g id="temperature"
     app:control="text-field"
     app:value="20"
     app:input-type="decimal">
    <rect x="24" y="80" width="200" height="48" rx="8"
          fill="white" stroke="black" />
    <text x="36" y="111">20</text>
  </g>

  <g id="convert"
     role="button"
     tabindex="0"
     app:on-press="convertTemperature">
    <rect x="24" y="152" width="140" height="48" rx="8" />
    <text x="94" y="182" text-anchor="middle" fill="white">Convert</text>
  </g>

  <text id="result" x="24" y="232" />

  <script type="application/ecmascript"><![CDATA[
    function convertTemperature() {
      const c = Number(sausage.controls.getValue("temperature"));
      document.getElementById("result").textContent =
        `${c} °C = ${(c * 9 / 5 + 32).toFixed(1)} °F`;
    }
  ]]></script>
</svg>
```

### 5.2 Unknown-element behaviour — PROPOSED

Visual Sausage controls SHOULD NOT require standard renderers to display children of unknown namespaced elements.

Non-visual namespaced elements SHOULD normally be placed inside `<metadata>`.

---

## 6. Runtime architecture

### 6.1 Android implementation — PROPOSED

The Android runtime SHOULD be implemented as a native Kotlin application containing:

1. an SVG-capable document renderer;
2. a native overlay layer for Android controls;
3. a restricted JavaScript-to-host bridge;
4. capability modules for Android services;
5. a loader, validator and permission manager.

The initial implementation SHOULD use Android WebView as the SVG renderer, DOM implementation and JavaScript engine. WebView remains an implementation detail and is not exposed as a Sausage application component.

The Sausage profile defines application behaviour. Applications MUST NOT rely on unrelated HTML or browser features merely because the initial runtime uses WebView.

### 6.2 Shared document model — DECIDED

Application scripts manipulate the actual SVG DOM.

The SVG is not merely compiled into an unrelated internal UI tree. The runtime MAY build indexes and native mirrors for efficiency, but the SVG document remains the authoritative application document.

### 6.3 Processing sequence — PROPOSED

When opening a Sausage document, the runtime:

1. reads and parses the XML;
2. validates the SVG root and profile version;
3. extracts manifest and capability declarations;
4. checks security policy;
5. creates the SVG rendering surface;
6. injects the `sausage` host API;
7. discovers Sausage controls and event attributes;
8. creates native control overlays;
9. runs the application startup event;
10. synchronises DOM, native control state and lifecycle events.

### 6.4 Renderer isolation — PROPOSED

Each document MUST execute in its own isolated renderer context. Documents in the same application MAY share only explicitly application-scoped host services such as storage and the database.

An application MUST NOT receive arbitrary access to:

- Android Java/Kotlin objects;
- another Sausage application's data;
- unrestricted local files;
- unrestricted network origins;
- the runtime's own internal storage.

---

## 7. Coordinates and native-control compositing

### 7.1 Coordinate source — PROPOSED

A native control is anchored to the rendered bounds of its associated SVG element, normally a `<g>` containing its fallback graphic.

The runtime calculates the anchor's final screen-space rectangle after applying:

- the SVG `viewBox`;
- viewport scaling;
- ancestor transforms;
- screen density;
- orientation changes;
- scrolling or panning performed by the runtime.

### 7.2 Version 1 transform restrictions — PROPOSED

Native controls in v1 MUST resolve to an axis-aligned screen rectangle.

The following are supported:

- translation;
- uniform scale;
- non-uniform scale;
- normal `viewBox` scaling.

The following are not required in v1:

- rotation;
- skew;
- perspective;
- arbitrary clipping paths;
- masks;
- SVG filters applied to native controls.

If an unsupported transform is encountered, the runtime SHOULD show the SVG fallback and report a developer warning.

### 7.3 Layering — PROPOSED

Native controls are composited above the SVG surface.

Version 1 MAY support a small number of explicit native overlay layers, but it is not required to interleave arbitrary SVG and native elements by z-order.

### 7.4 Synchronisation — PROPOSED

The runtime MUST update a native control when its anchor changes due to:

- DOM attribute changes;
- visibility changes;
- screen navigation;
- orientation or viewport changes;
- runtime-supported animation.

Continuous high-frequency animation of native controls is not a v1 goal.

---

## 8. Native controls

### 8.1 General rule — DECIDED

Ordinary visual buttons, panels, labels and custom widgets SHOULD be built from SVG.

Native controls are used where Android platform behaviour materially improves the application, especially text entry, accessibility and system pickers.

### 8.2 Proposed v1 controls

| Control | `app:control` value | Status |
|---|---|---|
| Single-line text input | `text-field` | PROPOSED |
| Multiline text input | `text-area` | PROPOSED |
| Numeric input | `text-field` with input type | PROPOSED |
| Password input | `text-field` with input type | PROPOSED |
| Checkbox | `checkbox` | PROPOSED |
| Switch | `switch` | PROPOSED |
| Slider | `slider` | PROPOSED |
| Dropdown/select | `select` | PROPOSED |
| Date picker | `date-picker` | PROPOSED |
| Time picker | `time-picker` | PROPOSED |
| File picker trigger | host action, not persistent control | PROPOSED |
| Native button | `button` | OPEN |

### 8.3 Common attributes — PROPOSED

Possible common namespaced attributes:

```text
app:control
app:value
app:disabled
app:required
app:placeholder
app:label
app:input-type
app:min
app:max
app:step
app:options
app:on-change
app:on-focus
app:on-blur
```

Normal SVG attributes control the fallback appearance.

### 8.4 Styling — OPEN

Two possible models:

**A. Platform-native:** controls mostly use Android styling, with only size, theme and a few semantic options exposed.

**B. Constrained theming:** Sausage exposes colours, corner radius, font and similar properties while preserving native behaviour.

Full CSS-level styling of Android widgets is not proposed.

---

## 9. Events and scripting

### 9.1 Language — DECIDED

JavaScript is the only v1 application scripting language.

Reasons:

- existing SVG scripting model;
- direct DOM manipulation;
- mature embedded execution support;
- strong AI code-generation support;
- no need to invent a new language.

### 9.2 Standard SVG events — PROPOSED

Where practical, applications SHOULD use standard DOM events.

The application profile adds concise namespaced event attributes for host-aware or convenience events:

```xml
app:on-press="save"
app:on-change="temperatureChanged"
app:on-start="initialise"
app:on-resume="refresh"
app:on-back="handleBack"
```

An event attribute names a global function or a runtime-resolvable handler.

Inline JavaScript expressions in event attributes SHOULD be discouraged.

### 9.3 Host API — PROPOSED

The runtime exposes a single global object:

```javascript
sausage
```

Proposed modules:

```text
sausage.app
sausage.controls
sausage.navigation
sausage.storage
sausage.db
sausage.http
sausage.files
sausage.camera
sausage.location
sausage.clipboard
sausage.share
sausage.haptics
sausage.notifications
sausage.device
sausage.log
```

All asynchronous operations SHOULD return JavaScript promises.

Example:

```javascript
const position = await sausage.location.current({
  accuracy: "balanced"
});
```

### 9.4 DOM-to-native state — PROPOSED

Control state MUST be readable and writable through the host API.

Where practical, changes to namespaced attributes SHOULD also update the native control:

```javascript
const field = document.getElementById("name");
field.setAttributeNS(APP_NS, "app:value", "Len");
```

A simpler helper is preferred for application code:

```javascript
sausage.controls.setValue("name", "Len");
```

### 9.5 Dynamic application structure — PROPOSED

The initial runtime discovers screens, event attributes and native controls when a document loads. Application structure is declarative and SHOULD be present in the source document.

Application scripts MAY make simple changes to existing elements, including:

- changing text content and attributes;
- changing styles, classes and visibility;
- updating existing native-control values;
- running animations on existing SVG content.

The initial runtime is not required to discover dynamically created screens or native controls. Dynamic structural creation is deferred until a reference application demonstrates the need.

---

## 10. Screens and navigation

### 10.1 In-document screen model — PROPOSED

A v1 application MAY contain multiple screens as ordinary SVG groups:

```xml
<g id="home" app:screen="home">
  ...
</g>

<g id="settings" app:screen="settings" display="none">
  ...
</g>
```

Only the active screen is displayed.

Navigation:

```javascript
await sausage.navigation.go("settings");
sausage.navigation.back();
```

### 10.2 Navigation stack — PROPOSED

The runtime maintains an application-local back stack.

Android's Back gesture/button:

1. dismisses an open native picker or keyboard where appropriate;
2. navigates back within the Sausage application;
3. closes the application when the stack is empty, unless intercepted.

### 10.3 Document links — DECIDED

Version 1 MUST support navigation to another Sausage document. Authors SHOULD use ordinary SVG links:

```xml
<a href="settings.svge">
  <g role="button">
    <rect x="24" y="120" width="160" height="48" rx="8" />
    <text x="104" y="151" text-anchor="middle">Settings</text>
  </g>
</a>
```

Equivalent scripted navigation:

```javascript
await sausage.navigation.open("settings.svge");
```

Navigation rules:

- `#fragment` navigates within the current document.
- A relative `.svge` path opens inside Sausage and pushes the current document onto the back stack.
- A relative `.svg` file containing the application namespace MAY also open as an application document.
- Ordinary `http:` and `https:` links open in the user's browser by default.
- Remote `.svge` loading is a separate permission and policy decision; it is not implied by ordinary web links.
- Other local file types MAY be handed to an appropriate Android viewer after user confirmation.
- `javascript:` URLs and arbitrary URI schemes MUST be blocked.
- Relative paths are resolved against the application's document root and MUST NOT escape it.

A linked document belongs to the same application when it resolves inside the runtime-approved application root and is opened as part of the same runtime-managed application instance. A matching manifest ID is required metadata but is not sufficient proof of identity by itself. Linked documents share that application's permissions, key-value storage and SQLite database.

Each document has its own SVG DOM and JavaScript context. Documents do not share JavaScript globals. Application state that must cross document boundaries SHOULD use key-value storage or SQLite.

The runtime maintains a document back stack, so Android Back returns to the previous `.svge` file. Preservation of an inactive document's in-memory DOM and JavaScript state is best-effort; applications MUST persist important state.

---

## 11. Application lifecycle

### 11.1 Proposed events

```text
start
resume
pause
stop
back
orientationchange
resize
lowmemory
permissionchange
notificationopen
deeplink
```

### 11.2 State preservation — PROPOSED

The runtime SHOULD preserve the application DOM and JavaScript state during ordinary temporary pauses when Android permits.

Applications MUST use persistent storage for state that must survive process termination.

---

## 12. Manifest

### 12.1 Location — PROPOSED

The manifest is embedded in SVG metadata:

```xml
<metadata>
  <app:manifest
      id="com.example.app"
      name="Example App"
      version="1.0.0"
      profile-version="1">
    <app:description>A small example application.</app:description>
    <app:permission name="camera" reason="Scan a receipt" />
    <app:network origin="https://api.example.com" />
  </app:manifest>
</metadata>
```

### 12.2 Proposed fields

```text
id
name
version
profile-version
description
author
icon
start-screen
orientation
theme
minimum-runtime
```

### 12.3 Application ID — OPEN

The application ID could be:

- reverse-domain style, e.g. `com.example.app`;
- a URI;
- an arbitrary stable identifier.

It identifies the application to authors and tooling.

The manifest ID alone MUST NOT ultimately be treated as proof of identity because unrelated unsigned documents can declare the same value. A future trust model should combine it with a runtime-managed installation identity, approved application root or publisher signature when separating permissions and private data.

During the initial single-user development stage, the runtime MAY use a simpler provisional mapping while keeping this distinction in its internal design.

---

## 13. Permissions and capabilities

### 13.1 Declaration — PROPOSED

Sensitive capabilities MUST be declared in the manifest before use.

A declaration SHOULD include a human-readable reason.

### 13.2 Runtime request — PROPOSED

Manifest declaration does not itself grant access.

The runtime requests Android permission at first use, or during an explicit application permission flow.

### 13.3 Proposed v1 capabilities

| Capability | v1 scope |
|---|---|
| Camera | Still-image capture |
| Photo picker | Select one or more images |
| Microphone | Record a bounded audio clip |
| Location | Current position and optional updates |
| Haptics | Simple vibration patterns |
| Clipboard | Read/write with platform restrictions |
| Share | Android share sheet |
| Notifications | Local notifications |
| Files | User-selected open/save only |
| Network | HTTPS to declared origins |
| Device info | Safe, non-identifying properties |
| Sensors | PROPOSED: accelerometer and orientation |
| Bluetooth | DEFERRED unless required |
| Contacts | DEFERRED |
| Calendar | DEFERRED |
| Background location | DEFERRED |
| Arbitrary shell/process access | FORBIDDEN |

### 13.4 Permission UX — PROPOSED

Before first launch, the viewer SHOULD present:

- application name;
- source or file location;
- requested capabilities;
- allowed network origins;
- whether the application is signed or unverified.

The runtime MUST prevent undeclared capability use.

---

## 14. Camera and media

### 14.1 Camera v1 — PROPOSED

Version 1 supports still capture through the Android camera flow:

```javascript
const image = await sausage.camera.capture({
  facing: "environment",
  quality: 0.85
});
```

The result is a runtime-managed file reference or blob-like object.

### 14.2 Live camera preview — OPEN

A live camera view embedded within SVG is significantly more complex because it introduces a second composited surface and lifecycle requirements.

Options:

- exclude live preview from v1;
- support a full-screen host camera screen;
- support a native rectangular camera surface anchored to SVG.

### 14.3 Audio — PROPOSED

Microphone support in v1 SHOULD use an explicit bounded recording action rather than unrestricted background capture.

---

## 15. Networking

### 15.1 Host-mediated HTTP — PROPOSED

Network requests SHOULD pass through the native host API rather than unrestricted browser networking.

Example:

```javascript
const response = await sausage.http.fetch({
  url: "https://api.example.com/weather",
  method: "GET",
  timeoutMs: 10000
});

const data = await response.json();
```

### 15.2 Policy — PROPOSED

- HTTPS is required by default.
- Origins must be declared in the manifest.
- Redirects to undeclared origins are blocked.
- Cookies are isolated per application.
- Browser navigation is disabled unless explicitly invoked through a safe host action.
- Cross-origin policy is defined by the Sausage profile rather than inherited accidentally from a local-file origin.

### 15.3 Secrets — DECIDED IN PRINCIPLE

Sausage application source is inspectable. Static API keys embedded in a document cannot be treated as secret.

The runtime MAY later provide secure user-owned credential storage, but it cannot make distributed application secrets safe.

---

## 16. Persistence

### 16.1 Key-value storage — PROPOSED

Every application receives isolated asynchronous key-value storage:

```javascript
await sausage.storage.set("temperatureUnit", "C");
const unit = await sausage.storage.get("temperatureUnit");
```

Supported values SHOULD include JSON-compatible data and binary blobs within quotas.

### 16.2 Local database — DECIDED

Version 1 MUST include an isolated SQLite-backed database API.

Possible API:

```javascript
await sausage.db.execute(
  "CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY, body TEXT)"
);

await sausage.db.execute(
  "INSERT INTO notes(body) VALUES (?)",
  ["Remember the milk"]
);

const rows = await sausage.db.query(
  "SELECT id, body FROM notes ORDER BY id DESC"
);
```

Each application receives a private database.

### 16.3 Database interface — DECIDED

Version 1 exposes direct parameterised SQLite through `sausage.db`.

- SQL statements are supplied as strings.
- Values MUST be supplied separately as parameters.
- Each application receives a private database.
- Multi-statement execution SHOULD be disabled by default.
- The runtime SHOULD provide transaction helpers.
- Schema migrations remain the application's responsibility.

### 16.4 Quotas — PROPOSED

The runtime SHOULD enforce per-application storage quotas and expose usage information.

---

## 17. Files and packaging

### 17.1 Version 1 file model — PROPOSED

The simplest application is a single `.svge` XML file.

Resources MAY be:

- embedded as data URIs;
- referenced using paths relative to the document;
- loaded from declared HTTPS origins.

### 17.2 Multi-file application roots — PROPOSED

A multi-file application has an application root containing its linked Sausage documents and resources.

The root MAY be:

- a directory already managed by Sausage;
- a directory selected by the user, with access granted to Sausage;
- a future packaged application.

Selecting or sharing one external `.svge` document does not necessarily grant access to sibling files. If that document requires relative sibling resources, Sausage SHOULD offer to let the user select its application directory or import it into managed storage.

### 17.3 Package format — DEFERRED / OPEN

A future packaged application could be a ZIP-based container containing:

```text
app.svg
assets/
modules/
manifest.*
```

A packaged application SHOULD use a distinct extension rather than making `.svge` mean both plain XML and ZIP content. The package extension remains open.

### 17.4 Ordinary SVG compatibility — PROPOSED

A `.svge` file SHOULD remain viewable as useful static artwork whenever practical.

Application authors SHOULD provide SVG fallback graphics for native controls.

---

## 18. Android viewer behaviour

### 18.1 Opening applications — PROPOSED

Sausage SHOULD support:

- Android file association for `.svge`;
- opening through the system file picker;
- opening a shared file from another application;
- opening a declared HTTPS URL;
- reopening recent applications.

### 18.2 Viewer chrome — PROPOSED

The default viewer SHOULD provide minimal host chrome:

- close/back;
- reload;
- application information;
- permissions;
- developer diagnostics;
- optional install/pin action.

Applications may request an immersive or full-screen presentation, subject to runtime policy.

### 18.3 Installed versus opened applications — OPEN

Possible modes:

1. **Document viewer only:** every application is opened as a document.
2. **Pin/install:** a document can be copied into managed storage and shown in a launcher inside Sausage.
3. **Shortcut:** Sausage can create an Android home-screen shortcut for a particular Sausage app.
4. **Standalone export:** future tooling wraps a Sausage app as its own APK.

Version 1 likely needs modes 1 and 2 or 3. Multi-file applications are supported through ordinary SVG links between Sausage documents.

### 18.4 Orientation — PROPOSED

Applications MAY declare portrait, landscape or adaptive orientation.

Adaptive SHOULD be the default.

---

## 19. Accessibility

### 19.1 Requirement — DECIDED IN PRINCIPLE

Accessibility is a core reason to use native controls.

### 19.2 Proposed rules

- Native controls MUST expose native Android accessibility semantics.
- Interactive SVG elements SHOULD declare standard roles and accessible names.
- `app:label` MAY supply a native accessible label.
- Focus order SHOULD follow document order unless explicitly overridden.
- Android font scaling SHOULD affect native controls.
- Applications SHOULD avoid conveying state by colour alone.
- The runtime SHOULD warn about interactive elements without accessible names.

---

## 20. Error handling and diagnostics

### 20.1 User mode — PROPOSED

A failed application SHOULD show:

- a concise error;
- the application name;
- a safe option to close or reload;
- no raw stack trace by default.

### 20.2 Developer mode — PROPOSED

Developer mode SHOULD provide:

- JavaScript console output;
- XML and manifest validation errors;
- unsupported feature warnings;
- permission denials;
- network request summaries;
- native-control bounding boxes;
- DOM/control synchronisation status;
- reload from file;
- optional live-reload endpoint later.

### 20.3 Strictness — OPEN

Decide whether unknown `app:` attributes and controls:

- fail validation;
- generate warnings and are ignored;
- depend on declared conformance mode.

A tolerant default with a strict developer mode is recommended.

---

## 21. Security model

### 21.1 Trust assumption — DECIDED IN PRINCIPLE

Sausage applications are executable documents and should ultimately be treated as untrusted code unless explicitly trusted.

### 21.2 Required protections — PROPOSED

The runtime MUST:

- isolate application storage;
- mediate all native capabilities;
- block undeclared permissions;
- restrict network access;
- prevent arbitrary WebView navigation;
- prevent access to runtime internals;
- prevent direct Java reflection or arbitrary bridge calls;
- avoid exposing stable device identifiers;
- clearly distinguish trusted and unverified applications.

### 21.3 Signing — DEFERRED / OPEN

A future release MAY support signed applications and publisher identities.

Signing is not required to prototype the runtime but affects distribution and update design.

### 21.4 Initial development priority — DECIDED

The initial runtime is intended for its developer as the only user. Signing, publisher identity and polished trust flows are therefore planning concerns rather than early implementation requirements.

The initial WebView bridge SHOULD nevertheless remain narrow: application scripts receive the `sausage` API and MUST NOT receive arbitrary Kotlin or Java objects. Maintaining this boundary early avoids coupling documents to unsafe implementation details.

---

## 22. Versioning and compatibility

### 22.1 Profile version — PROPOSED

The manifest declares a major profile version:

```xml
profile-version="1"
```

A runtime MUST reject unsupported major versions or open them in static SVG mode.

### 22.2 Feature detection — PROPOSED

Applications SHOULD be able to query:

```javascript
sausage.app.runtimeVersion
sausage.app.profileVersion
sausage.app.supports("camera.capture")
sausage.app.supports("control.date-picker")
```

### 22.3 Forward compatibility — PROPOSED

Unknown optional features SHOULD be ignored with warnings where safe.

Applications MAY declare required features. The runtime MUST refuse execution if a required feature is unavailable.

---

## 23. Conformance

A conforming v1 Sausage runtime MUST:

1. render standard SVG content;
2. expose the shared SVG DOM to application JavaScript;
3. recognise the v1 application namespace;
4. process the embedded manifest;
5. implement the v1 lifecycle;
6. implement required native controls;
7. implement permission mediation;
8. isolate application storage;
9. provide HTTP and local persistence;
10. provide deterministic error behaviour for unsupported features.

A conforming Sausage profile v1 document MUST:

1. be well-formed XML;
2. have an SVG root;
3. declare the application namespace when using Sausage application semantics;
4. declare required sensitive capabilities;
5. avoid relying on undeclared host access;
6. identify its required profile major version.

---

## 24. Incremental development slices toward v1

These slices are an implementation sequence, not separate profile versions. Together they build toward the complete v1 runtime. Each slice SHOULD produce something visible and testable in a reference application.

### Slice 1 — Graphical document viewer

- Open `.svge` files.
- Render SVG.
- Parse manifest.
- Inject the narrow `sausage` JavaScript object.
- Support SVG press events.
- Developer console.
- One active document.
- Demonstrate a polished static screen from a reference application.

### Slice 2 — Simple on-page interaction and animation

- Change existing text, attributes, classes and visibility.
- Run supported animation on existing SVG elements.
- Keep application structure static and declarative.
- Demonstrate an animated index-card interaction.

### Slice 3 — Pages and persistent progress

- Navigation between `.svge` documents and Android Back support.
- Isolated DOM and JavaScript contexts for each document.
- Lifecycle events.
- Key-value storage.
- Isolated SQLite database.
- Demonstrate saved progress across a learning journey or lucid-dreaming flow.

### Slice 4 — Native journal input

- Text field and text area.
- Overlay positioning and resize synchronisation.
- Keyboard and focus handling.
- Demonstrate creating and reopening a dream-journal entry.

### Slice 5 — Additional application behaviour

- Host-mediated HTTPS.
- Permissions UI.
- Checkbox, switch and slider.
- Select, date and time pickers.

### Slice 6 — Device capabilities

- Camera still capture.
- Photo picker.
- Current location.
- Clipboard, share and haptics.
- Local notifications.

### Slice 7 — Distribution polish

- Recent apps.
- Pin/install inside Sausage.
- Home-screen shortcuts.
- Better validation and diagnostics.
- Example applications and authoring guide.

---

## 25. Explicit non-goals for v1

- General HTML rendering as an application feature.
- Exposing WebView as a component.
- Arbitrary native Android APIs.
- Arbitrary background services.
- Full CSS styling of native widgets.
- Pixel-perfect interleaving of native controls and SVG layers.
- 3D rendering.
- Desktop and iOS runtimes.
- APK compilation.
- A visual application builder.
- A proprietary replacement for SVG.
- Compatibility with arbitrary browser web applications.

---

## 26. Principal open decisions

### Foundation

1. Is the hybrid model correct: namespaced attributes for visual semantics and namespaced elements only in metadata?
2. What permanent namespace URI should identify the Sausage application vocabulary?
3. Which SVG, CSS and JavaScript animation mechanisms form the supported animation baseline?
4. What viewport, safe-area, scrolling and keyboard conventions are needed for graphical mobile pages?

### Controls and rendering

5. Should native controls use platform-native styling or constrained Sausage theming?
6. Is a native button needed, or should buttons remain SVG?
7. Does `app:control` replace its SVG fallback visually, or does the SVG remain as styling beneath a native interaction layer?
8. Should v1 permit any rotation of native controls?

### Capabilities

9. Does camera v1 require only still capture, or an embedded live preview?
10. Are microphone, sensors and notifications mandatory for v1?
11. Which capabilities may run while the app is not visible?

### Distribution and trust

12. Should users be able to pin/install a document inside Sausage?
13. Should Sausage create home-screen shortcuts?
14. When should a multi-file app be opened from an approved directory and when should it be imported into managed storage?
15. What warning or approval flow should apply to unsigned apps?
16. Is remote URL loading part of v1 or should files be local only?

---

## 27. Recommended first decisions

The following decisions most strongly affect the next implementation slices:

1. **Animation baseline:** which WebView-supported SVG and CSS animations generated applications may rely on.
2. **Native control representation:** exactly how an annotated SVG anchor and its native control divide visual and interactive responsibility.
3. **Page viewport behaviour:** sizing, safe areas, scrolling, orientation and keyboard resizing.
4. **First reference journey:** the smallest polished lucid-dreaming or animated learning-card flow that exercises the runtime.
5. **Application root UX:** when Sausage asks for a directory and when it imports files into managed storage.

---

## 28. Working summary

Sausage should initially be a simple Android runtime for AI-generated graphical journeys with this shape:

- one readable SVG-based application file, or several linked `.svge` files within one application root;
- standard SVG for almost all visuals;
- a small `app:` namespace for application semantics;
- WebView as the initial SVG renderer, DOM and JavaScript engine;
- simple JavaScript acting directly on existing elements in the SVG DOM;
- static, declarative screens and controls rather than script-generated application structure;
- isolated document contexts, with persistent state shared through key-value storage or SQLite;
- Android controls overlaid only where native behaviour is valuable;
- narrow promise-based APIs for HTTP, SQLite and device capabilities;
- declared permissions and network origins;
- useful static fallback in ordinary SVG viewers;
- no exposed browser or unrestricted Android bridge.

The initial product is for a single developer-user. Stronger identity, signing and trust UX remain part of the architectural plan but are not prerequisites for the first development slices.

This preserves the central promise:

> A person without development experience can use AI to create and immediately run a beautiful graphical mobile experience as an SVG document.

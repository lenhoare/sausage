# Creating a Sausage app

Create one self-contained UTF-8 `.svge` file. A Sausage app is ordinary SVG plus a small declarative application vocabulary. It should be visually polished, content-led and simple enough for a non-developer to generate with AI.

When asked to create an app, output the complete `.svge` file and little or no explanation.

## Rules

- Use valid XML SVG. Include both namespaces shown below, a `viewBox`, and no `DOCTYPE`.
- Put application declarations inside `<metadata>`. Put visible artwork in ordinary SVG groups.
- Every `app:screen` needs at least one `app:graphic`. Slices appear vertically in declaration order; the first screen opens first.
- A graphic `ref` must name an existing SVG element ID and may be used only once in the flow.
- Use Sausage controls for input. Do not create HTML or imitate text fields in SVG.
- Scripts may update existing SVG text, attributes, classes and animations. Do not dynamically create screens or controls.
- Button actions name global functions: assign them as `window.actionName = ...`. An action may be async and may return a short status string.
- Put JavaScript inside CDATA. Wait for `sausage-ready` before reading restored control state.
- Keep resources embedded. Do not rely on external images, fonts, libraries or network access.
- Do not invent elements or APIs not listed here. Prefer a single file; multi-file navigation is currently for bundled apps only.
- Design for touch, readable text and restrained animation. SVG text does not wrap automatically; use separate `<text>`/`<tspan>` lines.

## Document shape

```xml
<svg xmlns="http://www.w3.org/2000/svg"
     xmlns:app="https://sausage.dev/ns/app/1"
     viewBox="0 0 390 360"
     width="100%" height="100%"
     preserveAspectRatio="xMidYMid meet">
  <metadata>
    <app:manifest id="dev.example.my-app"
                  name="My App"
                  version="0.0.1"
                  profile-version="1" />
    <app:screen id="home">
      <app:graphic ref="home-art" />
      <!-- controls go here in vertical order -->
    </app:screen>
  </metadata>

  <g id="home-art"><!-- SVG artwork --></g>
  <script type="application/ecmascript"><![CDATA[
    // simple interaction only
  ]]></script>
</svg>
```

IDs and keys should use letters, numbers, `.`, `_` or `-`. Application IDs should be stable and unique because they scope saved data.

## Available slices

```xml
<app:graphic ref="existing-svg-id" />
<app:text-area key="note" label="Your note" hint="Optional help" placeholder="Optional prompt" />
<app:choice key="mood" label="How do you feel?" options="Calm,Curious,Energised" />
<app:switch key="reminders" label="Remind me" />
<app:slider key="energy" label="Energy" min="0" max="10" step="1" value="5" />
<app:photo key="photo" label="Choose a photo" hint="Optional help" target="existing-image-id" />
<app:button label="Save" action="save" />
<app:button label="Continue" target-screen="next" />
```

- Keys are unique across the document and ordinary controls persist automatically.
- `choice` has 2–8 unique comma-separated options and returns a string or `null`.
- `switch` returns a Boolean; `slider` returns a number.
- A photo target must be an existing SVG `<image id="existing-image-id">`. Photo metadata is `{ name, type, size }` or `null` and is session-only.
- A button declares exactly one of `action` or `target-screen`.

## Script API

```javascript
sausage.controls.getValue("note");
sausage.controls.setValue("note", "Hello");
const unsubscribe = sausage.controls.onChange("mood", value => updateArt(value));

await sausage.storage.set("key", { any: "JSON value" });
const value = await sausage.storage.get("key");
await sausage.storage.remove("key");

await sausage.db.execute(
  "INSERT INTO notes(body) VALUES (?)",
  ["Remember this"]
);
const rows = await sausage.db.query(
  "SELECT body FROM notes ORDER BY rowid DESC",
  []
);
```

Database calls accept one parameterised statement without comments or a trailing semicolon. Never concatenate user input into SQL.

Device features must be declared as direct children of the manifest:

```xml
<app:manifest id="dev.example.beacon" name="Beacon" version="0.0.1" profile-version="1">
  <app:permission name="location" reason="Place the beacon at your position" />
  <app:permission name="notifications" reason="Send local reminders" />
</app:manifest>
```

```javascript
const position = await sausage.location.current({ accuracy: "balanced" });
// { latitude, longitude, accuracy, timestamp, cached }

await sausage.notifications.show({ title: "Beacon", body: "Ready." });
await sausage.notifications.schedule({
  id: "evening-reminder",
  title: "Beacon",
  body: "Pause for a moment.",
  at: Date.now() + 60 * 60 * 1000
});
await sausage.notifications.cancel("evening-reminder");
```

Location is foreground-only. Use `balanced` unless precision is genuinely needed. Scheduled notifications are one-off and inexact, so Android may deliver them late.

## Small complete example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg"
     xmlns:app="https://sausage.dev/ns/app/1"
     viewBox="0 0 390 330" width="100%" height="100%"
     preserveAspectRatio="xMidYMid meet">
  <metadata>
    <app:manifest id="dev.example.one-thought" name="One Thought"
                  version="0.0.1" profile-version="1" />
    <app:screen id="home">
      <app:graphic ref="thought-art" />
      <app:text-area key="thought" label="What is on your mind?"
                     placeholder="One sentence is enough..." />
      <app:button label="Keep this thought" action="keepThought" />
    </app:screen>
  </metadata>

  <defs>
    <linearGradient id="sky" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#071528" />
      <stop offset="1" stop-color="#244763" />
    </linearGradient>
  </defs>
  <style>
    text { font-family: Roboto, Arial, sans-serif; }
    #glow { transition: opacity 400ms ease; }
    #thought-art.saved #glow { opacity: .8; }
    @media (prefers-reduced-motion: reduce) { #glow { transition: none; } }
  </style>

  <g id="thought-art" aria-label="A quiet night sky">
    <rect width="390" height="330" fill="url(#sky)" />
    <circle id="glow" cx="195" cy="132" r="72" fill="#f6bf76" opacity=".18" />
    <circle cx="195" cy="132" r="34" fill="#f8dba8" />
    <text x="195" y="240" fill="#fff8ed" font-size="25" font-weight="700"
          text-anchor="middle">Hold one thought.</text>
    <text id="status" x="195" y="272" fill="#a9bfd6" font-size="12"
          text-anchor="middle">It will be here when you return.</text>
  </g>

  <script type="application/ecmascript"><![CDATA[
    (() => {
      window.keepThought = async () => {
        const thought = sausage.controls.getValue('thought').trim();
        if (!thought) throw new Error('Write a thought first.');
        await sausage.storage.set('latest-thought', thought);
        document.getElementById('thought-art').classList.add('saved');
        document.getElementById('status').textContent = 'Your thought is safe.';
        return 'Thought saved';
      };

      window.addEventListener('sausage-ready', async () => {
        const saved = await sausage.storage.get('latest-thought');
        if (typeof saved === 'string') sausage.controls.setValue('thought', saved);
      }, { once: true });
    })();
  ]]></script>
</svg>
```

Before returning a file, check that it is well-formed XML, every referenced ID exists, all keys are unique, action functions are global, capability declarations match API use, and every screen contains a graphic.

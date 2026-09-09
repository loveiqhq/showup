# Android working rules

Applies to everything under `android-preview-project/`. The shared rules in `../CLAUDE.md` apply too
— this file adds only what is genuinely Android-specific and overrides nothing.

Every rule has its reason next to it. A rule with no reason is one nobody can safely change later.

---

## The three rules that come first

**Before creating a new implementation, first search the repository for an existing shared
component, token, model, service, or pattern. Do not create a duplicate implementation without
explaining why.**

**Generated code compiling is not proof that the feature is complete.**

**Do not report a platform as verified unless it was actually built and tested in that platform's
toolchain.**

The first is not abstract here. The primary button existed **three times** in this project —
`PillButton` in `welcome`, `SunsetButton` in `tutorial`, and `ConnectAccountScreen` importing
both — until 7 September 2026, when they became `designsystem/PrimaryButton.kt`.

What that cost is the part worth remembering: `SunsetButton` was written six days before
`PillButton` existed and then never revisited, so it kept a two-stop gradient where the spec
wants three, no violet shadow though iOS had one, no press feedback at all, and a Material glyph
for its arrow. The 23 August audit found and fixed every one of those defects on the other
tutorial cards and missed these, because they lived in a file no verifier read.

A duplicate does not stay a duplicate. It becomes a worse copy that nobody is looking at.

---

**Portrait only.** `android:screenOrientation="portrait"` on `MainActivity` — `portrait`, not
`sensorPortrait`, which would also allow upside-down. Targeting SDK 36 means Android 16 ignores
this on displays 600dp and wider, so `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` is set as
the documented, temporary opt-out. Verify on a 600dp+ emulator; no test here can see it.

## Language and target

- **Kotlin and Jetpack Compose.** No XML layouts, no Views, no `findViewById`.
- **`minSdk = 30`** (Android 11), `targetSdk = 36`, `compileSdk = 36`.
- API 30 is a deliberate choice to keep the app installable on the long tail of active devices.
  **Nothing in this project may require an API above 30 without a `Build.VERSION` gate** or an
  AndroidX compatibility wrapper.
- `targetSdk = 36` is required by Play policy and is unrelated to the minimum.

## State ownership

**A composable holds no state.** State is hoisted to one owner per flow — `SignUpFlow` — and screens
take values and lambdas. This is the rule; MVVM is the name people give it.

- **`mutableStateOf` inside a composable is fine only for something local and disposable.** If a
  parent needs to read it, hoist it.
- **`remember` vs `rememberSaveable`:** `remember` survives recomposition. **`rememberSaveable`
  survives configuration change and process death.** Anything the user typed, chose or scrolled to
  uses `rememberSaveable` — losing a half-typed phone number to a rotation is a real bug that no test
  here currently catches. There are 16 in this project; that is the standard, not the exception.
- **A custom `Saver` is required** for any type Compose cannot save by default. See
  `TutorialRouting`.
- **No mutable state hidden in a random composable.** If a composable owns state that affects
  anything outside itself, it is in the wrong place.
- **ViewModels** are not used yet, deliberately — no screen owns asynchronous work. Introduce one
  the moment a screen loads, uploads or retries; then it exposes `StateFlow` and the screen collects
  it **lifecycle-aware** with `collectAsStateWithLifecycle()`, never bare `collectAsState()`, which
  keeps collecting while the app is backgrounded.
- **`derivedStateOf` for values computed from other state**, so recomposition does not run on every
  keystroke. None used yet; it becomes relevant with lists and filters.
- **No business logic inside a composable.** Validation and formatting go in plain functions — see
  `CountryCodes.kt` and `TutorialRouting.kt`, both unit-tested with no UI at all.

## Coroutines

- **`LaunchedEffect` keyed on what should restart it**, never `Unit` when the work depends on a
  value — that is how an effect silently stops re-running.
- **`rememberCoroutineScope` for event-driven work**, `LaunchedEffect` for composition-driven work.
- **Never launch from a composable body.** Only from an effect or a callback.
- **Structured concurrency:** work launched in a screen's scope must be cancelled with it. Do not use
  `GlobalScope`.
- **`withContext(Dispatchers.IO)` for real I/O**, and nothing else — Compose already runs on main.

## Layout

- **No absolute positioning.** `Row`, `Column`, `Box` and constraints only.
- **No fixed width on anything containing text**, and no width derived from the screen.
- **Never design for one device.** Every screen goes into `ScreenFitTest`, which measures 17 sizes
  from 320×686 to 440×956. `320 × 686` is the Galaxy Fold cover screen and is the hardest case —
  narrower than any iPhone.
- **48dp minimum for anything tappable** (44pt is the iOS floor; Material says 48). This project
  shipped an input measuring 23dp inside a 56dp row: it looked right and only its middle third
  responded, on 15 of 18 sizes. Invisible in source and in a screenshot — only measurement finds it.
- **Modifier order changes behaviour.** `.height(36.dp).padding(top = 10.dp)` and
  `.padding(top = 10.dp).height(36.dp)` produce different layouts. A reserved region that includes
  its own padding is a real bug we have had.
- **Reserve space for text that can wrap.** A region sized for one line will not grow into a fixed
  spacer; this shipped as a CTA that dropped 15.7dp when a two-line message appeared.
- **Respect insets.** Use `WindowInsets`; do not hardcode a status-bar height.

## Design tokens and components

- **One definition per value**, in `DesignSystem.kt`. **No `Color(0x…)` in a screen** — with one
  exception: third-party brand colours (Google, Meta) whose values their sign-in branding rules
  mandate. Those carry the rule that mandates them, in a comment.
- **No arbitrary `dp`, radius, icon size or duration at a call site.** Use the scale.
- **No `sp` size in a screen.** Use the named type roles.
- **Use the shared primitives** in `com.showup.designsystem`. The three-way button split happened
  because `welcome` and `tutorial` were treated as separate worlds. They are not.
- **A primary button is `PrimaryButton`.** Six variants, and `leading` / `trailing` slots for a
  mark or an arrow. If you need one it does not do, add a variant to it — do not write a second
  button. `NextButton` is not a counter-example: it is a label beside a circular arrow badge,
  a different silhouette with its own spec sheet.
- **A shared primitive never lives in a screen file.** That is not tidiness. `PillButton` sat in
  `WelcomeShell.kt`, and whoever wrote the tutorial's CTA had no reason to open the sign-up
  flow's shell, so they wrote their own. Anything two flows use belongs in `designsystem`.
- **A status badge is `StatusBadge`.** Two tones, `BadgeTone.Orange` and `.Lavender`. It does NOT
  align itself: pass `Modifier.align(...)` from the parent. That is the whole reason there were
  three of them — two were `ColumnScope` extensions that forced `Start`, so Connect, whose badge is
  centred, could not use either and wrote a third.
- **A component's tone is per screen, and both tones stay.** The eyebrow pill is orange on the phone
  screens and lavender on the tutorial cards; changing the shared token to fix one breaks the other.
- **Check the handoff for which variant a screen uses.** The back control was drawn `arrow-left`
  where the design says `chevron-left`. No test catches that; only reading the reference does.
- **`Modifier.shadow` uses elevation with a window-relative light source**, so its direction depends
  on where the element sits on screen. For a glow that must be symmetric, draw it — do not tune the
  elevation until it looks right on one device.

## Networking

- **No hand-written API models, endpoint paths, request bodies or response types** once the OpenAPI
  client is generated. The backend contract generates them.
- **Never edit generated files.** They live in `build/generated/openapi` and are overwritten.
- **Auth is attached by an OkHttp interceptor, never at a call site.**
- **No base URL literal anywhere.** It comes from `buildConfigField` per build type.

## Navigation

Routing today is a `Step` enum in `SignUpFlow` and a `FlowScreen` enum in `MainActivity` --
deliberate, typed, and testable for one linear flow. Do not add a second hand-rolled router.

**Adopt Navigation Compose at the first of these, and not before:** a deep link, a screen reachable
from two places, or the profile/discovery flows. Re-audited 8 September 2026 and confirmed as the
right call for now -- see `docs/mobile-client-architecture-spike.md` 1.5.1.

**When it triggers, migrate the SHELL only first** -- `MainActivity`'s `when(screen)` becomes a
`NavHost`; `SignUpFlow` stays one destination keeping its own `Step`. A full per-screen migration
needs a requirement that clearly demands it, because of the rule below.

**A screen takes values and returns pixels. It never receives a `NavController`.** That single
property is what makes `ScreenFitTest` at 17 sizes and 74 previews possible; a nav-graph migration
erodes it by default rather than by decision.

## Screen requirements

- **Every screen has loading, empty, error and offline states.**
- **IME handling:** the keyboard must not hide any required control. Use
  `Modifier.imePadding()` / `windowInsetsPadding(WindowInsets.ime)` rather than a fixed bottom
  spacer. **Not yet automated — check by hand**, on the smallest screen, where it bites.
- **Accessibility:** `contentDescription` on every meaningful icon and `null` on decorative ones;
  usable at the largest font scale; contrast at WCAG AA.
- **`@Preview` for every state**, and at the smallest and largest widths, not just the default.
- **Logging:** no `println`, no `Log.d` left in. Nothing that could log a phone number, a code, a
  token or an email.

## Tests

- Logic goes in plain functions and is unit-tested with no UI.
- Every screen is in `ScreenFitTest`.
- **Robolectric needs `@GraphicsMode(NATIVE)`** for anything measuring text. Without it, font metrics
  are stubbed and every measurement is wrong in a way that looks plausible.
- **R8 runs only on release.** CI builds `assembleRelease` as well as `assembleDebug`, because R8
  removes what it cannot see used — reflection-based libraries need keep rules (`proguard-rules.pro`).

## Verification

```
cd android-preview-project && ./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
cd .. && for f in audit/verify-*.py audit/check-*.py; do python "$f"; done
```

**Say what was actually verified.** "Builds and tests pass" and "I have seen it render" are different
claims. The wrong icon and the wrong eyebrow tone passed every automated check here, because both
were real components from the design system, correctly drawn, in the wrong place.

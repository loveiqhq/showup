# Mobile Client Architecture Spike

3 September 2026 · ShowUp · tied to the files in this repository

**Scope.** Architecture and research spike. Nothing in the product was refactored, no visual design
was changed and no product behaviour was altered to produce it. The only files written are this
document and the three `CLAUDE.md` files it describes.

**Evidence ratings** used throughout, as requested:

| | |
|---|---|
| **CONFIRMED** | observed in our code, or observed failing when built and run |
| **LIKELY** | strongly indicated by our code, not yet observed failing |
| **POSSIBLE** | plausible for this codebase, not indicated either way |
| **N/A** | asked about, does not apply to us, and why |

---

# Executive summary

## Top 5 risks

1. **Visual and behavioural defects that only appear when rendered.** Six shipped this month. All
   were real design-system components, correctly built, used incorrectly. CONFIRMED.
2. **API contract drift**, once networking exists. Nothing is written yet, so the whole client API
   surface is still a decision rather than a liability. LIKELY the moment it is hand-written.
3. **Navigation has no framework on either platform** — both flows route through a hand-rolled enum.
   Fine for five screens, does not survive deep links, back-stack restoration or process death.
   CONFIRMED as present, LIKELY to hurt as flows grow.
4. **Component duplication.** The primary button exists three times on Android; one screen imports
   two of them. Two of this month's defects were caused by it. CONFIRMED.
5. **Claude reporting work as verified when it was only inspected.** Eight Swift tests sat in no
   target for a week and were counted as passing. CONFIRMED, now structurally prevented.

## Top 5 recommended changes

1. **Emit `openapi.json` from the backend and generate both clients from it** — before any
   networking code is written by hand. The cheapest it will ever be is now, at zero lines.
2. **Extract the five primitives**, starting with the button, and build them against the smallest
   and largest screens before any more screens are generated.
3. **Design tokens for spacing, type and illustration colour**, with a checker that enforces them
   and an explicit allowlist for third-party brand colours.
4. **Adopt a navigation framework** — `NavigationStack` and Navigation Compose — before the flow
   count doubles.
5. **A required response format for Claude** that separates VERIFIED from UNVERIFIED per platform.

## What must be implemented before generating many more screens

**P0, in order: the OpenAPI pipeline, the design tokens, and the five primitives.** Every screen
generated before these exist is a screen that will be migrated onto them later, by hand. Five
screens exist today. That is the cheapest this migration will ever be.

Everything else in this document can follow screen work rather than block it.

---

# Inventory — what is actually here

Requested before recommendations, and it changes several answers.

| | Location | State |
|---|---|---|
| iOS app | `mobile/welcome-screen/ios-app/` | 22 Swift files, 5 screens, builds and tests in CI on `macos-15` |
| Android app | `mobile/welcome-screen/android-preview-project/` | 2 packages (`welcome`, `tutorial`), builds and tests in CI |
| Backend | `src/` | NestJS, 52 routes, Swagger configured |
| Design tokens | `DesignSystem.kt` / `.swift` | colour, type, gradients — no spacing, no radius, no icon-size scale |
| Networking | — | **none on either client** |
| Token storage | — | **none on either client** |
| Generated API clients | — | none |
| `openapi.json` artifact | — | **never written to disk** |
| Navigation framework | — | **none**; both platforms route through an enum in `SignUpFlow` |
| Lint (SwiftLint / detekt / ktlint) | — | **none configured** |
| Conformance checkers | `mobile/welcome-screen/audit/` | 9 scripts, 868 assertions + 77 call sites + a 17-API list |
| Tests | | 45 Android (Robolectric + Compose UI), 32 Swift (XCTest) |
| Screenshot / golden tests | — | none |
| CI | `.github/workflows/ci.yml` | 3 jobs: backend, Android, iOS |
| `CLAUDE.md` | `mobile/welcome-screen/CLAUDE.md` | one, shared |

**The two entries that most change the answers:** there is **no client networking of any kind**, so
Part 2 is a greenfield proposal rather than a migration; and there is **no navigation framework**,
which the brief asks about and which no previous document mentioned.

---

# Part 1 — Root cause analysis, iOS UI failures

iOS was built and run on a Mac for the first time on 1 September. Ten build errors, then four
behavioural bugs. Everything below is from that run or from the conformance harness, not from
reading.

## 1.1 · State ownership

**Measured across 22 Swift files:** `@State` 21 · `@Binding` 3 · `@StateObject` / `@ObservedObject`
/ `@Observable` **0**.

The brief's hypothesis is a missing `@Observable` or `@StateObject`. **NOT APPLICABLE as a cause** —
there are no view models by deliberate choice, state is hoisted to one owner per flow (`SignUpFlow`
holds ten of the twenty-one), and the two real state bugs were somewhere else entirely.

| Asked about | Rating | Finding |
|---|---|---|
| Inappropriate local `@State` | N/A | Screens take values and return a picture. `StartupView` and `WelcomeBackView` hold no state at all |
| Parent/child duplication | N/A | Not present; one owner per flow |
| Missing or incorrect `@Binding` | **CONFIRMED** | See 1.2 — the bug was a `Binding` whose *getter* rewrote text |
| Incorrect `@StateObject` / `@ObservedObject` | N/A | None exist |
| `@Observable` issues | N/A | Not adopted yet. Earns its place when a screen owns async work; none of the five do |
| State recreated unexpectedly | LIKELY (contained) | 16 `rememberSaveable` on Android vs no iOS equivalent — see 1.6 |
| State not propagating through navigation | POSSIBLE | There is no navigation framework to propagate through — see 1.5 |
| Derived state stored, not computed | N/A | Not observed |

## 1.2 · The two confirmed state bugs — both at the UIKit boundary

**CONFIRMED · `PhoneVerificationView.swift` · fixed.** Two defects, one cause.

The phone field was bound through a `Binding` whose getter reformatted the text. While a text field
is first responder, **UIKit owns its contents** and SwiftUI will not reliably push a getter's rewrite
back into it. Result: grouping never appeared at any typing speed; and when the rewrite *did* land it
clobbered keystrokes still in flight — **ten digits typed, five arrived.**

Fix: reformat in `onChange`, never in the binding's getter. Verified at human typing speed, with
backspace, mid-string insertion, and changing country with digits already entered.

**Why SwiftUI behaves this way:** a `Binding` getter is called during view evaluation, which is not
synchronised with UIKit's editing session. The architecture was not at fault — this is the seam
between two ownership models, and it is the single most dangerous place in a SwiftUI codebase.

## 1.3 · The CTA moved — the most instructive bug here

**CONFIRMED · `PhoneVerificationView.swift`, states C/D · fixed.** The error card was wrapped in a
bare `if mismatch { … }`.

In a SwiftUI `ViewBuilder`, an `if` with no `else` produces **nil**, and a frame and padding applied
around nil both collapse to zero. The region meant to reserve 42 points reserved nothing, so the
button jumped the full **56 points** when an error appeared — which SHOWUP-143 forbids explicitly.

Fix: always render the card, hide it with `opacity`. Measured at 0.0pt movement.

**Android did not have this bug.** A Compose `Box` lays out at its minimum height whether or not its
content is emitted. **Identical logic error, shipped bug on one platform, invisible on the other** —
the clearest single argument in this document for building both.

**Android had the sibling bug**, states A/B: one line reserved for a message that always takes two,
so the CTA dropped 15.7dp. A code comment claimed the message would "grow into the spacer"; the
spacer is a fixed 22dp. CONFIRMED, fixed.

## 1.4 · Touch and layout

**Measured:** `Button(...)` 21 · `.onTapGesture` 1 · `.contentShape(...)` 7 · `.frame(width:)` 41 ·
`ZStack` 20 · `.overlay(...)` 15 · `GeometryReader` 7.

| Asked about | Rating | Finding |
|---|---|---|
| Overlays intercepting touches | **N/A** | 15 overlays, all decorative; the classic gesture-without-`contentShape` bug is not present |
| Incorrect hit areas | **CONFIRMED (Android)** | Phone input measured **23dp inside a 56dp row**; it looked 56dp tall while only its middle third accepted a tap, on 15 of 18 sizes. Invisible in source and in a screenshot — found by measurement. Fixed; `ScreenFitTest` now asserts 44pt everywhere |
| Frame sizing | N/A | All 41 fixed frames are icons, avatars, badges. **No text container or layout region is pinned to a width** |
| `zIndex` problems | N/A | Not used |
| Gesture conflicts | N/A | One `.onTapGesture` in the codebase |
| Disabled state propagating | POSSIBLE | Not audited; add to the primitive specs in Part 3 |
| Transparent layers receiving touches | N/A | Not observed |
| `GeometryReader` misuse | **LIKELY — needs review** | 7 uses, all reading the parent's size for backdrop art and layout maths, which is legitimate. But `GeometryReader` fills its parent and returns a top-leading-aligned child, which silently changes layout when it wraps content rather than a background. **Flagged for review, not asserted as a bug** |
| Hardcoded widths/heights | See above | |
| Safe-area handling | **CONFIRMED CORRECT** | Insets are modelled per device in the fit harness; "fits" means "fits where the user can see it" |
| Keyboard overlap | **NOT MEASURED** | Real gap. See Part 6 |
| Text clipping | **CONFIRMED, caught by CI** | A message fit on Windows and was cut off on the Linux runner — same commit, same sizes |
| Dynamic type | **NOT MEASURED** | Real gap. See Part 6 |
| Single-screen-size assumptions | N/A | 17 sizes measured every run |
| Absolute positioning | N/A | Forbidden in `CLAUDE.md`; flex/stack only |

## 1.5 · Navigation and lifecycle — the gap nobody had flagged

**CONFIRMED: there is no navigation framework on either platform.** `NavigationStack` 0,
`NavigationLink` 0, `NavHost` 0. Both apps route by mutating a `Step` enum inside `SignUpFlow` and
switching on it.

**This is defensible today** — five linear screens, one flow, no deep links — and it is why the flow
is testable as a pure function. It is not a bug and nothing is broken.

**It stops being defensible** at: deep links, a back stack the OS restores, process death, or any
screen reachable from two places. Adopting `NavigationStack` and Navigation Compose later means
rewriting the routing of every screen that exists at that point. **Five screens is the cheapest this
will ever be** — hence P1 in Part 11.

| Asked about | Rating | Finding |
|---|---|---|
| `NavigationStack` / path ownership | N/A → **risk** | No stack exists to own a path |
| Views rebuilding unexpectedly | N/A | Not observed |
| Sheets losing state | POSSIBLE | One `.sheet(` (the country picker). Its state is hoisted, so it should survive; not explicitly tested |
| Navigation tied to temporary local state | **CONFIRMED, by design** | The `Step` enum *is* local state. Deliberate, documented, and the thing a framework would replace |

## 1.6 · Concurrency and the UI thread

**Measured:** `@MainActor` 15 · `Task {` 2 · strict concurrency `complete`, language mode 5.

| Asked about | Rating | Finding |
|---|---|---|
| UI mutation outside `MainActor` | N/A | Views are `@MainActor`; the phone-metadata singleton is explicitly `@MainActor` |
| Async callbacks mutating state incorrectly | N/A | Two `Task` blocks, both UI-local animation timing |
| Unsafe `Task` usage / missing cancellation | POSSIBLE | Both current uses are short and self-cancelling. Will matter the moment networking lands |
| Race conditions | N/A observed | |
| Strict concurrency | **CONFIRMED — one real find** | The first Mac build produced a `[String: Any]` that strict concurrency requires to be `[String: any Sendable]`. **No static checker here could have caught that** — it needs the compiler |

`check-swift-concurrency.py` bans the three escape hatches (`@unchecked Sendable`,
`nonisolated(unsafe)`, `@preconcurrency`), so warnings cannot be silenced instead of fixed.

## 1.7 · Android Compose, for comparison

**Measured:** `mutableStateOf` 21 · `rememberSaveable` 16 · `remember {` 12 · `LaunchedEffect` 26 ·
`ViewModel` 0 · `StateFlow` 0 · `collectAsState` 0 · `derivedStateOf` 0.

State hoisting is done correctly: screens take values and lambdas, `SignUpFlow` owns the state.
**Sixteen `rememberSaveable`** means Android already survives configuration change and process death
in those places — **iOS has no equivalent and has not been tested for it.** That asymmetry is
undocumented and belongs in the Definition of Done.

**Did Android only look healthier because it was compiled earlier?** Substantially yes, and that is
the honest answer:

1. **Android was compiled and iOS was not.** Four of the six iOS defects were iOS-only for that
   reason alone. **This asymmetry ended on 1 September** — it explains the backlog and predicts
   nothing about the future.
2. **Compose enforces what SwiftUI permits.** A composable holding state without `remember` loses it
   visibly on the next redraw. SwiftUI lets a view hold anything and misbehaves subtly.
3. **Compose has no UIKit boundary.** Both iOS state bugs were at that seam. Android has no
   equivalent seam.

Two of the three are about the environment, not the language.

## 1.8 · Comparison table

| Concern | iOS today | Android today | Risk | Recommended standard |
|---|---|---|---|---|
| State ownership | `@State` hoisted to `SignUpFlow` | hoisted, `mutableStateOf` + `rememberSaveable` | Low | Keep hoisting. `@Observable` / ViewModel only when a screen owns async work |
| Process death / restore | **nothing** | 16 `rememberSaveable` | **Medium** | `@SceneStorage` or explicit restore on iOS; add to DoD |
| Navigation | enum in `SignUpFlow` | enum in `SignUpFlow` | **Medium, rising** | `NavigationStack` + Navigation Compose before flow count doubles |
| Text input | fixed; format in `onChange` | correct from the start | Low | Never reformat inside a `Binding` getter |
| Reserved regions | was nil-collapsing | `Box` reserves min height | Low now | Always render + `opacity`; assert with a moved-CTA test |
| Touch targets | no violations found | was 23dp, fixed | Low | 44pt asserted on 17 sizes every run |
| Concurrency | strict, `@MainActor`, checker | Kotlin coroutines, unused so far | Low | Keep the checker; revisit when networking lands |
| Networking | **none** | **none** | — | Generated clients, Part 2 |
| Design tokens | partial | partial | **Medium** | Part 3 |
| Primitives | 2 buttons + inline | **3 buttons** + inline | **Medium** | Part 3 |

---

# Part 2 — Automated API and model generation

**Nothing exists yet.** No `URLSession`, no `Keychain`, no Retrofit, no OkHttp, no `DataStore`, no
client models. This is the ideal moment: the cost of adopting generation is zero lines of
hand-written client code thrown away, and it only ever rises.

## 2.1 · How the backend produces OpenAPI today

`SwaggerModule.createDocument` runs at startup to serve the interactive docs (`src/main.ts:60`). The
document is **never written to a file**, so no build tool can consume it. That is the first blocker.

## 2.2 · Where `openapi.json` should live

The brief asks which of five options. **Recommended: generated in backend CI, committed to the
repository, and verified in CI.**

| Option | Verdict |
|---|---|
| Generated during backend CI | **Yes** — the spec must come from the code, never be edited |
| Committed to the repo | **Yes** — one repo holds backend and both apps, so it is one file, and a client build must never depend on a running server |
| Downloaded during mobile builds | No — makes builds need network and a live backend; breaks offline and reproducibility |
| Published as an artifact | Not yet — the indirection buys nothing while everything is in one repository |

**With a CI step that fails if the committed spec differs from a freshly generated one.** Without
that, the spec silently drifts from the code and becomes actively harmful, because it is trusted.

```ts
// scripts/emit-openapi.ts
const app = await NestFactory.create(AppModule, { logger: false });
writeFileSync('openapi.json', JSON.stringify(SwaggerModule.createDocument(app, swaggerConfig), null, 2));
await app.close();
```

## 2.3 · iOS setup — apple/swift-openapi-generator

Apple's own generator, run as a **build plugin**, so generation happens at build time.

**Packages:** `swift-openapi-generator`, `swift-openapi-runtime`, `swift-openapi-urlsession`.

**Configuration** — two files beside the target, `openapi.json` and:

```yaml
# openapi-generator-config.yaml
generate: [types, client]
accessModifier: internal
```

| | |
|---|---|
| Generated location | `.build/plugins/outputs/…` — build directory, never the source tree |
| Transport | `URLSessionTransport` |
| How generation runs | Automatically, as part of every build. No script to remember |
| Git treatment | **Nothing generated is committed.** `openapi.json` is the only committed input |
| Preventing edits | Structural — the output is inside the build directory, so an edit is erased by the next build. Nothing to police |

That last row is the real argument for the plugin over a CLI: with a CLI you commit generated code
and then need a rule that nobody edits it. With the plugin there is nothing to edit.

## 2.4 · Android setup — OpenAPI Generator Gradle plugin

```kotlin
plugins { id("org.openapi.generator") version "7.x" }

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/../../../openapi.json")
    outputDir.set("${layout.buildDirectory.get()}/generated/openapi")
    apiPackage.set("com.showup.api")
    modelPackage.set("com.showup.api.model")
    library.set("jvm-retrofit2")
    configOptions.set(mapOf(
        "serializationLibrary" to "kotlinx_serialization",
        "dateLibrary" to "kotlinx-datetime",
    ))
}
tasks.named("preBuild") { dependsOn("openApiGenerate") }
```

Same properties: output in `build/`, never committed, regenerated when the spec changes, nothing to
hand-edit. **`kotlinx_serialization`** rather than Moshi or Gson because the project is already
Kotlin-first and it needs no reflection, which keeps R8 configuration simple.

## 2.5 · Authentication

**Refresh tokens exist** — `POST /auth/refresh` and `POST /auth/logout` are real routes in
`src/modules/auth/auth.controller.ts`, alongside `phone/start`, `phone/verify`, `apple`, `google`,
`email/start`, `email/verify`. This section describes those, and invents nothing.

**Auth must never appear inside a generated endpoint method.** One interceptor per platform:

```swift
struct AuthMiddleware: ClientMiddleware {
    let tokens: TokenStore
    func intercept(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
                   operationID: String,
                   next: (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?))
        async throws -> (HTTPResponse, HTTPBody?) {
        var request = request
        if let token = await tokens.accessToken {
            request.headerFields[.authorization] = "Bearer \(token)"
        }
        let (response, body) = try await next(request, body, baseURL)
        if response.status == .unauthorized {
            try await tokens.refresh()          // single-flight; see below
            return try await next(request, body, baseURL)
        }
        return (response, body)
    }
}
```

| Question | Answer |
|---|---|
| Where is the JWT stored | **iOS: Keychain** (`kSecAttrAccessibleAfterFirstUnlock`). **Android: `EncryptedSharedPreferences`** or DataStore with the Jetpack Security crypto library. Never `UserDefaults`, never plain `SharedPreferences` |
| How requests receive it | Middleware / interceptor, above. No screen and no generated method ever touches a token |
| How logout removes it | Call `POST /auth/logout` to invalidate server-side, then clear the store — **in that order**, and clear locally even if the call fails |
| Token missing | The request proceeds without the header and the backend returns 401, which is handled uniformly. No special-casing at call sites |
| 401 handling | Refresh once, retry once, and on a second 401 clear the store and route to sign-in |
| Refresh | **Single-flight.** The token store is an `actor` on iOS and a `Mutex`-guarded suspend function on Android, so ten concurrent 401s produce one refresh call, not ten |

Single-flight is the detail worth insisting on: without it, the first screen that fires several
requests at once produces a burst of refreshes, and whichever finishes last wins. That is a real bug
that appears only under load and is very hard to reproduce.

## 2.6 · Base URLs and environments

Never in generated code, never a literal in a screen.

**iOS:** one `AppEnvironment` enum resolved from the build configuration (Debug / Staging / Release)
via `xcconfig`, so the URL is a build input rather than a code branch.
**Android:** `buildConfigField` per `buildType`/`productFlavor`, read once into the interceptor.

Both give the same property: **the URL is chosen by which build you made, not by a runtime
condition that can be wrong.** This mirrors the three-branch workflow — development, staging, main.

## 2.7 · NestJS OpenAPI quality

Measured across 52 routes and their DTOs.

| Check | Finding |
|---|---|
| `operationId` | **0 of 52.** NestJS then invents one from controller + method, so clients read `authControllerVerifyPhone(...)` |
| Explicit response DTOs | **43 typed, 5 explicit 204, 2 bodyless-but-undeclared.** An earlier count of "39 untyped" was wrong -- it was produced by a grep that did not match `@ApiOkResponse({ type: ... })`, which is the form this codebase actually uses |
| `@ApiOkResponse` etc. | 44 `@ApiOkResponse`, 1 `@ApiCreatedResponse`, 1 `@ApiNoContentResponse` — present but often without a `type` |
| `@ApiProperty` | 150 uses — good coverage |
| Enums | 20 — will generate as Kotlin/Swift enums if surfaced in typed DTOs |
| `: any` | **26 occurrences, all of them in `.spec.ts` test doubles.** None in a DTO, none in a controller, and the emitted schemas contain zero untyped properties. They do not touch the contract |
| Pagination model | **none** — no shared pagination DTO exists |
| Error response schema | not declared — errors generate as untyped |
| Auth scheme | declared in Swagger config; verify it emits `bearerAuth` in the document |
| File uploads | none currently |

### Backend OpenAPI changes required before client generation

**REQUIRED** — generation is not worth doing without these:

1. An emit script and an `npm run openapi:emit`
2. `operationId` on all 52 routes
3. Accurate responses on the 2 bodyless routes, the 2 with a status-code mismatch, and the 2 returning a nullable DTO
4. A CI step that fails when the committed spec differs from a freshly generated one

**RECOMMENDED:**

5. ~~Remove or narrow the 26 `: any`~~ — **withdrawn.** All 26 are test doubles and none affects the contract
6. A declared error-response schema, so both clients get one typed error rather than a blob
7. A shared pagination DTO before the first list endpoint ships

**NICE TO HAVE:**

8. Consistent `operationId` naming (`verbNoun`, no controller prefix)
9. Explicit `nullable` vs optional on every DTO property — the distinction becomes `String?` vs a
   missing key, and getting it wrong produces crashes on the client
10. Date representation stated once (ISO 8601 strings) rather than per DTO

### CI check for contract drift

```yaml
- run: npm run openapi:emit
- run: git diff --exit-code openapi.json ||
        { echo "openapi.json is stale -- run npm run openapi:emit and commit"; exit 1; }
```

Because both apps build from the committed spec, a stale spec becomes a **build failure in the app
that uses the changed field**, which is exactly the intended behaviour: a breaking backend change
stops the client build instead of surprising a user.

---

# Part 3 — Design system and UI primitives

## 3.1 · What is hardcoded today

| Token class | Centralised? | Raw uses in screens |
|---|---|---|
| Colours | partly | **19 Swift / 14 Kotlin** — but see below |
| Typography | partly (families, some sizes) | point sizes still stated at call sites |
| Spacing | **no** | raw `dp` / points throughout |
| Corner radius | **no** | raw values |
| Component heights | **no** | raw values |
| Shadows / elevation | **no** | raw values |
| Icon sizes | **no** | 41 fixed frames, all icon-shaped |
| Opacity | **no** | raw values |
| Animation timings | **no** | raw values |

**The colour count needs splitting, and the split changes what to do:**

| | Swift | Kotlin | |
|---|---|---|---|
| Third-party brand colours | 7 | 7 | Google's four logo colours, its border grey and label black; Facebook blue |
| Everything else | 12 | 7 | illustration fills, gradient stops, a scrim |

**The 14 brand colours must stay hardcoded.** Google and Meta specify exact values in their sign-in
branding rules; a re-themeable token is the wrong container for a value we are not permitted to
change. **The other 19 are the finding** — the eyebrow bug was exactly this shape, a colour decided
inside one screen that should have been a token with two variants.

A checker that fails on every literal would have flagged all 14 brand colours and been switched off
within a day. **The allowlist is the design, not an exception to it.**

## 3.2 · Proposed tokens

Derived from `colors_and_type.css` and the current screens, not invented.

**Colour** — `background`, `surface`, `primary`, `secondary`, `accent`, `textPrimary`,
`textSecondary`, `error`, `warning`, `success`, `disabled`, `border`. Plus a separate `Brand`
namespace holding the third-party values, each carrying the rule that mandates it.

**Typography** — named roles only, so no screen ever states a point size: `display`,
`headlineLarge`, `headlineMedium`, `title`, `body`, `bodyEmphasis`, `label`, `caption`, `button`.

**Spacing** — a 4-point scale, which is what the handoff already uses:
`xxs 2 · xs 4 · sm 8 · md 16 · lg 24 · xl 32 · xxl 48`.

**Other** — `radius` (sm 8, md 16, pill 999), `height` (control 56, compact 44), `elevation`,
`icon` (sm 16, md 20, lg 24, xl 56), `duration` (fast 150, base 250, slow 400).

## 3.3 · Native duplication or a shared source?

Three options, and the recommendation is deliberately the least clever one.

| Approach | Pros | Cons |
|---|---|---|
| **Duplicated natively** (recommended now) | No build step, no new tooling, full IDE support, readable in both languages | Two files to keep in step |
| Generated from shared JSON | One source of truth | A generator, a build step and a new failure mode, to keep ~50 values in step |
| A cross-platform styling framework | — | Disproportionate at this size |

**Recommendation: duplicate natively, and enforce parity with a checker.** We already run
`verify-*.py` scripts that compare Kotlin against Swift; extending them to token values costs a few
lines and gives the same guarantee a generator would, without a build step. Revisit if the token
count passes a few hundred or a third platform appears.

## 3.4 · The five primitives

**The candidate list is right, with one substitution to argue about.** Our codebase says the button
is the most urgent by a distance:

| Primitive | Today | Verdict |
|---|---|---|
| **PrimaryButton** | Android: `PillButton` (used by 6 files), `NextButton`, `SunsetButton`. iOS: `NextButton`, `SunsetButton`, plus the phone CTA inline | **Three implementations.** `ConnectAccountScreen` imports two of them, because it sits on the seam between the `welcome` and `tutorial` packages |
| **InputField** | inline in the phone screens, both platforms | The 23dp tap-target bug lived here |
| **TopBar** | inline in `VerificationFrame` | The wrong-icon bug lived here |
| **StatusBadge** | private `Eyebrow` in Connect + a pill inline in `TutorialShell` | Twice on both platforms, and they diverged — the eyebrow bug |
| **SelectPicker** | `CountrySheet`, shared on both | **Already fine.** The one that needs formalising, not building |

**The substitution worth discussing:** `SelectPicker` is done, and the thing we actually keep
rebuilding is the **screen scaffold** — the backdrop, safe-area handling and CTA placement that
every screen repeats. If a fifth slot is wanted for new work rather than consolidation, `ScreenScaffold`
earns it more than `SelectPicker` does.

### Specification, per primitive

**PrimaryButton** — the one to build first.

| | |
|---|---|
| Purpose | Every terminal action: Next, Continue, Create free account |
| Variants | `sunset` (gradient), `pill` (solid), `quiet` (border only) |
| States | normal · pressed · disabled · loading · destructive |
| API | `label`, `onTap`, `variant`, `isEnabled`, `isLoading`, `role` |
| Accessibility | label = visible text; loading announces "busy"; disabled announces disabled |
| Loading | Spinner replaces the label **in place**, width pinned, so the button never resizes |
| Min tap target | 56dp preferred, **44pt hard floor** |
| Previews | normal · pressed · disabled · loading · a 40-character German label · 320dp · 440pt |
| Tests | Height never below 44; label never clipped; **width identical between idle and loading**; disabled does not fire |

The loading rule is not cosmetic: a button that resizes when tapped moves everything under it, which
is the same class of defect as the 56-point CTA jump.

**InputField** — `label`, `value`, `onChange`, `placeholder`, `error`, `keyboardType`, `isEnabled`.
States: empty · focused · filled · error · disabled. **The error slot must reserve its height
whether or not an error is present** — this is the exact bug from Part 1.3, written into the
component so no screen can reproduce it. Min tap target 44pt on the *hit area*, not the drawn box.

**TopBar** — `title`, `onBack`, `trailing`. **One back control, one icon** (`chevron-left`, per the
handoff), one place to get it wrong. Back must be ≥ 44pt including padding.

**StatusBadge** — `label`, `tone`. **Tone is a parameter with both values** (`orange` for phone
screens, `lavender` for tutorial cards), which is precisely what would have prevented the eyebrow
bug: fixing one screen could not break the other.

**SelectPicker** — `items`, `selection`, `onSelect`, `searchable`. Exists as `CountrySheet`;
formalise the interface and add the keyboard-overlap test from Part 6.

### Folder structure

```
mobile/welcome-screen/
  ios-app/ShowUpWelcome/
    DesignSystem/
      Tokens/       Colors.swift  Typography.swift  Spacing.swift  Brand.swift
      Components/   PrimaryButton.swift  InputField.swift  TopBar.swift  StatusBadge.swift  SelectPicker.swift
    Screens/
  android-preview-project/app/src/main/java/com/showup/
    designsystem/
      tokens/       Colors.kt  Typography.kt  Spacing.kt  Brand.kt
      components/   PrimaryButton.kt  InputField.kt  TopBar.kt  StatusBadge.kt  SelectPicker.kt
    welcome/  tutorial/
```

A `com.showup.designsystem` package already exists on Android — `TutorialShell.kt` imports
`EyebrowBg` from it — so this formalises something started rather than introducing it.

---

# Part 4 — AI guardrails, CLAUDE.md

## 4.1 · Scoping, and why three files

This is a **monorepo**: backend and both apps in one repository. Claude Code reads `CLAUDE.md` files
from the repository root **down to the directory being worked in**, and the nearest file wins on a
conflict. So scope determines what applies:

| File | Applies to | Holds |
|---|---|---|
| `mobile/welcome-screen/CLAUDE.md` | both apps | Everything true of both: state ownership, both-platforms-together, design tokens, layout, verification, house style |
| `mobile/welcome-screen/ios-app/CLAUDE.md` | iOS only | SwiftUI, strict concurrency, `@MainActor`, `@State` / `@Binding` / `@Observable` rules, availability, previews, UIKit-wrapper policy |
| `mobile/welcome-screen/android-preview-project/CLAUDE.md` | Android only | Compose, `remember` vs `rememberSaveable`, coroutines, lifecycle-aware collection, `minSdk`, previews |

**Why not two files with everything duplicated:** a rule stated twice drifts, and the parity rules
("both platforms change in the same commit") have no natural home in a platform-specific file. The
shared parent carries what is genuinely shared; each child carries only what is genuinely
platform-specific.

All three files were written as part of this spike and are live.

## 4.2 · The three rules the brief mandates, verbatim

Present in all three files:

> **Before creating a new implementation, first search the repository for an existing shared
> component, token, model, service, or pattern. Do not create a duplicate implementation without
> explaining why.**

> **Generated code compiling is not proof that the feature is complete.**

> **Do not report a platform as verified unless it was actually built and tested in that platform's
> toolchain.**

The first would have prevented the three-way button duplication. The third is the rule Claude broke
when eight Swift tests were reported as present and had never run.

## 4.3 · What is enforced versus what is written

A rule nothing checks is a preference. Current state:

| Rule | Enforced by |
|---|---|
| iOS 17.0 / Android API 30 minimums | build settings + `check-ios-availability.py` |
| No `@unchecked Sendable` / `nonisolated(unsafe)` / `@preconcurrency` | `check-swift-concurrency.py` |
| No mutable global state; no `DispatchQueue` in new code | `check-swift-concurrency.py` |
| Strict concurrency stays on | `check-swift-concurrency.py`, generator and project file |
| Tap targets ≥ 44pt | `ScreenFitTest`, 17 sizes |
| Both platforms change together | `verify-*.py` parity checks |
| Copy matches the ticket | `verify-*.py`, quoting the ticket |
| No new UIKit wrapper without a written reason | **review only** |
| No fixed-width frame on text or layout regions | **review only** |
| No raw hex outside the design system | **proposed** (Part 3) |
| No point size outside the type scale | **proposed** (Part 3) |

**On UIKit wrappers:** one exists and should stay. `WashHeadline` is a `UIViewRepresentable` because
the underline must be measured from laid-out text, which SwiftUI does not expose. The rule is "no
NEW wrapper without a stated reason", not a ban — a ban gets broken quietly instead of argued with.

**On MVVM:** settled as the principle rather than the pattern name — a view holds no state, hoisted
to one owner per flow, each platform in its own idiom. `@Observable` and ViewModels earn their place
the moment a screen owns asynchronous work; none of the five do, and profile and discovery will.

---

# Part 5 — Definition of Done

Copy-pasteable into a PR template. A screen is **not** done because Claude generated it, because it
looks right in one preview, or because static inspection passed.

**Build**
- [ ] iOS builds in Xcode (CI, `macos-15`)
- [ ] Android builds with Gradle, debug **and release** (R8 removes what it cannot see used)
- [ ] No new compiler warnings

**Static analysis**
- [ ] All 9 conformance checkers pass
- [ ] SwiftLint / detekt clean *(neither configured yet — see Part 7)*

**UI**
- [ ] Renders at **320 × 686** and **440 × 956** with no new fit findings
- [ ] No clipping, no overlap, no unwanted scrolling; intended scrolling works
- [ ] Safe area correct, including Dynamic Island
- [ ] **Keyboard does not hide any required control** *(not yet automated)*
- [ ] Long localised text does not break layout — German is longer than English
- [ ] Disabled, loading, error and empty states all render

**Interaction**
- [ ] Every button works and every selector opens
- [ ] Back navigation works and lands where intended
- [ ] Repeated taps do not corrupt state or double-fire
- [ ] Async actions cannot be triggered twice

**State**
- [ ] State persists only where intended, resets only where intended
- [ ] Parent and child stay synchronised
- [ ] **Survives rotation, backgrounding and process death**, or the exception is documented

**API**
- [ ] Request and response types come from the generated client, not hand-written
- [ ] Loading, error and empty states exist for every call
- [ ] Auth attached by the interceptor, never at the call site

**Accessibility**
- [ ] Every tappable thing ≥ 44pt / 48dp
- [ ] Labelled for VoiceOver and TalkBack
- [ ] Usable at the largest system font *(not yet automated)*
- [ ] Contrast meets WCAG AA

**Tests**
- [ ] Unit tests for any logic
- [ ] The screen is in `ScreenFitTest`
- [ ] Both platforms changed in the same commit

**And the one with no substitute**
- [ ] **Somebody has looked at it running, on a device**

The wrong back icon and the wrong eyebrow tone both passed every automated check, because both were
real components from the design system, correctly drawn, in the wrong place. No checker will ever
catch that class of defect.

---

# Part 6 — Compatibility matrix

## 6.1 · Current configuration, as found

| Setting | Value | Where |
|---|---|---|
| iOS deployment target | **17.0** | `gen_pbxproj.py` |
| Android `minSdk` | **30** | `app/build.gradle.kts` |
| Android `targetSdk` | **36** | same |
| Android `compileSdk` | **36** | same |

## 6.2 · The product requirement conflict — not resolved here

The specification contradicts itself:

> Area 4: *"minimum OS targets (iOS 17+, **Android 14+**)"*
> Compatibility: *"Minimum supported should be: iOS 17.0 and **Android API 30**"*

**Android 14 is API 34. API 30 is Android 11.** Three years and three releases apart. As instructed,
this is flagged rather than silently decided. **The code currently implements API 30.**

| | **API 30 (Android 11)** | **API 34 (Android 14)** |
|---|---|---|
| Device coverage | Substantially wider — includes phones sold 2020-2023, which remain a large share of active Android devices, especially outside the US. *Take the current figure from the Android Studio distribution dashboard rather than any estimate here* | Materially narrower; excludes most devices more than ~2 years old |
| Testing burden | Wider matrix; more variation in OEM behaviour | Narrower and more predictable |
| API compatibility burden | Some newer APIs need `Build.VERSION` gates or AndroidX compatibility wrappers | Fewer gates; more platform APIs available unconditionally |
| Does current code assume newer APIs? | **No.** Nothing in the app requires above API 30 today; the code compiles and its tests pass at `minSdk = 30` | — |
| Reversibility | Raising the minimum later is easy | **Lowering it later is not** — it means re-testing everything on older devices |

**The asymmetry is the argument.** Raising the floor later costs almost nothing. Lowering it later
costs a full re-test. Since nothing in our code needs API 34, **starting at 30 keeps the option open
in both directions**, and the decision can be made on market data when there is a launch date.

For Germany specifically, the older-device share is worth checking before deciding — this is a
market question, not a technical one.

## 6.3 · Test sizes

`ScreenFitTest` runs every screen state across **17 device sizes** on every pull request, with
per-device safe-area insets modelled.

| Role | Size | Device |
|---|---|---|
| **Narrowest overall** | 320 × 686 | Galaxy Fold cover screen |
| Smallest iOS | 375 × 667 | iPhone SE (3rd gen) |
| Smallest Android | 360 × 640 | 4.7-inch Android |
| Largest modern Android | 412 × 915 | Pixel 8 Pro |
| Largest modern iOS | 430 × 932 / 440 × 956 | iPhone 15/16 Pro Max |

**The brief names the iPhone SE as the smallest screen, and it is not the hardest case.** The
narrowest in the matrix is the **Galaxy Fold cover screen at 320dp** — 55 points narrower than the
SE, on a device that runs API 30 perfectly well. A layout that fits the SE can still break there.
The SE is the iOS floor, not the floor.

## 6.4 · What is and is not measured

| Requirement | Status |
|---|---|
| Safe area | **Automated**, per-device insets |
| Dynamic Island | **Automated** via the 430/440 insets |
| Text wrapping / clipping | **Automated** — caught a real Windows/Linux difference |
| Button visibility | **Automated** — CTA movement asserted |
| Tap targets | **Automated**, 44pt floor |
| Status / navigation bars | **Automated** via insets |
| Edge-to-edge | **Not tested** |
| **Keyboard overlap** | **Not tested.** The phone and code screens are at risk, and the smallest screen is where it bites. Needs the keyboard modelled as another inset — a contained addition to the existing harness |
| **Font scaling / accessibility sizes** | **Not tested.** Everything is measured at the default size. This is where "no scroll" requirements break, and it is an EU legal requirement under the Accessibility Act since June 2025 |
| Scroll behaviour | Partly — presence of scroll is checked, behaviour is not |
| Modals / sheets | Not systematically |
| Landscape | **Not tested, and not decided** |

## 6.5 · Portrait lock — recommendation

**Yes, lock portrait**, for this app, now.

It is a dating app whose flows are single-column, camera-and-photo oriented, and read one-handed.
Landscape would double the layout matrix, and it is not how anyone uses this category. Locking is
one line per platform, and it converts an untested dimension into a non-existent one — which is
strictly better than an untested one. Revisit only if tablet support is ever wanted.

---

# Part 7 — Automated testing strategy

**The smallest useful stack**, not everything that exists.

## 7.1 · Recommended

| Layer | iOS | Android | Status |
|---|---|---|---|
| Unit / logic | **XCTest** | **JUnit** | **In place** — 32 and 45 tests |
| Component / layout | **`ScreenFitTest`** (renders at 17 sizes, measures) | **Robolectric + Compose UI test** | **In place** — the harness that found the 23dp target and the CTA movement |
| Lint | **SwiftLint** | **detekt** | **Not configured — recommended, P2** |
| UI / end-to-end | XCUITest | Espresso | **Not recommended yet** |
| Screenshot / golden | — | Paparazzi or Roborazzi | **See below** |

**Swift Testing over XCTest?** Not yet. It is the better API, but the existing 32 tests are XCTest
and the harness works; migrating buys nothing today. Adopt for new test *files* once iOS CI has been
stable for a while.

**XCUITest / Espresso?** Not yet. Full end-to-end UI tests are the slowest and most brittle layer,
and the bugs we actually shipped were all caught — or catchable — one layer down, by measurement.
Revisit when there are real user journeys crossing several flows.

## 7.2 · Can we automatically detect visual regressions from Claude-generated changes?

**Partly today, and this is the honest answer.**

**What we already detect** — and it is more than screenshot diffing would: element positions and
sizes, clipping, safe-area violations, tap targets, CTA movement, text collapse, at 17 sizes, on
every pull request. This is *measurement*, and it does not produce false alarms when a colour
changes by one shade.

**What we do not detect: anything about appearance.** The wrong icon, the wrong tone, the wrong
underline width — all six of this month's UI defects — pass every one of those checks, because they
are correct components correctly drawn.

**Screenshot testing would catch exactly those.** Recommendation:

| | |
|---|---|
| Tool | **Roborazzi** on Android (runs on the JVM with the existing Robolectric setup, no emulator, no extra CI cost) |
| Where it runs | The existing Android CI job |
| Baselines | Committed PNGs. A diff fails the build and posts the image; a reviewer either fixes the code or approves the new baseline in the same PR — **approval is a human action in the diff, never automatic** |
| Maintenance | **This is the real cost.** Every intentional design change re-baselines every affected screen, and fonts or renderer versions can shift pixels for no product reason. Budget for genuine churn |
| iOS equivalent | Deliberately deferred — start on one platform, learn the maintenance cost, then decide |

**Recommended as P2, not P0.** It closes the one gap automation cannot otherwise reach, but it is
the highest-maintenance thing in this document, and it should not land in the same month as the
primitives — every primitive extraction would re-baseline everything.

---

# Part 8 — Claude Code workflow

The most important section, and the one the brief cares most about: **better first-pass output, not
faster repair.**

## 8.1 · The workflow

1. **Read the scoped `CLAUDE.md`** — repository root down to the working directory
2. **Read the ticket**, and treat its copy block as authoritative over any render or PNG
3. **Search for an existing primitive, token, model or pattern before writing anything new** — and
   if creating a duplicate, say why in the PR
4. **Write the spec's ambiguities down before coding.** Where the ticket is silent, record the
   inference in `audit/CONFLICTS-*.md` rather than deciding silently. This is what the Connect-screen
   question came from
5. **Implement on both platforms in the same commit**
6. **Add previews** at the smallest and largest sizes, not just the default
7. **Add the screen to `ScreenFitTest`** and write tests for any logic
8. **Compile both platforms.** Not one
9. **Run the 9 conformance checkers**
10. **Report using the format below**, separating verified from unverified
11. **A person looks at it running** before merge
12. **Merge only against the Definition of Done**

Steps 3 and 4 are the two that most improve first-pass quality, and neither is about code. Most of
our defects were decisions, not syntax: which component, which tone, what the ticket meant.

## 8.2 · Required response format

Claude must use this after implementing a screen. **The point is that "unverified" is a first-class
outcome** — the failure mode this prevents is a confident "done" backed only by reading.

```
IMPLEMENTATION COMPLETE — <screen> (<ticket>)

Files changed:
  <path>  (+n / -n)

Build:
  Android:  PASS  (assembleDebug + assembleRelease)
  iOS:      PASS  (xcodebuild test, macos-15)

Tests:
  Android:  45 passed
  iOS:      32 passed
  Fit:      17 device sizes, 0 new findings

Checkers:
  9/9 pass

Device checks:
  320x686 Fold cover:      OK
  375x667 iPhone SE 3:     OK
  440x956 iPhone 16 PM:    OK

VERIFIED:
  - <what was actually built, run or measured>

UNVERIFIED:
  - <what was only read, and why>

Known limitations:
  - <e.g. keyboard overlap not measured on any screen>

Manual checks still required:
  - <what a person must look at, and on which device>
```

**Rules for it.** Never write PASS for a platform that was not built. Never list something under
VERIFIED that was only inspected — "the code looks correct" belongs under UNVERIFIED with the reason.
If any section is empty, say "none" rather than deleting the heading, so an omission is visible.

## 8.3 · What this can and cannot fix

Worth stating plainly, because it is the question behind the whole brief.

**Automation catches the measurable**: clipping, movement, tap targets, drift between platforms,
copy against the ticket, forbidden patterns, anything with a number.

**It does not catch the tasteful**: the wrong icon from the right icon set, the wrong tone of a real
component, a layout that is technically correct and looks wrong.

**Expect the volume of repair to drop sharply and not to reach zero.** The realistic goal is that
every *measurable* defect is caught before a person sees it, so human review spends its time on
judgement instead of on finding clipped text.

---

# Part 9 — CI/CD proposal

## 9.1 · What runs today

Three jobs on every pull request and on pushes to `development`, `staging` and `main`:

| Job | Runner | Does |
|---|---|---|
| Lint, build, unit & e2e tests | `ubuntu` | The backend |
| Android build, unit tests & design conformance | `ubuntu` | `assembleDebug`, `assembleRelease`, 45 tests, 9 checkers |
| iOS build, unit tests & screen fit | **`macos-15`** | Regenerates and verifies `project.pbxproj`, resolves packages, builds, runs 32 tests |

Two details in the iOS job are worth keeping deliberately: it **regenerates `project.pbxproj` and
fails if the committed one is stale** — which is what let eight tests sit in no target — and it sets
`pipefail`, without which `xcodebuild | xcbeautify` returns xcbeautify's exit code and **a red suite
goes green**.

## 9.2 · Minimum viable · recommended · future

| Tier | Android | iOS | Backend / OpenAPI |
|---|---|---|---|
| **Minimum viable** — *all in place* | Gradle build + unit tests | Xcode build + unit tests | Lint, build, tests |
| **Recommended** — *next* | + detekt | + SwiftLint | **+ emit `openapi.json` and fail if stale** · **+ fail if a generated client is out of date** |
| **Future** | + Roborazzi screenshot tests | + screenshot tests once the Android cost is known | + contract tests against a running backend |

**Runners:** `ubuntu-latest` for backend and Android; **`macos-15` is required for iOS** and is the
only paid-tier-sensitive part of the pipeline — macOS minutes bill at a higher multiplier than Linux.
Current iOS job runtime is under four minutes, so this is not currently a cost concern, but it is the
line to watch if the matrix grows.

**Do not add** an emulator-based Android instrumentation job. Robolectric already gives us the
layout measurement on the JVM, at a fraction of the time.

---

# Part 10 — Risk register

Likelihood and impact are for **our** codebase as it stands, not in general.

| Risk | Platform | Likelihood | Impact | How we detect it | How we prevent it | Automatable | Affects current code |
|---|---|---|---|---|---|---|---|
| Compiles on Android, fails on iOS | Both | **Was high, now low** | High | Both built in CI | Both platforms in the same commit | **Yes — done** | No longer |
| Incorrect SwiftUI state ownership | iOS | Low | High | Compile + run | State hoisted to one owner; `CLAUDE.md` | Partly | No |
| SwiftUI/UIKit binding conflicts | iOS | **Medium** | High | Only by running | Never reformat in a `Binding` getter | **No** — needs a device | **Was the phone-field bug** |
| Compose state duplication | Android | Low | Medium | Review | Hoisting; no ViewModels yet | Partly | No |
| Reserved region collapsing to nil | iOS | **Medium** | High | CTA-movement test | Always render + `opacity` | **Yes — done** | Fixed |
| Touch interception / small hit areas | Both | Medium | High | `ScreenFitTest` 44pt floor | Primitives with the floor built in | **Yes — done** | Fixed |
| Keyboard overlap | Both | **Medium** | High | **Nothing today** | Model the keyboard as an inset | Yes — **not built** | Unknown |
| Fixed-size layouts | Both | Low | Medium | 17-size measurement | No fixed width on text | **Yes — done** | No |
| Safe-area issues | Both | Low | Medium | Per-device insets | Same | **Yes — done** | No |
| **API contract drift** | Both | **High once networking exists** | **High** | Nothing today | Generated clients + stale-spec CI | **Yes — not built** | **No client code yet** |
| Manually duplicated DTOs | Both | **High if we hand-write** | High | Review | Generate everything | Yes | None yet |
| Auth header inconsistency | Both | Medium | High | Review | One interceptor, never at call sites | Partly | None yet |
| OS/API compatibility | Both | Low | Medium | `check-ios-availability.py`; `minSdk` | Stated minimums | Partly | No |
| Dependency version mismatch | Both | Low | Medium | CI build | Pinned versions | Yes | No |
| Xcode/Gradle project drift | iOS | **Was high** | High | `pbxproj` regenerated and diffed in CI | Generate, never hand-edit | **Yes — done** | **Was the 8-lost-tests bug** |
| Font / resource issues | Both | Low | Medium | Fit harness | Bundled fonts, checked | Partly | No |
| **Visual regression** | Both | **Medium** | **High** | **Only human review** | Screenshot tests (Part 7) | Yes — **not built** | **All six UI bugs** |
| Missing loading/error/empty states | Both | Medium | Medium | Review + DoD | DoD gate | Partly | Not yet applicable |
| Accessibility regression | Both | **Medium** | High (legal) | Tap targets only | Dynamic Type + contrast checks | Partly — **not built** | Unknown |
| Race conditions | Both | Low | High | Strict concurrency | `@MainActor`, actors, checker | Partly | No |
| Navigation state bugs | Both | **Low now, rising** | Medium | Manual | Adopt a navigation framework | Partly | Enum routing |
| **Claude claiming unperformed verification** | Both | **Medium** | **High** | Human noticing | Required response format; CI as the source of truth | Partly | **Was the 8-test claim** |

**Reading the register:** the two highest-value unbuilt items are **contract drift** (high
likelihood, high impact, fully automatable, and currently costs nothing to prevent because no client
code exists) and **visual regression** (already our most frequent real defect, and the only one no
current check can see).

---

# Part 11 — Prioritised action plan

## P0 — before generating more screens

| # | Task | Why | Size | Files | Depends on | Blocks frontend? |
|---|---|---|---|---|---|---|
| 1 | ~~Emit `openapi.json` + `operationId` on 52 routes + fix the response gaps~~ **DONE 4 Sep** | Every hand-written client model becomes throwaway work. Zero exist today | **M** | `src/**` | — | was **Yes** |
| 2 | Design tokens: spacing, type roles, illustration palette, brand allowlist | Every screen built without them is migrated by hand later | **M** | `DesignSystem.*` | — | **Yes** |
| 3 | `PrimaryButton` — collapse three implementations into one | Three exist; one screen imports two. Caused two defects | **S** | `designsystem/components` | 2 | **Yes** |
| 4 | `InputField`, `TopBar`, `StatusBadge` with the 44pt floor and reserved error slot built in | The three defects that shipped live in exactly these | **M** | same | 2, 3 | **Yes** |

**P0 is four items and they are the answer to "what should we implement NOW".** Five screens exist.
Every screen added before these lands is migrated afterwards by hand.

## P1 — very soon

| # | Task | Why | Size | Depends on |
|---|---|---|---|---|
| 5 | Generated client on Android, then iOS | Proves the pipeline end to end | M | 1 |
| 6 | Auth interceptor + Keychain / EncryptedSharedPreferences, single-flight refresh | Refresh tokens already exist server-side | M | 5 |
| 7 | Keyboard overlap in the fit harness | Named in the brief, entirely unmeasured, and the phone screens are at risk | **S** | — |
| 8 | Adopt `NavigationStack` + Navigation Compose | Five screens is the cheapest this ever gets | M | — |
| 9 | Lock portrait | One line per platform; removes an untested dimension | **S** | — |

## P2 — after core architecture stabilises

| # | Task | Why | Size |
|---|---|---|---|
| 10 | Dynamic Type / largest-font measurement | EU Accessibility Act; where "no scroll" breaks | M |
| 11 | Roborazzi screenshot tests on Android | The only automation that catches appearance | M |
| 12 | SwiftLint + detekt | Cheap, but neither has caught anything we have hit | S |
| 13 | Pagination DTO before the first list endpoint ships | Quality of the generated clients | S |

## P3 — later

| # | Task |
|---|---|
| 14 | iOS screenshot tests, once Android's maintenance cost is known |
| 15 | Contract tests against a running backend |
| 16 | Shared token generation from JSON, if the token count justifies it |
| 17 | Edge-to-edge and landscape, if portrait lock is ever reversed |

---

# Appendix A — Root cause, by file

Every actual issue found. All were fixed before this document.

| File | Issue | Rating |
|---|---|---|
| `ios-app/…/PhoneVerificationView.swift` | Bare `if` in a `ViewBuilder` collapsed the reserved region to nil; CTA moved 56pt in states C/D | CONFIRMED · fixed |
| `ios-app/…/PhoneVerificationView.swift` | Number never grouped — `Binding` getter rewrote text owned by UIKit | CONFIRMED · fixed |
| `ios-app/…/PhoneVerificationView.swift` | Keystrokes lost, 10 typed → 5 landed, same cause | CONFIRMED · fixed |
| `ios-app/…/CountryCodes.swift` and others | 10 build errors on first Mac build, incl. `[String: Any]` needing `[String: any Sendable]` | CONFIRMED · fixed |
| `ios-app/ShowUpWelcomeTests/` | 8 tests in no target for a week — never executed | CONFIRMED · fixed, CI now diffs `pbxproj` |
| `android/…/PhoneVerificationScreen.kt` | Input 23dp inside a 56dp row; hit area a third of the visible control, 15 of 18 sizes | CONFIRMED · fixed |
| `android/…/PhoneVerificationScreen.kt` | One line reserved for two-line copy; CTA dropped 15.7dp in states A/B | CONFIRMED · fixed |
| `android/…/WelcomeShell.kt` | Back control drawn `arrow-left`; handoff says `chevron-left` | CONFIRMED · fixed |
| `android/…/ConnectAccountScreen.kt` | Eyebrow pill lavender on a screen the design paints orange | CONFIRMED · fixed |
| `android/…/WelcomeShell.kt` | Underline wash covered 56% of its phrase, spec says 78% | CONFIRMED · fixed |
| `android/…/WelcomeShell.kt` | CTA glow fell to the left — `Modifier.shadow` uses a window-relative light source | CONFIRMED · fixed |
| CI | Error text fit on Windows, clipped on Linux — same commit | CONFIRMED · fixed |
| `ios-app/…` (7 files) | `GeometryReader` used to read parent size; legitimate, but it fills its parent and top-leading-aligns its child | **LIKELY — review** |
| both | No navigation framework; routing by enum | CONFIRMED present · risk, not a bug |
| both | No process-death restoration on iOS; Android has 16 `rememberSaveable` | **LIKELY gap** |

# Appendix B — OpenAPI readiness, by area

| Area | Routes | Untyped responses | `operationId` |
|---|---|---|---|
| `safety-admin` | — | **9** | 0 |
| `auth` | 8 | **6** — incl. `/auth/apple`, `/auth/google`, `/auth/refresh` | 0 |
| `dates` | — | **5** | 0 |
| all others | — | 19 | 0 |
| **Total** | **52** | **2 (both bodyless by design)** | **52 of 52 since 4 Sep** |

The `: any` count is 26 and **all of them are test doubles** — none reaches the contract. A
pagination DTO is still absent. The error schema now exists (`ApiErrorDto`). Current state is in
`docs/openapi-contract.md`.

---

# Questions and decisions needed from the team

1. **Android minimum: API 30 or API 34?** The specification says both. The code implements **API 30**
   and nothing in it requires anything newer. Raising the floor later is cheap; lowering it later is
   not. **Product decision, needs market data for Germany.**
2. **Primitives: retrofit the five existing screens, or apply forward-only and migrate gradually?**
   Meaningfully different amounts of work.
3. **Are the backend OpenAPI changes in scope for the mobile team?** P0 item 1 is entirely backend.
4. **Does a returning user see the Connect screen?** SHOWUP-146 says a returning user skips the
   tutorial and says nothing about Connect. We assumed they skip both. **No ticket states it.**
5. **Lock portrait?** Recommended in 6.5, but it is a product call.
6. **Screenshot testing: accept the re-baselining cost?** It is the only automation that catches our
   most common defect class, and the highest-maintenance item in this document.
7. **Split into separate iOS and Android repositories?** The brief assumes two. There is one, and
   the parity checks that keep the platforms aligned need both in one place. Recommend keeping one.

Items that could **not** be determined from the repository: current Android version distribution for
our target market; whether landscape is wanted; the launch date, which affects the API-level decision.

---

# Sources

Primary documentation only, as instructed.

- Apple — Swift OpenAPI Generator: `github.com/apple/swift-openapi-generator` and its
  `Documentation/` directory
- Apple — SwiftUI `ViewBuilder`, `Binding`, and Observation framework reference
- Apple — Human Interface Guidelines, minimum 44pt tap targets
- Android Developers — Jetpack Compose state and state hoisting; `rememberSaveable`
- Android Developers — Material accessibility, 48dp minimum touch targets
- Android Studio — version distribution dashboard (for the API 30 vs 34 decision)
- OpenAPI Generator — Kotlin generator and Gradle plugin documentation
- NestJS — OpenAPI (Swagger) module documentation
- Swift — strict concurrency and `Sendable` documentation
- European Accessibility Act, applicable since June 2025

# Client Architecture Spike

3 September 2026 · tied to the files in this repository, not to general practice

---

# 1 · Where developing with Claude has bottlenecks

This is the question underneath the brief, so it comes first. One example each, not a catalogue.

## The pattern

**Claude writes code well and cannot see what it made.** Every serious defect this month came from
that gap, not from bad code. Six UI bugs were real components from the design system, correctly
built, in the wrong place or with a property nobody could observe — the back control drawn as an
arrow where the design says chevron, an underline covering 56% of its phrase instead of 78%, a
button jumping 56 points on iOS when you mistyped a code. Reading the code cannot find these,
because the code says what it intends and the intention was wrong.

| Bottleneck | One example | What stops it |
|---|---|---|
| **Cannot see the output** | Six UI defects passed every review except somebody looking at the screen | Build and run on both platforms, in CI |
| **Confident wrong reasoning when it cannot verify** | Text was clipped; I proposed three expensive fixes. Real cause was one line — `height` applied before `padding` | Treat any conclusion reached without execution as a hypothesis |
| **Copy taken from the wrong part of a ticket** | Seven error messages lifted from the ticket's **Tracking** section, where they are analytics labels | One marked copy block per ticket; checks quote the ticket |
| **Invents behaviour where the spec is silent** | SHOWUP-146 says a returning user skips the tutorial, says nothing about Connect. We made them skip both | Inferences recorded where the product side reads them |
| **Checks that pin the mistake** | A check asserted the hand-tuned underline widths — which *were* the bug — then blocked the fix | Checks quote the ticket, not the code |
| **Tests that exist without running** | Eight Swift tests sat in the repo for a week in no test target | CI runs them; "present" is never reported as "passing" |
| **One machine is not enough** | A message fit on Windows, was cut off on Linux CI — same commit, same device sizes | CI builds on a different OS than we develop on |

## What this means in practice

**The bottleneck is the loop between writing and seeing, not the code.** Where that loop was closed
— Android, since CI landed — defects died in minutes. Where it was open — iOS, for weeks — they
accumulated silently and then arrived all at once: ten build errors and four runtime bugs in one
afternoon.

**As of 1 September the loop is closed on both platforms.** Eman added a `macos-15` CI job the same
day he first ran the iOS app, and it has been green since. Both apps are now compiled, tested and
measured at 17 screen sizes before anything merges.

**The honest answer to "can we stop the fixing cycle?"** Partly, and it is already much better than
it was. Automation catches clipping, movement, tap targets, drift between platforms, and anything
with a number attached. It cannot catch "this is the wrong icon" or "this tone is wrong for this
screen" — those are real components used incorrectly, and only a person looking at the screen finds
them. Expect the machine to catch the measurable and a person to catch the tasteful. The volume of
fixing drops sharply; it does not go to zero.

---

# 2 · Code-level analysis, iOS

Ten build errors, then four runtime bugs, on the first real device run. The build errors were
ordinary. The four behavioural ones map onto the brief's categories.

## 2.1 · The CTA moved 56 points — the bug worth understanding

**`PhoneVerificationView`, states C/D.** The error card was wrapped in a bare `if mismatch { … }`.

In SwiftUI, an `if` with no `else` inside a `ViewBuilder` produces **nil**, and a frame and padding
around nil both collapse to nothing. The region meant to reserve 42 points reserved zero, so the
button jumped the full 56 when an error appeared — which SHOWUP-143 forbids outright. Fixed by
always rendering the card and hiding it with `opacity`. Now 0.0pt.

**Android did not have this bug**: a Compose `Box` lays out at its minimum height whether or not its
content is emitted. Identical logic error, severe on one platform, invisible on the other.

## 2.2 · State handling — the brief's hypothesis, tested

The brief suggests a missing `@Observable` or `@StateObject`. Measured across 22 Swift files:

| `@State` | `@Binding` | `@StateObject` / `@ObservedObject` / `@Observable` |
|---|---|---|
| 21 | 3 | **0** |

**There are no view models, deliberately** — state is hoisted to one owner per flow, `SignUpFlow`,
which holds ten of the twenty-one. **That is not where the bugs were.** Both iOS state bugs were at
the SwiftUI/UIKit boundary: the phone number never grouped as you typed, and ten typed digits landed
as five, because the field was bound through a `Binding` whose getter rewrote the text. While a text
field is first responder, UIKit owns its contents and SwiftUI will not reliably push a getter's
rewrite back into it. Fixed by reformatting in `onChange` instead.

The architecture held. The boundary with UIKit did not.

## 2.3 · Touch handling and frames

| `Button(...)` | `.onTapGesture` | `.contentShape(...)` | `.frame(width:)` |
|---|---|---|---|
| 21 | 1 | 7 | 41 |

The classic SwiftUI touch bug — a stack with a tap gesture and no `contentShape`, where only the
drawn pixels respond — **is not present**. All 41 fixed frames are icons, avatars and badges;
**no text container or layout region is pinned to a width.**

**A touch bug was found, on Android, by measurement**: the phone input measured 23dp inside a 56dp
row, so the field *looked* 56dp tall while only its middle third accepted a tap, on 15 of 18 sizes.
Invisible in source and in a screenshot. `ScreenFitTest` now asserts a 44pt minimum everywhere.

## 2.4 · Why Android behaved differently

Not because Compose is safer. Three reasons, in order of importance:

1. **Android was being compiled and iOS was not.** Four of the six iOS defects were iOS-only for
   that reason alone. **This asymmetry is now gone**, so it explains the backlog and predicts
   nothing about the future.
2. **Compose enforces what SwiftUI permits.** A Compose function that keeps state without `remember`
   loses it visibly on the next redraw. SwiftUI lets a view hold whatever it likes and misbehaves
   subtly.
3. **Compose has no UIKit boundary.** Both iOS state bugs were at that seam. Android has no
   equivalent seam, so no equivalent failure.

---

# 3 · Automated API and model generation

Agreed. Hand-written client models against a typed backend drift, and the drift is silent.

## 3.1 · Three backend changes come first

| Gap | Today | Needed |
|---|---|---|
| Nothing writes `openapi.json` | `createDocument` runs at startup to serve the docs only | An emit script, plus a CI step that fails if the committed spec differs |
| No route declares an `operationId` | **0 of 52** | `@ApiOperation({ operationId: 'verifyPhoneOtp' })` — otherwise clients offer `authControllerVerifyPhone(...)` |
| Untyped responses | **39 of 52** emit `void` or an untyped blob | A typed response decorator each |

Per-route detail is generated from the controllers into `docs/openapi-gaps.md`. The concentration is
`safety-admin` (9), `auth` (6) and `dates` (5) — including `POST /auth/apple`, `/auth/google` and
`/auth/refresh`, three the app depends on directly.

**Do these before generating anything.** An under-typed spec produces an under-typed client, which
then gets patched by hand — the exact problem generation removes.

## 3.2 · iOS — apple/swift-openapi-generator

Apple's own, a build plugin, so generation happens at build time and no generated code is committed.
Add the three packages (`swift-openapi-generator`, `-runtime`, `-urlsession`), then two files beside
the target: `openapi.json` and

```yaml
generate: [types, client]
accessModifier: public
```

**JWT and base URL are injected as middleware, never baked into generated code** — the property that
matters, because generated code is disposable:

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
        return try await next(request, body, baseURL)
    }
}

let client = Client(serverURL: AppEnvironment.current.apiBaseURL,
                    transport: URLSessionTransport(),
                    middlewares: [AuthMiddleware(tokens: tokenStore)])
```

The middleware is also where refresh-on-401 belongs, so no screen ever handles a token.

## 3.3 · Android — OpenAPI Generator Gradle plugin

```kotlin
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/../../openapi.json")
    outputDir.set("${layout.buildDirectory.get()}/generated/openapi")
    library.set("jvm-retrofit2")
    configOptions.set(mapOf("serializationLibrary" to "kotlinx_serialization"))
}
tasks.named("preBuild") { dependsOn("openApiGenerate") }
```

Output lives in `build/`, never committed, regenerated when the spec changes. JWT and base URL go in
an OkHttp interceptor — the mirror of the iOS middleware.

**The property being protected:** rename a field in a NestJS DTO and both apps stop compiling until
they are updated. A breaking change becomes a build error instead of a production surprise. This
only holds if the spec is regenerated and verified in CI — a stale spec is worse than none, because
it is trusted.

---

# 4 · Design system and primitives

## 4.1 · Tokens

Colour, type and gradient tokens exist in `DesignSystem.kt` / `.swift`. Raw hex still appears in
screen files — **19 in Swift, 14 in Kotlin** — but they are not all the same thing:

| | Swift | Kotlin | |
|---|---|---|---|
| Third-party brand colours | 7 | 7 | Google's four logo colours, its border grey and label black, Facebook blue |
| Everything else | 12 | 7 | illustration fills, gradient stops, a scrim |

**The brand colours must stay hardcoded.** Google and Meta specify exact values in their sign-in
branding rules; a re-themeable token is the wrong container for a value we are not allowed to change.

**The other 19 are the finding.** The eyebrow bug was exactly this shape — a colour decided inside
one screen that should have been a token with two variants, which is why fixing it on the phone
screen could not fix the tutorial cards.

**Proposed:** a `Palette` per platform, a `Space` scale (4/8/16/24/32), named type roles
(`display`, `headline`, `body`, `caption`, `label`) so no screen states a point size — and a check
that fails on raw hex outside the design system **with an allowlist for the brand values**. Worth
noting the naive version of that check, failing on every literal, would have flagged all fourteen
brand colours and been switched off within a day.

## 4.2 · The five primitives

**The button exists three times on Android**, and the duplication follows the package split:
`com.showup.welcome` and `com.showup.tutorial` each grew their own.

| Primitive | Today | Problem |
|---|---|---|
| **PrimaryButton** | `PillButton` (6 files), `NextButton`, `SunsetButton` | three implementations; `ConnectAccountScreen` imports two of them |
| **StatusBadge** | private `Eyebrow` in Connect, plus a pill inline in `TutorialShell` | twice on both platforms, and they diverged |
| **InputField** | inline in the phone screens | not extracted; the 23dp tap-target bug lived here |
| **TopBar** | inline in `VerificationFrame` | not extracted; the wrong-icon bug lived here |
| **SelectPicker** | `CountrySheet` | already shared on both — the one that is fine |

**This is not tidiness.** Three of this month's defects were in these places and two were *caused* by
the duplication.

**Order:** tokens and their checks → PrimaryButton (collapses three into one) → StatusBadge (tone as
a parameter, so the eyebrow bug cannot recur) → TopBar → InputField (44pt built in, not remembered).
Each built and previewed at 320dp and 440pt before any screen migrates onto it.

---

# 5 · Guardrails and Definition of Done

## 5.1 · One repository, not two

The brief says "both the iOS and Android repositories". **There is one**, holding the backend and
both apps, with one `CLAUDE.md` at `mobile/welcome-screen/` that is read automatically on every
request. Splitting is possible; I advise against it — the parity checks that compare Kotlin against
Swift are the only thing keeping the platforms from diverging, and they need both in one place.

## 5.2 · The rules, and what enforces each

| Rule | Enforced by |
|---|---|
| iOS 17.0 / Android API 30 minimums | build settings; `check-ios-availability.py` |
| No `@unchecked Sendable`, `nonisolated(unsafe)`, `@preconcurrency` | `check-swift-concurrency.py` |
| No mutable global state; no `DispatchQueue` in new code | `check-swift-concurrency.py` |
| Strict concurrency stays on | `check-swift-concurrency.py` |
| **No new UIKit wrapper without a written reason** | review — see below |
| **No fixed-width frame on text or layout regions** | review; `ScreenFitTest` catches the consequence |
| Tap targets ≥ 44pt | `ScreenFitTest`, 17 device sizes |
| Both platforms change together | the `verify-*.py` parity checks |
| No raw hex outside the design system | *proposed, with a brand allowlist* |

**On UIKit wrappers:** one exists and should stay. `WashHeadline` is a `UIViewRepresentable` because
the underline must be measured from laid-out text, which SwiftUI does not expose. The rule is "no
NEW wrapper without a stated reason", not a ban — a ban gets broken quietly rather than argued with.

**On MVVM:** settled as the principle rather than the pattern name — a view holds no state, hoisted
to one owner per flow, each platform in its own idiom. `@Observable` earns its place the moment a
screen owns asynchronous work; none of the five built so far do, and profile and discovery will.

## 5.3 · Definition of Done for a mobile screen

- [ ] Both apps build and test in CI — Android on `ubuntu`, iOS on `macos-15`, both required
- [ ] The screen is in `ScreenFitTest` and adds no new fit findings
- [ ] Renders correctly at **320 × 686** and **440 × 956**
- [ ] Every tappable thing is **≥ 44pt / 48dp**
- [ ] No new hardcoded colour, size or spacing
- [ ] Copy matches the ticket exactly, checked by a verifier that **quotes the ticket**
- [ ] Survives rotation, backgrounding and process death
- [ ] Loading, empty, error and offline states exist — not just the happy path
- [ ] Labelled for VoiceOver and TalkBack; usable at the largest system font
- [ ] Both platforms changed in the same commit
- [ ] All nine conformance checkers pass
- [ ] **Somebody has looked at it running, on a device**

The last item has no substitute: the wrong back icon and the wrong eyebrow tone both passed every
automated check, because both were real components correctly drawn.

---

# 6 · Backwards compatibility

**Minimums: iOS 17.0 and Android API 30**, as built. Note the brief says "Android 14+" in area 4 and
"Android API 30" in the compatibility section — those are three generations apart (Android 14 is API
34; API 30 is Android 11). We built API 30, the more inclusive of the two. **Worth confirming which
was meant**, because moving to API 34 would drop a substantial share of active Android devices.

`ScreenFitTest` runs every screen state across **17 device sizes** on every pull request.

| | Size | Why |
|---|---|---|
| **Narrowest** | 320 × 686 | Galaxy Fold cover screen — the hardest layout in the set |
| iOS floor | 375 × 667 | iPhone SE (3rd gen) — the smallest phone running iOS 17 |
| | 360 × 640 | 4.7-inch Android |
| | 412 × 915 | Pixel 8 Pro |
| **Largest** | 440 × 956 | iPhone 16 Pro Max |

**The brief names the iPhone SE as the smallest screen, and it is not.** The narrowest in the matrix
is the **Galaxy Fold cover screen at 320dp** — 55 points narrower, on a device that runs API 30
perfectly well. A layout that fits the SE can still break there, so the SE is the iOS floor rather
than the floor.

**Measured today:** clipped text, elements outside the safe area, text collapsed to nothing, tap
targets below 44pt. Insets are modelled per device, so "fits" means "fits where the user can see it".

**Not yet measured, and named in the brief:**

- **Keyboard overlap** — the phone and code screens are at risk, and the smallest screen is where it
  bites. Needs the keyboard modelled as another inset; a contained addition to the existing harness.
- **Largest system font** — everything is measured at the default text size. Accessibility sizes are
  where "no scroll" requirements break, and this is an EU legal requirement under the Accessibility
  Act since June 2025.
- **Landscape** — support it or lock it, but decide.

---

# 7 · Open questions

1. **Android minimum — API 30 or Android 14?** The brief says both. We built API 30.
2. **Primitives: retrofit or forward-only?** Reworking five working screens and re-verifying them,
   or applying it to new work and migrating gradually. Meaningfully different amounts of work.
3. **Are the backend OpenAPI changes in scope for us?** Section 3.1 is all backend edits.
4. **Does a returning user see the Connect screen?** We assumed not; no ticket says either way.

## Suggested order

iOS in CI would have led this list and is already done. What is left:

1. The backend OpenAPI gaps — pure backend, unblocks both clients
2. A generated client on Android, proving the pipeline end to end
3. Tokens, the type scale, and the checks that enforce them
4. The five primitives, previewed at 320dp and 440pt
5. Keyboard overlap and Dynamic Type in the fit harness

## Current state

| | |
|---|---|
| Conformance checks | 868 named assertions across 6 checkers, plus 77 call sites and a 17-API availability list |
| Tests | 45 Android, 32 Swift |
| Device sizes per run | 17 |
| CI | both apps built and tested on every pull request; Android on `ubuntu`, iOS on `macos-15` |

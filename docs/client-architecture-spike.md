# Client Architecture Spike

2 September 2026 · second draft · tied to the files in this repository, not to general practice

**What changed since the first draft.** It opened by saying the most important section could not be
written, because nobody had ever run the iOS app. That is no longer true: it was built and run on a
Mac and an iPhone on 1 September. Ten things had to be fixed before it would compile, and running it
produced four real bugs. Section 1 is now an account of observed failures rather than an inspection
of source.

That single change is also the answer to the question underneath the brief, so it comes first.

---

# 0 · Where building native apps with Claude actually goes wrong

This is the part the brief is really asking, and it deserves evidence rather than opinion. Every
failure mode below happened here, in the last week, with a named example. Each one has a
countermeasure, and most are already running.

## 0.1 · The dominant failure: it cannot see what it made

**Six defects survived every form of review except somebody looking at the screen.**

The back control was drawn as an arrow where the design says chevron. The eyebrow pill used the
lavender variant on screens the design paints orange. The underline wash covered 56% of its phrase
instead of 78%. The glow under a button fell to the left. On iOS, a button jumped 56 points when
you mistyped a code, the phone number never grouped as you typed it, and typing ten digits landed
five.

**None of these are careless.** Every one is a real component from the design system, correctly
built, in the wrong place or with a property nobody could observe. Reading the code cannot find
them, because the code says what it intends and the intention was wrong.

**Countermeasure, and it is the only one that matters: build it and run it, on both platforms.**
Everything else in this document is a way of narrowing what has to be caught by eye. Nothing
replaces the eye.

Status: **closed on both platforms, as of 1 September.** Android has built, tested and
layout-measured on every pull request for a while. iOS now does too — Eman added a `macos-15` job
the same day he first ran it, and it has been green on `development` since. Both apps are compiled,
tested and measured before anything merges.

## 0.2 · Confident, wrong reasoning when it cannot verify

When Claude cannot run something, it will still reason — and the reasoning is fluent whether or not
it is right.

**Example.** CI found error text clipped on narrow phones. Unable to see it, I proposed three fixes
and argued for the least-bad: shorten the message, or move the button, or change the scrolling. All
three were wrong and two were expensive. The actual cause was a single line — `height` applied
before `padding`, so the reserved space contained its own padding — found in minutes by someone who
could run it.

**Countermeasure:** treat any conclusion reached without execution as a hypothesis. In practice this
means asking for the measurement rather than accepting the argument, particularly when the answer
sounds tidy.

## 0.3 · Copy taken from the wrong part of a ticket

**Three times this week, from two different people**, which makes it a property of the tickets
rather than of carelessness.

| What | Where it was actually taken from |
|---|---|
| Seven phone-error messages | the **Tracking** section, where they are analytics `reason` labels |
| The lavender eyebrow tone | a **reference render** the spec text itself flags as wrong |
| "Register and date now" | a **user story** — *"so that I can register and date now"* — and the PNG |

Every ticket scatters copy across user stories, acceptance criteria, tracking notes, reference
files and a PNG. Only SHOWUP-140 states which wins:

> The reference file wins on numbers. **The ticket wins on behaviour, scope, and copy. The PNG wins
> on nothing.**

**Countermeasure, and this one is yours rather than ours:** one clearly-marked copy block per
ticket, and that precedence rule stated on every ticket rather than just on 140. On our side, the
conformance checks now quote the ticket rather than the code — see 0.5.

## 0.4 · Inventing behaviour where the spec is silent

SHOWUP-146 says a returning user should not see the tutorial. It says nothing about whether they
see the Connect screen. We built them skipping both.

The inference was reasonable — SHOWUP-144 describes Connect as following phone verification "so
that I can continue sign-up", and its copy says *"Skip and continue to profile"* and *"Let's finish
your profile in 90 seconds"*, neither of which fits somebody who has a profile. But **no ticket says
it**, and the first anyone outside the work knew of it was when the screen appeared to have
vanished.

**Countermeasure:** an inference is recorded as an inference, in a place the product side reads —
not only in a code comment. `audit/CONFLICTS-2026-08-27.md` is that place, and A9 and A10 are
examples of it working.

## 0.5 · Automated checks that pin the mistake

A conformance check written from the code protects the code. Three times a check asserted something
that turned out to be wrong, and then blocked the fix:

- it asserted the per-card underline widths — the hand-tuned numbers that were the bug
- it asserted the seven invented error strings
- it asserted `.shadow(` on Android, the modifier that could not express the design's shadow

**Countermeasure:** checks quote the ticket, and say so. Where a check deviates deliberately, the
reason is written beside it. The CTA label check now carries the precedence rule verbatim, because
that label has already been changed away and back once.

## 0.6 · Tests that exist without running

Eight Swift tests sat in the repository for a week **in no test target**. They were written,
reviewed, committed and counted — and never executed once. In Eman's words, indistinguishable from
having no tests.

**Countermeasure:** CI runs them, and "present in the repo" is never reported as "passing". The
project file is generated by a script that now discovers the test folder the same way it discovers
sources, so a new test file cannot go missing the same way.

## 0.7 · One machine is not enough

An error message fit on Windows and was cut off on the Linux CI runner — the same commit, the same
device sizes. The two lay text out fractionally differently and that message sat exactly on the
boundary between two lines and three.

**Whether a user saw the whole message depended on their phone.** Nothing about the code was
marginal-looking; it looked fine everywhere it had been checked.

**Countermeasure:** already in place. CI builds on Linux while development happens on Windows, and
the disagreement between them is the point, not a nuisance.

## 0.8 · What this adds up to

**The bottleneck is not Claude's code. It is the loop between writing and seeing.**

Where that loop was closed — Android, since CI landed — defects were caught in minutes by machines.
Where it was open — iOS, for weeks — they accumulated silently and then arrived all at once, ten
build errors and four runtime bugs in a single afternoon.

**As of 1 September the loop is closed on both.** That is the single most important change in this
document, and it had already happened before this document was written.

Everything in the rest of this document is either closing that loop further or narrowing what has
to fall through it.

---

# 1 · Code-level analysis, iOS

## 1.1 · What the first run on a device found

Ten build errors, then four behavioural bugs. The build errors are ordinary and not interesting —
stale preview signatures, a positional-argument order, a `private` that should not have been, a
dictionary typed `[String: Any]` where strict concurrency needs `[String: any Sendable]`.

The four behavioural bugs are the ones the brief is asking about, and they map exactly onto the
categories it names.

### The CTA moved — 56 points, on the screen where the ticket forbids it

**`PhoneVerificationView`, states C/D.** The error card was wrapped in a bare `if mismatch { … }`.

In SwiftUI, an `if` with no `else` inside a `ViewBuilder` produces **nil**, and a frame and padding
applied around nil both collapse to nothing. So the region intended to reserve 42 points reserved
zero, and the button jumped the full 56 when an error appeared.

SHOWUP-143 forbids this outright — *"The CTA does not move between the default and error state"* —
and that reserved region exists for no other purpose. **Fixed** by always rendering the card and
hiding it with `opacity`, which reserves the space whether or not it is visible. Now 0.0pt.

**Android did not have this bug**, and the reason is instructive: a Compose `Box` lays out at its
minimum height whether or not its content is emitted. The same mistake is invisible on one platform
and severe on the other.

**Android did have the sibling bug** on states A/B — one line reserved for a message that always
takes two, so the CTA dropped 15.7pt. Its comment claimed the message would "grow downward into the
spacer"; the spacer is a fixed 22dp, so it did not. Also fixed.

### The phone number never grouped

Typing `2015550123` left `2015550123`, with no spaces, at any typing speed. The formatting feature
simply never worked on iOS.

**Cause:** the field was bound through a `Binding` whose getter reformatted the text. While a text
field is first responder, UIKit owns its contents, and SwiftUI will not reliably push a getter's
rewrite back into it.

This is precisely the category the brief names — *"local state mutation vs. two-way `@Binding`"* —
and it is worth noting that it is invisible in source. The binding looks correct. It is correct, in
the sense of compiling and being well-formed. It just does not do anything.

### Typing lost characters

Worse, and found in the same place: when the rewrite *did* land, it clobbered keystrokes still in
flight. **Ten digits typed, five arrived.**

**Fixed** by reformatting in `onChange` rather than in the binding's getter, and verified across
three trials at human typing speed, plus backspace, mid-string insertion, and changing country with
digits already entered.

## 1.2 · State handling — the brief's hypothesis, tested

The brief asks where state is handled incorrectly, suggesting a missing `@Observable` or
`@StateObject`. Measured across the 22 Swift files in the app target:

| | count |
|---|---|
| `@State` | 21 |
| `@Binding` | 3 |
| `@StateObject` / `@ObservedObject` / `@Observable` | **0** |

**There are no view models, deliberately.** Ten of the twenty-one `@State` declarations live in one
file, `SignUpFlow.swift`, which owns the whole flow. `StartupView` and `WelcomeBackView` hold none
at all.

**That is not where the bugs were.** Both iOS state bugs were in the *binding* between SwiftUI and
UIKit, not in the ownership model. The architecture held; the boundary with UIKit did not.

**Settled 1 September:** the rule is the principle, not the pattern name — a view holds no state,
hoisted to one owner per flow, each platform in its own idiom. `@Observable` earns its place the
moment a screen owns asynchronous work of its own; none of the five built so far do, and the
profile and discovery screens will.

## 1.3 · Touch handling

| | count |
|---|---|
| `Button(...)` | 21 |
| `.onTapGesture` | 1 |
| `.contentShape(...)` | 7 |

The classic SwiftUI touch bug — a stack with a tap gesture and no `contentShape`, where only the
drawn pixels respond and the gaps silently do not — **is not present**.

**But a touch bug was found, on Android**, by measurement rather than by reading: the phone input
measured 23dp inside a 56dp row and the row centred it, so the field *looked* 56dp tall while only
its middle third accepted a tap. Fifteen of eighteen device sizes.

That is the shape of touch bug that actually occurs here: not a gesture conflict, but a hit area
that does not match what is drawn. **It is invisible in source and invisible in a screenshot.** Only
measurement finds it, which is why `ScreenFitTest` now asserts a 44pt minimum on every tappable
thing.

## 1.4 · Fixed frames

41 `.frame(width:)` calls, all icons, avatars, badges and rings — 20×20, 56×56, 84×84, 104×104,
120×120. Fixed-size shapes that are meant to be fixed-size. **No text container or layout region is
pinned to a width.** The rule is worth writing down, and the code already keeps it.

## 1.5 · Why Android behaved differently

Not because Compose is safer. Three specific reasons, in order of importance:

**1. Android was being compiled and iOS was not.** For weeks, every Android change was built,
tested and layout-measured, while the Swift was read by static checkers that cannot type-check, lay
out or render. Four of the six iOS defects were iOS-only for that reason alone — not because the
Swift was written less carefully, but because nothing was checking it.

**This asymmetry is now gone**, which means it explains the backlog of defects found on 1 September
and predicts nothing about the future.

**2. Compose enforces what SwiftUI permits.** A Compose function that keeps state without `remember`
loses it on the next redraw, immediately and visibly. SwiftUI lets a view hold whatever it likes and
misbehaves subtly. Our Swift follows the good pattern by discipline; the Kotlin follows it because
the framework insists.

**3. Compose has no UIKit boundary.** Both iOS state bugs were at the SwiftUI/UIKit seam. Compose
has no equivalent seam in this code, so it has no equivalent failure.

Two of those three are about the environment, not the language.

---

# 2 · Automated API and model generation

Agreed without reservation. Hand-written client models against a typed backend drift, and the drift
is silent.

## 2.1 · Three things must change on the backend first

**1. Nothing writes `openapi.json`.** `SwaggerModule.createDocument` runs at startup to serve the
interactive docs (`src/main.ts:60`); the document is never written to a file, so no build tool can
read it.

```ts
// scripts/emit-openapi.ts
const app = await NestFactory.create(AppModule, { logger: false });
writeFileSync('openapi.json', JSON.stringify(SwaggerModule.createDocument(app, swaggerConfig), null, 2));
await app.close();
```

Then `npm run openapi:emit`, and a CI step that fails if the committed file differs from the
generated one — so the spec cannot drift from the code.

**2. No route declares an `operationId`.** Zero, across 52 routes. NestJS then invents one from the
controller and method name, and a generated client offers `authControllerVerifyPhone(...)` rather
than `verifyPhoneOtp(...)`. One decorator each:

```ts
@ApiOperation({ operationId: 'verifyPhoneOtp' })
```

**3. 39 of 52 routes have no typed response.** They emit `void` or an untyped blob into both
clients, losing every field. The full per-file list is generated from the controllers into
`docs/openapi-gaps.md`; the concentration is `safety-admin` (9), `dates` (5) and `auth` (6) —
including `POST /auth/apple`, `/auth/google` and `/auth/refresh`, three the app depends on directly.

**Do these before generating anything.** Generating from an under-typed spec produces an
under-typed client, which then gets patched by hand — the exact problem generation is meant to
remove.

## 2.2 · iOS — apple/swift-openapi-generator

Apple's own, a build plugin, so generation happens at build time and no generated code is committed.

```swift
dependencies: [
    .package(url: "https://github.com/apple/swift-openapi-generator", from: "1.0.0"),
    .package(url: "https://github.com/apple/swift-openapi-runtime", from: "1.0.0"),
    .package(url: "https://github.com/apple/swift-openapi-urlsession", from: "1.0.0"),
],
```

Two files beside the target — `openapi.json` and `openapi-generator-config.yaml`:

```yaml
generate: [types, client]
accessModifier: public
```

**JWT and base URL are injected as middleware, never baked into generated code** — which is the
property that matters, because the generated code is disposable:

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

## 2.3 · Android — OpenAPI Generator Gradle plugin

```kotlin
plugins { id("org.openapi.generator") version "7.x" }

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/../../openapi.json")
    outputDir.set("${layout.buildDirectory.get()}/generated/openapi")
    library.set("jvm-retrofit2")
    configOptions.set(mapOf(
        "serializationLibrary" to "kotlinx_serialization",
        "dateLibrary" to "kotlinx-datetime",
    ))
}
tasks.named("preBuild") { dependsOn("openApiGenerate") }
```

Output lives in `build/`, is never committed, and regenerates when the spec changes. JWT and base
URL go in an OkHttp interceptor — the exact mirror of the iOS middleware.

## 2.4 · The property being protected

**One spec, generated from the backend, feeding two generated clients.** Rename a field in a NestJS
DTO and both apps stop compiling until they are updated. **A breaking change becomes a build error
instead of a production surprise.**

It only holds if `openapi.json` is regenerated and verified in CI. A stale spec is worse than none,
because it is trusted.

---

# 3 · Design system and primitives

## 3.1 · Tokens exist and are not used everywhere

`DesignSystem.kt` and `DesignSystem.swift` hold the colour, type and gradient tokens, and
`verify-welcome.py` checks the important ones on both platforms. But raw hex still appears in screen
files: **19 in Swift, 14 in Kotlin.**

**They are not all the same kind of thing, and the difference decides what to do about them:**

| | Swift | Kotlin | |
|---|---|---|---|
| Third-party brand colours | 7 | 7 | Google's four logo colours, its border grey and label black, Facebook blue |
| Everything else | 12 | 7 | illustration fills, gradient stops, a scrim |

**The brand colours must stay hardcoded.** Google and Meta specify exact values in their sign-in
branding rules, and a design-system token that could be re-themed is precisely the wrong container
for a value we are contractually not allowed to change. Tokenising them would be an error.

**The other 19 are the real finding**, and they are not the harmless illustration details they look
like: **the eyebrow bug was exactly this shape** — a colour decision made inside one screen that
should have been a named token with two variants, which is why fixing it on the phone screen could
not fix it on the tutorial cards.

**Proposed:** a `Palette` per platform for the illustration colours, and a check that fails on
`Color(0x` / `Color(hex:` outside the design-system files **with an explicit allowlist for the
brand values**, each carrying the rule that mandates it. Note that the naive version of this check —
the one I first proposed here, failing on every literal — would have flagged all fourteen brand
colours and been switched off within a day. That is the section 0.5 failure mode arriving in a
document about avoiding it.

**Spacing** is currently raw dp at call sites; the handoff uses a consistent 4-point scale:

```kotlin
object Space { val xs = 4.dp; val s = 8.dp; val m = 16.dp; val l = 24.dp; val xl = 32.dp }
```

**Typography** should be named roles — `Type.display`, `.headline`, `.body`, `.caption`, `.label` —
so a screen never states a point size. The values are in `colors_and_type.css` and can be lifted
directly.

## 3.2 · The five primitives — and the finding that matters

**The button exists three times on Android, and the duplication follows the package split** —
`com.showup.welcome` and `com.showup.tutorial` were built as separate worlds and each grew its own
version of the same thing.

| Primitive | Android today | iOS today | Problem |
|---|---|---|---|
| **PrimaryButton** | `PillButton` (welcome, used by 6 files), `NextButton` (tutorial), `SunsetButton` (tutorial) | `NextButton`, `SunsetButton`, and the phone CTA built inline | **three implementations** on Android, and `ConnectAccountScreen` imports two of them |
| **StatusBadge** | `Eyebrow` private in `ConnectAccountScreen`, plus the pill drawn inline in `TutorialShell` | `Eyebrow` private in `ConnectAccountView`, `EyebrowPill` in `TutorialShell` | twice on both platforms, and they diverged |
| **InputField** | inline in `PhoneVerificationScreen` | inline in `PhoneVerificationView` | not extracted; the 23dp tap-target bug lived here |
| **TopBar** | inline in `VerificationFrame` | inline | not extracted; the wrong-icon bug lived here |
| **SelectPicker** | `CountrySheet` | `CountrySheet` | already shared on both — the one that is fine |

`ConnectAccountScreen` using both `PillButton` and `NextButton` is the clearest symptom: one screen,
two different implementations of the same control, because it sits on the seam between the two
packages.

**This is not tidiness.** Three of this week's defects were in exactly these places and two were
*caused* by the duplication: the wordmark on tutorial card 1 had drifted three ways from the shared
one, and the eyebrow exists twice so fixing the phone screen could not fix the tutorial.

**Order of work:**

1. `Space`, `Type`, the illustration palette, and the check that enforces them
2. **PrimaryButton** — collapses three Android implementations into one
3. **StatusBadge** — collapses two, tone as a parameter, so the eyebrow bug cannot recur
4. **TopBar** — one back control, one place
5. **InputField** — with the 44pt minimum built in rather than remembered
6. **SelectPicker** — effectively done; formalise the interface

Each is built and tested in isolation **at the smallest and largest supported size** before any
screen migrates onto it.

---

# 4 · Guardrails and Definition of Done

## 4.1 · One repository, not two

The brief says "both the iOS and Android repositories". **There is one**, with the backend and both
apps on it. Splitting is possible; I would advise against it. The parity checks that compare Kotlin
against Swift are the only thing that has kept the two platforms from diverging, and they need both
in one place.

`mobile/welcome-screen/CLAUDE.md` exists and is read automatically on every request. It covers where
state lives, Swift 6 concurrency, iOS availability, both-platforms-together, design tokens, layout,
verification and house style.

## 4.2 · Rules, and what enforces each

**Minimum OS — done.** iOS 17.0, Android API 30. It *deleted* two workarounds rather than moving
them: an availability fork around `scrollBounceBehavior`, and a deprecated single-parameter
`onChange` that was about to become the next warning.

| Rule | Enforced by |
|---|---|
| No `@unchecked Sendable` / `nonisolated(unsafe)` / `@preconcurrency` | **check-swift-concurrency.py** |
| No mutable global state | **check-swift-concurrency.py** |
| No `DispatchQueue` in new code | **check-swift-concurrency.py** |
| Strict concurrency stays on | **check-swift-concurrency.py**, both generator and project file |
| No API newer than the deployment target | **check-ios-availability.py** |
| Both platforms change together | **verify-*.py** parity checks |
| Tap targets ≥ 44pt | **ScreenFitTest**, 17 device sizes |
| No raw hex outside the design system | *proposed, ~10 lines* |
| No point sizes outside the type scale | *proposed* |
| No new UIKit wrappers without a written reason | review |

**On UIKit wrappers:** one exception exists and should stay. `WashHeadline` is a
`UIViewRepresentable` because the underline has to be measured from laid-out text, which SwiftUI
does not expose. The rule should be "no NEW wrapper without a stated reason", not a ban — a ban gets
broken quietly rather than argued with.

**Architecture — settled.** The principle, not the pattern name: a view holds no state. See 1.2.

## 4.3 · Definition of Done for a mobile screen

- [ ] **Both apps build and test in CI** — Android on `ubuntu`, iOS on `macos-15`; both required
- [ ] Unit tests pass, and the screen is in `ScreenFitTest`
- [ ] Renders correctly at **375×667** and **440×956**, with no new fit findings
- [ ] Every tappable thing is **≥ 44pt / 48dp**
- [ ] No new hardcoded colour, size or spacing
- [ ] Copy matches the ticket exactly, checked by a verifier that **quotes the ticket**
- [ ] Survives rotation, backgrounding and process death
- [ ] Loading, empty, error and offline states exist — not just the happy path
- [ ] Labelled for VoiceOver and TalkBack; usable at the largest system font
- [ ] Both platforms changed **in the same commit**
- [ ] All nine conformance checkers pass
- [ ] **Somebody has looked at it running, on a device**

The last item has no substitute. The wrong back icon and the wrong eyebrow tone both passed every
automated check, because both were real components from the design system, correctly drawn.

---

# 5 · Backwards compatibility

Minimums are **iOS 17.0** and **Android API 30**. `ScreenFitTest` runs every screen state across
**17 device sizes** on every pull request.

| | Size | Why |
|---|---|---|
| **Narrowest** | 320 × 686 | Galaxy Fold cover screen — the hardest layout in the set |
| iOS floor | 375 × 667 | iPhone SE (3rd gen) — the smallest phone that runs iOS 17 |
| | 360 × 640 | 4.7-inch Android, the most common small Android |
| | 412 × 915 | Pixel 8 Pro |
| | 430 × 932 | iPhone 15/16 Pro Max, Dynamic Island |
| **Largest** | 440 × 956 | iPhone 16 Pro Max |

Worth being precise about the floor, because the brief names the iPhone SE and it is *not* the
hardest case. The narrowest screen in the matrix is the **Galaxy Fold cover screen at 320dp** — 55
points narrower than the SE, on a device that runs API 30 perfectly well. A layout that fits the SE
can still break there, so the SE is the iOS floor rather than the floor.

The 320 × 568 iPhone SE (1st gen) was dropped when the minimum moved to iOS 17, which it cannot
run. Testing a phone the app will not install on produces findings nobody can act on — but note
that dropping it did not make 320dp go away, because the Fold still sits there.

**Measured today:** clipped text, elements outside the safe area, text collapsed to nothing, tap
targets below 44pt. Insets are modelled per device, so "fits" means "fits where the user can see
it", not "fits the rectangle".

**Not yet measured, and named in the brief:**

- **Keyboard overlap.** The phone and code screens are the ones at risk and the smallest screen is
  where it bites. Needs the keyboard modelled as another inset — a contained addition to the
  existing harness.
- **Largest system font.** Everything is measured at the default text size today. Accessibility
  sizes are where "no scroll" requirements break, and this is a legal requirement in the EU under
  the Accessibility Act since June 2025.
- **Landscape.** Support it or lock it, but decide.

---

# 6 · Open questions

Three of the five from the first draft are answered. These remain:

1. **Primitives: retrofit or forward-only?** Extracting the five means reworking five screens that
   currently work and re-verifying them, or applying it to new work and migrating gradually.
   Meaningfully different amounts of work.
2. **Are backend changes in scope for us?** Section 2's three gaps are backend edits — the emit
   script, 52 `operationId`s, 39 response decorators.
3. **Does a returning user see the Connect screen?** See 0.4. We assumed not; no ticket says.

## Suggested order

**The item that would have led this list is already done.** iOS in CI was going to be number one —
it closes the loop section 0 is about — and Eman shipped it on 1 September, before this document
existed. Worth reading the job itself rather than just noting it exists: it regenerates
`project.pbxproj` and fails if the committed one is stale, which is precisely what let eight Swift
tests sit in no target for a week, and it sets `pipefail` so a red suite cannot go green through
`xcbeautify`. Both are the failure modes in 0.6 being designed out rather than remembered.

So, what is actually left:

1. The backend OpenAPI gaps — pure backend, unblocks both clients, nothing depends on it first
2. A generated client on Android, proving the pipeline end to end before iOS follows
3. `Space`, `Type`, the palette, and the checks that enforce them — with the brand allowlist from 3.1
4. The five primitives, with previews at 320dp and 440pt
5. Keyboard overlap and Dynamic Type in the fit harness — the two named gaps in section 5

## Current state, for reference

| | |
|---|---|
| Automated conformance checks | **868** named assertions across 6 checkers, plus 77 call sites checked for argument order and a 17-API availability list |
| Android unit tests | **45** |
| Swift tests | **32** |
| Device sizes measured per run | **17** |
| CI | both apps built and tested on every pull request — Android on `ubuntu`, iOS on `macos-15`, both required |

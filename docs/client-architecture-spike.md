# Client Architecture Spike

1 September 2026 · tied to the files in this repository, not to general practice

Four areas, as requested. Everything below is measured from the code as it stands at commit
`main`, and where something could not be measured it says so rather than filling the gap.

---

## Before area 1: what this document can and cannot claim

The brief asks why "our buttons, selectors, and dynamic layouts are failing" on iOS.

**Nobody here has seen the iOS app run.** There is no Mac in this environment, so the Swift has
never been compiled, launched or displayed. Every defect found this week was either on Android, or
was found by reading the design handoff and comparing it to the source.

So area 1 below is a **line-by-line inspection of the Swift**, which is real work with real
findings — but it is not a diagnosis of failures anybody has observed. Where the inspection finds
nothing, that means *nothing is visible in the source*, not that the screen behaves correctly.

The two are genuinely different. A button that does not respond to a tap usually looks perfect in
the code; it is the running app that tells you. **To analyse observed failures I need the
observations** — which screens, which controls, what happened, on which device and iOS version, and
a screenshot or screen recording. With those I can work backwards. Without them, any list of causes
would be plausible-sounding invention, which is worse than an empty section.

---

# 1 · Code-level analysis, iOS

## 1.1 · What the inspection actually found

Twenty-one Swift files, read for the three failure classes named in the brief.

### State handling — the hypothesis does not match the code

The brief asks where state is handled incorrectly, suggesting missing `@Observable` or
`@StateObject`. The measurement:

| | count |
|---|---|
| `@State` | 21 |
| `@Binding` | 2 |
| `@StateObject` / `@ObservedObject` / `@Observable` | **0** |

**There are no view models at all, and that is deliberate rather than an omission.** Ten of the
twenty-one `@State` declarations are in one file, `SignUpFlow.swift`, which owns the entire flow:
which step, the country, the digits typed, the error, the cooldown, the remembered account. The
screens themselves are almost pure:

| Screen | own `@State` |
|---|---|
| `StartupView` | **0** |
| `WelcomeBackView` | **0** |
| `PhoneVerificationView` | 1 |
| `ConnectAccountView` | 2 |

A screen takes values and returns a picture. That is the Compose pattern ported to SwiftUI, and it
is the reason 45 automated tests can render every screen in every state without a device.

**Where this becomes a real limitation:** it holds while screens are pure display. The moment a
screen owns asynchronous work of its own — loading a profile, uploading a photo, retrying a failed
request — hoisted `@State` stops being enough, because that work needs to survive view redraws and
be cancellable. That is the point at which `@Observable` earns its place. **None of the five screens
built so far are at that point. The profile and discovery screens will be.**

### Touch handling — no defect found in the source

| | count |
|---|---|
| `Button(...)` | 21 |
| `.onTapGesture` | **1** |
| `.contentShape(...)` | 7 |

The classic SwiftUI touch bug is a stack with `.onTapGesture` and no `contentShape`, where only the
drawn pixels respond and the gaps between them silently do not. **That pattern is not present.**
Nearly everything is a real `Button`, and the seven `contentShape` calls are doing exactly the job
they exist for. The single `onTapGesture` (`PhoneVerificationView.swift:260`) puts focus in the code
field, which is a legitimate use.

There is no evidence of gesture conflict in the source. **That is not the same as saying taps work
on a device** — see the preface.

### Fixed frames — 41, and they are the right kind

The brief forbids hardcoded fixed-width frames. There are 41 `.frame(width:)` calls. Reading them:
they are icons, avatars, badges, the linking ring, the success circle — 20×20, 56×56, 84×84,
104×104, 120×120. **Fixed-size shapes that are supposed to be fixed-size.** No text container and
no layout region is pinned to a fixed width.

So the rule is worth writing down, and the code already keeps it.

## 1.2 · The iOS defects that WERE found, all this week, all now fixed

Found by reading and by automated checks, not by running:

| Defect | Nature | Status |
|---|---|---|
| `scrollBounceBehavior` used below its minimum OS | Valid Swift, wrong OS floor | fixed, then deleted entirely when the floor moved to 17 |
| Single-parameter `onChange` (iOS 16 form) | Deprecated from iOS 17 | fixed |
| Flag cache as unisolated shared mutable state | Swift 6 rejects it outright | fixed |
| Shake animation scheduled through `DispatchQueue` | Swift 6 will not allow it near view state | fixed, wants one look on a Mac |
| Back control drawn as `arrow-left` | Wrong icon from the design's own set | fixed |
| Eyebrow pill using the lavender tone | Wrong variant for these screens | fixed |

The last two are worth noting because **no test could have caught them.** Both were real, correctly
drawn components from the design system — just the wrong ones. Only comparing the running screen to
the reference finds that, which is what happened.

## 1.3 · Contrast with Compose, and why Android behaved differently

Both platforms use the same architecture, so the honest answer is **not** "Compose is better here".
The differences that matter are elsewhere.

**Compose enforces the pattern; SwiftUI permits it.** A Compose function that tries to keep state
without `remember` loses it on the next redraw, immediately and visibly. SwiftUI lets a view hold
whatever it likes and only misbehaves subtly. Our Swift follows the good pattern by discipline; the
Kotlin follows it because the framework insists.

**Android has a compiler in CI. iOS has never been compiled at all.** This is the whole difference,
and it is not about the languages. Every Android change is built, tested at 17 phone sizes and
checked against the design on every pull request. The Swift is read by static checkers that cannot
type-check, cannot lay out, and cannot render. **Four of the six defects above were iOS-only for
exactly one reason: nothing was checking.**

**Kotlin's null safety versus Swift's optionals** are equivalent; neither caused a defect here.

**The conclusion is uncomfortable but simple: Android did not behave differently because Compose is
safer. It behaved differently because it was being checked.**

---

# 2 · Automated API and model generation

Agreed without reservation: hand-written client models against a typed backend are a standing
invitation to drift. The backend is a NestJS app with Swagger already configured.

## 2.1 · What has to change on the backend first

Three concrete gaps, all measured:

**1. Nothing writes `openapi.json`.** `SwaggerModule.createDocument` runs at startup to serve the
interactive docs (`src/main.ts:60`), but the document is never written to a file, so no build tool
can read it. Needs a small script:

```ts
// scripts/emit-openapi.ts
const app = await NestFactory.create(AppModule, { logger: false });
const doc = SwaggerModule.createDocument(app, swaggerConfig);
writeFileSync('openapi.json', JSON.stringify(doc, null, 2));
await app.close();
```

Then `npm run openapi:emit`, and CI fails if the committed file differs from the generated one — so
the spec can never drift from the code.

**2. No route declares an `operationId`.** Zero, across 52 routes. NestJS then invents one from the
controller and method name, and generated clients turn those into method names like
`authControllerVerifyPhone(...)`. Every route needs an explicit id:

```ts
@ApiOperation({ operationId: 'verifyPhoneOtp' })
```

That single line is the difference between `client.verifyPhoneOtp(...)` and something nobody wants
to type.

**3. 39 of 52 routes have no typed response.** They carry no `@ApiOkResponse`, so the generator
emits `void` or an untyped blob and the client loses every field. The full list is in
`docs/openapi-gaps.md`; the concentration is in `safety-admin` (9), `dates` (5) and `auth` (6),
including `POST /auth/apple`, `POST /auth/google` and `POST /auth/refresh` — three the app depends
on directly.

**None of this is difficult.** It is one decorator per route, and it should be done before any
client generation, because generating from an under-typed spec produces an under-typed client that
then gets papered over by hand — the exact problem this is meant to solve.

## 2.2 · iOS: apple/swift-openapi-generator

Apple's own generator, a build plugin, so generation happens at build time and no generated code is
committed.

Add to `Package.swift` (or the Xcode target's package dependencies):

```swift
dependencies: [
    .package(url: "https://github.com/apple/swift-openapi-generator", from: "1.0.0"),
    .package(url: "https://github.com/apple/swift-openapi-runtime", from: "1.0.0"),
    .package(url: "https://github.com/apple/swift-openapi-urlsession", from: "1.0.0"),
],
```

Two files beside the target: `openapi.yaml` (or `.json`) and `openapi-generator-config.yaml`:

```yaml
generate: [types, client]
accessModifier: public
```

**Authentication and base URL** are injected as a middleware, not baked into the generated code —
which is the property that matters, because the generated code is disposable:

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

let client = Client(
    serverURL: AppEnvironment.current.apiBaseURL,
    transport: URLSessionTransport(),
    middlewares: [AuthMiddleware(tokens: tokenStore)]
)
```

The middleware is also the right place for the refresh-on-401 retry, so no screen ever thinks about
tokens.

## 2.3 · Android: OpenAPI Generator Gradle plugin

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

Output goes to `build/`, never committed, and regenerates whenever the spec changes.

JWT and base URL go in an OkHttp interceptor, the exact mirror of the iOS middleware:

```kotlin
class AuthInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.accessToken ?: return chain.proceed(chain.request())
        return chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }
}
```

## 2.4 · The property worth protecting

**One spec, two generated clients, and the spec is generated from the backend.** A field renamed in
a NestJS DTO changes `openapi.json`, which changes both clients, and both apps fail to compile until
they are updated. That is the whole value: a breaking change becomes a build error instead of a
runtime surprise in production.

It only holds if `openapi.json` is regenerated and checked in CI. Otherwise it drifts like anything
else, and a stale spec is worse than none because it is trusted.

---

# 3 · Design system and primitives

## 3.1 · Tokens exist. They are not being used everywhere.

`DesignSystem.kt` and `DesignSystem.swift` already hold the colour, type and gradient tokens, and
`verify-welcome.py` checks the important ones on both platforms.

But raw hex still appears in screen files:

| | hardcoded hex outside the design system |
|---|---|
| Swift | **69** |
| Kotlin | **18** |

Examples: the heart's gradient stops in `WelcomeScreen.kt:129`, an accent circle at line 145, the
sparkle at 161, the handoff scrim in `ConnectAccountScreen.kt:190`, gradient stops in
`TutorialShell.kt:251`.

Most are illustration colours — a heart, a sparkle — which feels like a reasonable exception and is
not. **The eyebrow bug this week was exactly this shape**: a colour decision made in one screen that
should have been a named token with two variants. The rule that would have prevented it is *every*
colour is named, including the ones that only appear once.

**Proposed:** a `Palette` extension per platform holding the illustration colours, and a checker
that fails on `Color(0x` or `Color(hex:` anywhere outside the design system files. That check is
about ten lines and would have caught two of this week's defects.

### Spacing and typography

Spacing is currently raw `dp`/points at call sites. The handoff uses a consistent 4-point scale, so:

```kotlin
object Space { val xs = 4.dp; val s = 8.dp; val m = 16.dp; val l = 24.dp; val xl = 32.dp }
```

Typography should be named roles rather than sizes — `Type.display`, `Type.headline`, `Type.body`,
`Type.caption`, `Type.label` — so a screen never states a point size. The values exist in
`colors_and_type.css` in the handoff and can be lifted directly.

## 3.2 · The five primitives — and the finding that matters

**Two of the five already exist twice.**

| Primitive | Today | Problem |
|---|---|---|
| **PrimaryButton** | `NextButton` (welcome) **and** `SunsetButton` (tutorial) | Two implementations of one concept |
| **StatusBadge** | `Eyebrow` (welcome) **and** `EyebrowPill` (tutorial) | Two implementations, and they diverged |
| **InputField** | inline in `PhoneVerificationScreen` | Not extracted; the 23dp touch-target bug lived here |
| **SelectPicker** | `CountrySheet` | Exists, reusable, fine |
| **TopBar** | inline in `VerificationFrame` | Not extracted; the wrong-icon bug lived here |

**This is not a tidiness observation.** Three of this week's six defects were in exactly these
places, and two of them were *caused* by duplication:

- the wordmark on tutorial card 1 had drifted from the shared one in three ways at once
- the eyebrow existed twice, so fixing the phone screen's tone could not have fixed the tutorial's

Each duplicate pair is a place where a fix lands on one screen and not the other. Extracting these
five is the direct remedy, and the sequence should be: **build the primitive, test it in isolation
with previews at the smallest and largest supported sizes, then migrate the screens onto it** — not
the other way round.

## 3.3 · Order of work

1. `Space` and `Type` scales, plus the illustration palette, and the checker that enforces them
2. `PrimaryButton` — collapses two implementations into one
3. `StatusBadge` — collapses two, with the tone as a parameter, so the eyebrow bug cannot recur
4. `TopBar` — the back control lives in one place
5. `InputField` — with the 44pt minimum built in rather than remembered
6. `SelectPicker` — already effectively done, formalise the interface

---

# 4 · Guardrails and Definition of Done

## 4.1 · Where CLAUDE.md lives

The brief says "both the iOS and Android repositories". **There is one repository**, with the
backend, the iOS app and the Android app all on `main`. Splitting is possible but I would advise
against it: the parity checks that compare Kotlin against Swift are the only automated check the
iOS half currently has, and they need both in one place.

`mobile/welcome-screen/CLAUDE.md` exists and covers Swift 6 conventions, availability, tokens,
layout and verification. The additions the brief asks for are below.

## 4.2 · Rules to add

**Minimum OS.** iOS 17.0 and Android API 30 — **already done**, and it deleted two workarounds
rather than moving them.

**Forbidden patterns:**

| Rule | Enforceable how |
|---|---|
| No UIKit wrappers in new screens | checker: `UIViewRepresentable` outside an allow-list |
| No fixed-width frames on text or containers | review; the shape cases are legitimate |
| No raw hex outside the design system | checker, ~10 lines |
| No point sizes outside the type scale | checker |
| No `@unchecked Sendable` / `nonisolated(unsafe)` / `@preconcurrency` | **already enforced** |
| No mutable global state | **already enforced** |
| No `DispatchQueue` in new code | **already enforced** |

**On UIKit wrappers**, one exception already exists and should stay: `WashHeadline` is a
`UIViewRepresentable` because the orange wash has to be measured from the laid-out text, which
SwiftUI does not expose. The rule should be "no NEW UIKit wrappers without a written reason", not a
blanket ban — a blanket ban would be quietly broken rather than argued with.

**Architecture.** This needs your decision, and it is the one open question in this section. Our
screens hold no state; it is hoisted to one owner per flow. That is the Compose idiom and it is why
every screen is testable without a device. MVVM is the iOS idiom and fits SwiftUI naturally. The
options:

- **MVVM on both** — consistent, familiar to any iOS hire, and means restructuring working Android screens
- **"Views hold no state" on both**, each platform's idiom underneath — no rework, and the tests keep working

I would take the second and write the principle down rather than the pattern name. But it is a
call about the team you intend to hire as much as about the code.

## 4.3 · Definition of Done for a mobile screen

A screen may merge when:

- [ ] Both apps **build** — Android in CI today, iOS once the Mac exists
- [ ] Unit tests pass, and the screen is in `ScreenFitTest`
- [ ] It renders correctly at **375×667** and **440×956**, and the fit suite reports no new findings
- [ ] Every tappable thing is **≥ 44pt / 48dp**
- [ ] No new hardcoded colour, size or spacing
- [ ] Copy matches the ticket **exactly**, checked by a verifier
- [ ] It survives **rotation, backgrounding and process death**
- [ ] Loading, empty, error and offline states exist — not just the happy path
- [ ] Labelled for VoiceOver and TalkBack; usable at the largest system font
- [ ] Both platforms changed **in the same commit**
- [ ] The nine design-conformance checkers pass
- [ ] **Somebody has looked at it running** — on a device or a simulator

The last one has no substitute, and this week is the evidence: the wrong back icon and the wrong
eyebrow tone both passed every automated check, because both were real components from the design
system, correctly drawn. Only a person comparing the screen to the reference found them.

---

# 5 · Backwards compatibility and the device matrix

Minimums are now **iOS 17.0** and **Android API 30**, and the fit suite runs every screen state
across **17 device sizes** on every pull request.

| | Size | Why it is in the matrix |
|---|---|---|
| Smallest | 375 × 667 | iPhone SE (3rd gen) — the floor you named |
| | 360 × 640 | 4.7-inch Android |
| Largest | 430 × 932 | iPhone 15/16 Pro Max, Dynamic Island |
| | 440 × 956 | iPhone 16 Pro Max |
| | 412 × 915 | Pixel 8 Pro |

The 320 × 568 iPhone SE (1st gen) was removed when the floor moved: it cannot run iOS 17, and no
API 30 Android is that narrow.

**What the suite measures today:** clipped text, elements outside the safe area, text collapsed to
nothing, and tap targets below 44pt. Insets are modelled per device, so "fits" means "fits where the
user can see it", not "fits the raw rectangle".

**What it does not yet measure, and should:**

- **Keyboard overlap.** Named in the brief and genuinely missing. The phone and code screens are the
  ones at risk, and the smallest screen is where it bites. Needs the keyboard's height modelled as
  another inset — a contained addition to the existing harness.
- **Largest system font.** Currently everything is measured at the default text size. Accessibility
  sizes are where "no scroll" requirements break, and this is a legal requirement in the EU under
  the Accessibility Act since June 2025.
- **Landscape.** Either support it or lock it, but decide.

---

# 6 · What I need from you

1. **The iOS observations.** Screens, controls, what happened, device and OS, screenshots or a
   recording. Area 1 becomes real analysis with them and stays speculation without them.
2. **MVVM by name, or the principle?** See 4.2.
3. **Primitives: retrofit or forward-only?** Extracting them means reworking five screens that
   currently work and re-verifying them, or applying it to new work and migrating gradually.
4. **Backend changes in scope?** Areas 2's three gaps are backend edits — the emit script, 52
   `operationId`s, 39 response decorators.
5. **One repo or two?** See 4.1. I would keep one.

## Suggested order, given no Mac yet

Everything in areas 2 and 3 can be done now, on Android and on the backend, and iOS inherits it the
day the Mac arrives:

1. The backend OpenAPI gaps — pure backend, unblocks both clients
2. Generated client on Android, proving the pipeline end to end
3. `Space`, `Type` and the palette, plus the checkers
4. The five primitives on Android, with previews
5. **Mac arrives** — iOS builds and tests in CI, the iOS layout audit, then the same primitives in
   Swift against an interface already proven

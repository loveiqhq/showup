# iOS working rules

Applies to everything under `ios-app/`. The shared rules in `../CLAUDE.md` apply too — this file
adds only what is genuinely iOS-specific and overrides nothing.

Every rule has its reason next to it. A rule with no reason is one nobody can safely change later.

---

## The three rules that come first

**Before creating a new implementation, first search the repository for an existing shared
component, token, model, service, or pattern. Do not create a duplicate implementation without
explaining why.**

**Generated code compiling is not proof that the feature is complete.**

**Do not report a platform as verified unless it was actually built and tested in that platform's
toolchain.**

The first exists because the primary button was written three times, and the copy nobody was
looking at drifted for three weeks before anyone noticed. The third exists because eight
tests here sat in no target for a week and were reported as present and passing.

---

## Framework and target

- **SwiftUI first.** UIKit only through a wrapper, and only with a written reason — see below.
- **Deployment target is iOS 17.0.** Using a newer API compiles cleanly and then misbehaves on a
  real phone. This has happened twice (`scrollBounceBehavior`, and `onChange`). Gate anything newer
  with `if #available`, and run `../audit/check-ios-availability.py`.
- That checker holds a curated list, so it is a safety net, not a compiler. A clean run means
  "nothing known is wrong", never "this builds".

## State ownership

**A view holds no state.** State is hoisted to one owner per flow — `SignUpFlow` — and screens take
values and return a picture. This is the rule; MVVM is the name people give it.

The property being protected is that a screen is a function from values to pixels, which is exactly
why every screen can be rendered in every state at 17 device sizes with no device attached.

- **`@State`** — only for something genuinely local and disposable: whether a menu is open, an
  animation phase. If a parent needs to read it, it is in the wrong place.
- **`@Binding`** — to let a child write a value its parent owns. **Never put formatting or any other
  logic in a binding's getter.** A getter runs during view evaluation, which is not synchronised
  with UIKit's editing session; this shipped as the phone field that never grouped and lost half its
  keystrokes. Transform in `onChange` instead.
- **`@Observable`** — when a screen owns asynchronous work that must outlive a redraw and be
  cancellable: loading, uploading, retrying. Then it is a `@MainActor @Observable final class`. None
  of the current screens qualify; profile and discovery will. Adopting it before that adds ceremony
  without buying anything.
- **`@StateObject` / `@ObservedObject`** — do not introduce. `@Observable` supersedes them on iOS 17.
- **No business logic inside a view.** Validation, formatting and decisions live in plain testable
  functions — see `CountryCodes.swift`, whose rules are unit-tested without any view.

## Concurrency

The project sets `SWIFT_STRICT_CONCURRENCY = complete` with the language mode still at 5.0, so
data-race problems are **warnings** today and **errors** when the mode moves to 6.0. Write as though
it were already 6.0.

- **Views and anything holding UI state are `@MainActor`.**
- **Types crossing an isolation boundary are `Sendable`.** Prefer `struct` with `let`. A class shared
  across tasks is an `actor` or it is immutable.
- **No shared mutable global state.** No `var` at file scope, no mutable `static var`. If it must be
  global and mutable it is an `actor` or it is `@MainActor` — see `phoneUtility`.
- **`async`/`await`** over completion handlers, and over Combine, for new code.
- **`nonisolated` is a deliberate claim**, not a way to silence a warning.
- **Never silence a concurrency warning.** Fix the isolation, or leave it and say so. The three
  escape hatches — `@unchecked Sendable`, `nonisolated(unsafe)`, `@preconcurrency` — are banned and
  `../audit/check-swift-concurrency.py` fails the build on them.
- **Every `Task` that outlives a view must be cancellable**, and cancellation must actually be
  handled. Two exist today, both short-lived animation timing.

**The honest limit:** Swift 6's checking is whole-module static analysis. Conventions make code
*likely* to pass; only compilation proves it. The first Mac build found a `[String: Any]` that needed
`[String: any Sendable]` — no checker here could have caught that. CI compiles on `macos-15`, so this
is enforced, not merely intended.

## Layout

- **No absolute positioning.** Stack layout only.
- **No fixed width on anything containing text**, and no width derived from the screen. Fixed frames
  are for icons, avatars and badges — things meant to be a fixed size.
- **Never design for one device.** Every new screen goes into `ScreenFitTest`, which measures 17
  sizes from 320×686 to 440×956.
- **Text is measured, not guessed.** No hand-tuned width for an underline or accent — measure the
  laid-out run. A fixed width is right at one size and wrong at sixteen.
- **44pt minimum for anything tappable**, even where the reference draws smaller. A control whose
  hit area is smaller than it looks is a bug the eye cannot see; this shipped as a 23dp target inside
  a 56dp row.
- **Never wrap a reserved region in a bare `if`.** In a `ViewBuilder`, `if x { Card() }` with no
  `else` evaluates to nil, and a frame around nil reserves nothing. This shipped as a CTA that moved
  56 points. Render it always and hide it with `opacity`.
- **`GeometryReader` fills its parent and top-leading-aligns its child.** Use it to read a size for
  background art or layout maths, not as a container around content, or it will silently change the
  layout around it.
- **Respect the safe area**, including the Dynamic Island. The fit harness models per-device insets.

## Design tokens and components

- **One definition per value**, in `DesignSystem.swift`. **No colour literal in a screen** — with one
  exception: third-party brand colours (Google, Meta) whose values their sign-in branding rules
  mandate. Those carry the rule that mandates them, in a comment.
- **No arbitrary spacing, radius, icon size or duration at a call site.** Use the scale.
- **No point size in a screen.** Use the named type roles.
- **Use the shared primitives.** If one does not exist yet, that is a reason to build it, not to
  inline a copy — see rule one.
- **A primary button is `PrimaryButton`** (`PrimaryButton.swift`). Six variants, and `leading` /
  `trailing` slots. Name the slot at the call site rather than using a trailing closure: with
  two closure properties an unlabelled one is ambiguous, and Swift's error for that names
  neither. `NextButton` is not a second primary button — it is a label beside a circular arrow
  badge, with its own spec.
- **A shared primitive never lives in a screen file.** `PillButton` sat in `WelcomeShell.swift`,
  so the tutorial grew its own copy. Anything two flows use gets its own file.
- **A status badge is `StatusBadge`** (`StatusBadge.swift`). Two tones, `.orange` and `.lavender`.
  It does not align itself — the parent positions it. That is why there were three: two of the
  originals forced leading alignment, and Connect's badge is centred, so it grew its own copy.
- **A component's tone is per screen, and both tones stay.** The eyebrow pill is orange on the phone
  screens and lavender on the tutorial cards. Changing the shared token to fix one screen breaks the
  other; add a variant.
- **Check the handoff for which variant a screen uses.** The back control was drawn `arrow-left`
  where the design says `chevron-left` — a real icon from the same set, faithfully drawn, and wrong.
  No test catches that; only reading the reference does.

## UIKit wrappers

**No new `UIViewRepresentable` without a written reason in the file.** Not a ban — a ban gets broken
quietly instead of argued with.

One exists and should stay: `WashHeadline`, because the underline must be measured from laid-out
text and SwiftUI does not expose that. Both of the worst state bugs in this codebase were at the
SwiftUI/UIKit seam, which is why each new one has to be justified.

## Networking

- **No hand-written API models, endpoint paths, request bodies or response types** once the OpenAPI
  client is generated. The backend contract generates them.
- **Never edit generated files.** They live in the build directory and are erased on the next build.
- **Auth is attached by middleware, never at a call site.** No screen touches a token.
- **No base URL literal anywhere.** It comes from the build configuration.

## Navigation

Routing today is a `Step` enum in `SignUpFlow` and a `FlowScreen` enum in `ShowUpWelcomeApp` --
deliberate, typed, and testable for one linear flow. Do not add a second hand-rolled router.

**Adopt `NavigationStack` at the first of these, and not before:** a deep link, a screen reachable
from two places, or the profile/discovery flows. Re-audited 8 September 2026 and confirmed as the
right call for now -- see `docs/mobile-client-architecture-spike.md` 1.5.1.

**When it triggers, migrate the SHELL only first** -- `TutorialFlow`'s `switch` becomes a
`NavigationStack` with a `NavigationPath`; `SignUpFlowView` stays one destination keeping its own
`Step`. A full per-screen migration needs a requirement that clearly demands it.

**State restoration is a separate concern and is already solved without a framework.** Flow position
and the user's typed input are held in `@SceneStorage` -- scene-scoped, so a properly closed scene
does not resurrect a half-finished sign-up. Do not reach for `NavigationStack` to get restoration.

**A screen takes values and returns pixels. It never receives a navigator.** That single property is
what makes `ScreenFitTest` at 17 sizes and 50 previews possible.

## Screen requirements

- **Every screen has loading, empty, error and offline states.** Not just the happy path.
- **Keyboard must not hide any required control.** The phone and code screens are where this bites,
  and it is not yet automated — check it by hand.
- **Accessibility:** every control labelled for VoiceOver; usable at the largest system font;
  contrast at WCAG AA.
- **Previews for every state**, and at the smallest and largest size, not just the default.
- **Logging:** no `print`. Nothing that could log a phone number, a code, a token or an email.

## Tests

- Logic goes in plain functions and is unit-tested without a view.
- Every screen is in `ScreenFitTest`.
- **A test file that is not in the target does not exist.** `project.pbxproj` is generated by
  `gen_pbxproj.py`, which discovers tests from disk; CI regenerates it and fails if the committed one
  is stale.

## Verification

```
cd ios-app && xcodebuild -project ShowUpWelcome.xcodeproj -scheme ShowUpWelcome \
    -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
cd .. && for f in audit/verify-*.py audit/check-*.py; do python "$f"; done
```

**Say what was actually verified.** "Builds and tests pass" and "I have seen it render" are different
claims. Both reserved regions on SHOWUP-143 were wrong in ways that appeared only once the CTA was
measured moving — one reserved nothing at all. Run the screen.

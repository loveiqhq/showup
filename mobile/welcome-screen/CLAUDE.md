# Working rules for the ShowUp mobile code

Instructions for anyone — person or assistant — writing Kotlin or Swift in this folder. Rules here
are followed by default and do not need restating in each request.

Every rule below exists because something went wrong once. None of them are style preferences.

---

## Swift 6 concurrency

The project sets `SWIFT_STRICT_CONCURRENCY = complete` with the language mode still at 5.0, so
data-race problems appear as **warnings** today and become **errors** the day the mode moves to 6.0.
Write as though it were already 6.0:

- **Views and view models are `@MainActor`.** SwiftUI already is; anything holding UI state joins it
  rather than hopping between contexts.
- **Types crossing an isolation boundary are `Sendable`.** Prefer `struct` with `let` properties;
  a class shared across tasks is either an `actor` or immutable.
- **No shared mutable global state.** No `var` at file scope, no mutable `static var`. If something
  must be global and mutable it is an `actor` or it is `@MainActor`.
- **`async`/`await` over completion handlers**, and over Combine for new code.
- **`nonisolated` is a deliberate claim**, not a way to silence a warning. Only where the work
  genuinely touches no isolated state.
- **Never silence a concurrency warning to make it go away.** Fix the isolation, or leave the
  warning and say so.

### The honest limit of these rules

**Following them is not a guarantee, and nothing written here can make it one.**

Swift 6's data-race checking is whole-module static analysis. Whether a specific line is safe
depends on the isolation of every type it touches, the `Sendable` conformance of things declared in
other files, and where actor boundaries fall. The compiler computes that. Conventions make the code
*likely* to pass; only compilation *proves* it.

So these rules reduce the size of the eventual migration. They do not remove the need to compile,
and any claim that code is "Swift 6 clean" without a build is unfounded. **Only Xcode on a Mac can
make this rule enforced rather than intended** — the same way the Android rules below are enforced,
because CI builds them.

There is a Mac now, as of 2026-09-01, and the first build found four errors the reading had not.

## iOS availability

The deployment target is **iOS 17.0**. Using a newer API compiles cleanly and then misbehaves on a
real phone — this has happened twice here (`scrollBounceBehavior`, and the two-parameter
`onChange`). Gate anything newer with `if #available`, and run `audit/check-ios-availability.py`.

Note the second example inverted when the minimum moved from 16 to 17: the ONE-parameter
`onChange(of:perform:)` is the deprecated form now. Both directions cost a build to notice.

That checker holds a curated list, so it is a safety net and not a compiler. Treat a clean run as
"nothing known is wrong", never as "this builds".

## Both platforms move together

A change to a screen changes it on Android **and** iOS in the same commit. Kotlin and Swift are
ports of the same designs; a fix to one and not the other is how they drift.

`audit/check-tutorial-routing.py` and the `verify-*.py` scripts compare the two. They are currently
the **only** automated check the iOS half has at all.

## Design tokens

- **One definition per value**, in `DesignSystem.kt` / `DesignSystem.swift`. Never a colour literal
  in a screen.
- **A component's tone is per screen, and both tones stay.** The eyebrow pill is orange on the phone
  screens and lavender on the tutorial cards. Changing the shared token to fix one screen breaks the
  other; add a second token.
- **Check the handoff for which variant a screen uses.** The back control was an `arrow-left` where
  the design says `chevron-left` — a real icon from the same set, faithfully drawn, and the wrong
  one. Tests cannot catch that; only reading the reference can.

## Layout

- **No absolute Y positioning.** Flex/stack layout only.
- **Text is measured, not guessed.** No hand-tuned width for an underline or an accent — measure the
  laid-out run. A fixed width is right at one screen size and wrong at seventeen others.
- **44dp/pt minimum for anything tappable**, even where the reference draws smaller. A visible
  control whose touch area is smaller than it looks is a bug the eye cannot see.
- New screens are added to `ScreenFitTest`, which measures every state at 18 phone sizes.

## Verification

Before saying a change is done:

```
cd android-preview-project && ./gradlew :app:assembleDebug :app:testDebugUnitTest
cd ios-app && xcodebuild -project ShowUpWelcome.xcodeproj -scheme ShowUpWelcome \
    -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
cd .. && for f in audit/verify-*.py audit/check-*.py; do python "$f"; done
```

CI runs both halves on every pull request. Android adds `assembleRelease` — R8 only runs on
release, and it removes what it cannot see being used. iOS adds `ScreenFitTests`, which renders both
phone screens at 17 device sizes and measures whether the CTA moves; it is the only automated check
that would have caught the two layout faults SHOWUP-143 shipped.

**Say what was actually verified.** "Builds and tests pass" and "I have seen it render" are
different claims, and on iOS the second one is now available: the simulator will show you things
no amount of reading will. Both reserved helper regions on SHOWUP-143 were wrong in ways that only
appeared once the CTA was measured moving — one of them reserved nothing at all. Run the screen.

## House style

- **No emoji. Anywhere** — in code, comments, commits or UI.
- Comments explain **why**, not what. A comment that restates the code is deleted.
- A rule with no reason next to it is one nobody can safely change later.

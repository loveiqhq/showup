# Working rules for the ShowUp mobile code

Instructions for anyone — person or assistant — writing Kotlin or Swift in this folder. Rules here
are followed by default and do not need restating in each request.

Every rule below exists because something went wrong once. None of them are style preferences.

---

## How these files are scoped

This is a monorepo. Claude Code reads every `CLAUDE.md` from the repository root **down to the
directory being worked in**, and the nearest file wins on a conflict. There are three:

| File | Applies to | Holds |
|---|---|---|
| **this file** | both apps | What is true of both: state ownership, parity, tokens, layout, verification, house style |
| `ios-app/CLAUDE.md` | iOS only | SwiftUI, strict concurrency, `@MainActor`, `@State` / `@Binding` / `@Observable`, availability, UIKit wrappers |
| `android-preview-project/CLAUDE.md` | Android only | Compose, `remember` vs `rememberSaveable`, coroutines, lifecycle-aware collection, `minSdk`, IME |

**A rule belongs in exactly one of them.** Stated twice, it drifts. If a rule is true of both
platforms it goes here, even when it is expressed differently in each language.

---

## The three rules that come first

**Before creating a new implementation, first search the repository for an existing shared
component, token, model, service, or pattern. Do not create a duplicate implementation without
explaining why.**

**Generated code compiling is not proof that the feature is complete.**

**Do not report a platform as verified unless it was actually built and tested in that platform's
toolchain.**

Each of these is here because it was broken. The primary button existed three times on Android until 7 September 2026, and one of the copies had quietly drifted four ways. Eight
Swift tests sat in no target for a week and were reported as present. "It compiles" was, more than
once, the whole basis for calling a screen done.

---

## Where state lives

**A view holds no state.** State is hoisted to one owner per flow — `SignUpFlow` on both platforms —
and screens take values and return a picture.

This is the rule, not MVVM. Each platform expresses it its own way, and the property being protected
is that a screen is a function from values to pixels — which is exactly why every screen can be
rendered in every state, at 17 device sizes, with no device.

**When a screen genuinely owns asynchronous work** — loading, uploading, retrying — hoisted state is
no longer enough, because that work must outlive a redraw and be cancellable. Then it gets
`@Observable` on iOS or a ViewModel on Android. That is this rule being applied, not abandoned. None
of the screens built so far are at that point; the profile and discovery screens will be.

**No business logic inside a view or composable.** Validation, formatting and routing decisions go in
plain functions that are tested with no UI at all — see `CountryCodes` and `TutorialRouting`, which
exist on both platforms in the same shape for exactly this reason.

## Both platforms move together

A change to a screen changes it on Android **and** iOS in the same commit. Kotlin and Swift are ports
of the same designs; a fix to one and not the other is how they drift.

`audit/check-tutorial-routing.py` and the `verify-*.py` scripts compare the two. They used to be the
only automated check the iOS half had; since 2026-09-01 it is compiled and tested in CI as well, so
they are now a parity check rather than a substitute for one.

**The same logic error can be harmless on one platform and severe on the other.** A bare `if` around
a reserved region collapses to nil in SwiftUI and reserved nothing, moving the CTA 56 points; the
identical code on Android reserved its minimum height and looked fine. "It works on Android" is not
evidence about iOS.

**Portrait only, both platforms.** Product decision, 9 September 2026. iOS lists one supported
orientation in `Info.plist`; Android sets `android:screenOrientation="portrait"`. Do not design
for landscape and do not add the other orientations back without a product decision reversing
this. Large screens are a real caveat — see `docs/mobile-client-architecture-spike.md` 1.5.2.

## Minimum OS targets

**iOS 17.0 and Android API 30.** Both are set in the build, not only written here:
`IPHONEOS_DEPLOYMENT_TARGET = 17.0` in `gen_pbxproj.py`, `minSdk = 30` in `app/build.gradle.kts`.

API 30 is Android 11, not Android 14. It was chosen to keep the app installable on the long tail of
active devices; raising it is a product decision, not a technical one. Nothing in the code currently
requires anything newer on either platform.

## Design tokens

Six files per platform, since 7 September 2026. `DesignSystem` holds colour and type families;
`Spacing`, `Radius`, `ComponentSizes`, `IconSizes` and `Motion` hold the rest. `docs/design-system.md`
lists every token and, just as importantly, what deliberately is not one.

**Before writing a number into a screen, check whether a token holds it.** In order:

- **A raw hex colour in a screen is forbidden** while a colour token exists for it. The one exception
  is below.
- **`Spacing.screenGutter`, `.xs`, `.sm`, `.md`, `.lg`, `.xl`, `.xxl`** — never a raw `dp`/point value
  for padding, a gap, or a spacer when one of these holds it.
- **`Radius.control`, `.pill`, `.errorBox`, `.card`** — never a raw corner radius for a component.
- **`ComponentSizes.controlHeight`, `.minTapTarget`** — never a new standard control height. A
  56-tall control and a 44 tap floor already have names.
- **`IconSizes.sm`, `.badge`** — never a raw size for an icon or a status badge.
- **`Motion.*`** — never a raw animation duration. The five are shared across platforms and the
  shake is specified by SHOWUP-143.

**Typography is the exception, and on purpose.** There are no type tokens: the design has 41 distinct
family/size/weight/line-height combinations and only 12 repeat, so there is nothing coherent to name
yet. Keep writing type at the call site until that changes, and do not invent roles to fill the gap.

**Do not add a token because a number appears several times.** A token needs a meaning its name can
state. `14` appears 22 times in spacing and is still local, because no single meaning could be found
for it. A token whose name cannot say what it is for is a number with extra steps.

**Do not tokenise illustration geometry.** Dot offsets, ring positions, sparkle coordinates and
canvas sizes belong to the drawing. Putting them in the design system makes them look reusable.

**If a value must stay local, say why at the call site.** The reserved helper regions on the phone
screens are the model: 42 is not a constant, it is `error box + 2`, and the comment says so and says
the two move together.

- **One definition per value**, in the token files. Never a colour literal, spacing value, radius,
  icon size or font size in a screen file when a token holds it.
- **One exception: third-party brand colours.** Google's and Meta's sign-in branding rules mandate
  exact values we are not permitted to re-theme, so those stay as literals and carry the rule that
  mandates them in a comment. A checker that failed on every literal would flag all fourteen of them
  and be switched off within a day.
- **A component's tone is per screen, and both tones stay.** The eyebrow pill is orange on the phone
  screens and lavender on the tutorial cards. Changing the shared token to fix one screen breaks the
  other; add a second variant.
- **Check the handoff for which variant a screen uses.** The back control was an `arrow-left` where
  the design says `chevron-left` — a real icon from the same set, faithfully drawn, and the wrong
  one. Tests cannot catch that; only reading the reference can.

## Layout

- **No absolute positioning.** Flex/stack layout only.
- **Text is measured, not guessed.** No hand-tuned width for an underline or an accent — measure the
  laid-out run. A fixed width is right at one screen size and wrong at sixteen others.
- **No fixed width on anything containing text**, and none derived from the screen width. Fixed
  frames are for icons, avatars and badges.
- **44pt / 48dp minimum for anything tappable**, even where the reference draws smaller. A visible
  control whose touch area is smaller than it looks is a bug the eye cannot see.
- **Reserve space that does not depend on whether content is present.** Both platforms have shipped a
  CTA that moved because a reserved region was sized for content that was absent or shorter than
  reality.
- New screens are added to `ScreenFitTest`, which measures every state at 17 phone sizes. The
  narrowest is **320 × 686**, the Galaxy Fold cover screen — narrower than any iPhone, and the case
  that actually breaks layouts.

## Screen requirements

Every screen, on both platforms:

- **Loading, empty, error and offline states**, not only the happy path.
- **The keyboard must not hide any required control.** Not yet automated on either platform — check
  it by hand, on the smallest screen.
- **Accessibility:** every control labelled for VoiceOver and TalkBack, usable at the largest system
  font, contrast at WCAG AA.
- **Previews for every state**, at the smallest and largest size, not just the default.
- **No logging of a phone number, a verification code, a token or an email.** Ever.

## Networking

No hand-written API models, endpoint paths, request bodies or response types once the OpenAPI client
is generated — the backend contract generates them, and generated files are never edited. Auth is
attached by middleware or an interceptor, never at a call site, and no base URL appears as a literal.

See `docs/mobile-client-architecture-spike.md` part 2.

## Verification

Before saying a change is done:

```
cd android-preview-project && ./gradlew :app:assembleDebug :app:testDebugUnitTest
cd ios-app && xcodebuild -project ShowUpWelcome.xcodeproj -scheme ShowUpWelcome \
    -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
cd .. && for f in audit/verify-*.py audit/check-*.py; do python "$f"; done
```

CI runs both halves on every pull request — Android on `ubuntu`, iOS on `macos-15`. Android adds
`assembleRelease`, because R8 only runs on release and removes what it cannot see being used.

**Say what was actually verified.** "Builds and tests pass" and "I have seen it render" are
different claims. Report them separately, in the format below.

## Required response format

Use this after implementing or changing a screen. It lives here rather than in the architecture
spike because a rule somebody has to go and look up is a rule that gets skipped — the spike explains
why this exists, this file is what makes it apply.

**"Unverified" is a first-class outcome, not a failure.** What it prevents is a confident "done"
backed only by reading, and on this project that has happened repeatedly: eight Swift tests were
reported as present and passing while sitting in no test target for a week; iOS was called green for
weeks with no Mac anywhere; a package was described as wired into an app target whose build had
never run.

```
IMPLEMENTATION COMPLETE — <screen> (<ticket>)

Files changed:
  <path>  (+n / -n)

Build:
  Android:  PASS | FAIL | NOT RUN (<why>)
  iOS:      PASS | FAIL | NOT RUN (<why>)

Tests:
  Android:  <n> passed
  iOS:      <n> passed
  Fit:      17 device sizes, <n> new findings

Checkers:
  <n>/<n> pass

VERIFIED:
  - <what was actually built, run or measured, and by what>

UNVERIFIED:
  - <what was only read, and why it could not be run>

Known limitations:
  - <e.g. keyboard overlap is not measured on any screen>

Manual checks still required:
  - <what a person must look at, and on which device>
```

### The rules that make it worth having

- **Never write PASS for a platform that was not built.** `NOT RUN` with a reason is the honest
  answer, and the reason matters: "no Mac available" and "CI has no runners" are different problems
  with different fixes.
- **Never list something under VERIFIED that was only inspected.** "The code looks correct" belongs
  under UNVERIFIED. So does "the structure is internally consistent" — a checker passing is not a
  compiler passing.
- **A count is not a verification.** "45 tests" means nothing unless they ran. Take the number from
  the XML report or the CI log, never from the files on disk.
- **If a section is empty, write "none" rather than deleting the heading**, so an omission is
  visible rather than silent.
- **One platform's result never stands in for the other's.** "It works on Android" is not evidence
  about iOS: the same logic error has already been severe on one and invisible on the other.

## Definition of Done

A screen is not done until every one of these is true. This is the merge gate.

- [ ] Both apps build and test in CI — Android on `ubuntu`, iOS on `macos-15`
- [ ] The screen is in `ScreenFitTest` and adds no new fit findings
- [ ] Renders correctly at **320 x 686** and **440 x 956**
- [ ] Every tappable thing is **at least 44pt / 48dp**
- [ ] No new hardcoded colour, size or spacing
- [ ] Copy matches the ticket exactly, checked by a verifier that quotes the ticket
- [ ] Survives rotation, backgrounding and process death
- [ ] Loading, empty, error and offline states exist -- not only the happy path
- [ ] Labelled for VoiceOver and TalkBack; usable at the largest system font
- [ ] Both platforms changed in the same commit
- [ ] All nine conformance checkers pass
- [ ] **Somebody has looked at it running, on a device**

The last item has no substitute and is not a formality. The back control was drawn as an arrow where
the design says chevron, and the eyebrow pill used the lavender variant on a screen the design paints
orange. Both passed every automated check here, because both were real components from the design
system, correctly drawn, in the wrong place.

## House style

- **No emoji. Anywhere** — in code, comments, commits or UI.
- Comments explain **why**, not what. A comment that restates the code is deleted.
- A rule with no reason next to it is one nobody can safely change later.

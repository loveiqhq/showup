# Native app development: what to settle before building

31 August 2026 · Kotlin/Android and Swift/iOS · written against ShowUp's actual situation
(dating app, German launch, EU data residency, AWS eu-west-1)

This is not a general tutorial. It is the list of things that are expensive or impossible to change
later, the traps that are specific to native mobile, and a concrete bar for "good enough to ship".

---

# 0 · Three things that are already true today

These are not "before you build" items. They are live now, and two of them affect ShowUp directly.

## 0.1 · Google Play's target API deadline — DONE

As of **31 August 2026**, new apps and app updates must target **Android 16 (API 36)** to be
published on Google Play.

**Moved on 31 August 2026.** The project now declares:

```kotlin
compileSdk = 36
targetSdk  = 36
minSdk     = 24     // still worth raising, see 1.1
```

API 36 needs a newer toolchain than we had, so three things moved together — AGP 8.7.3 to 8.9.3,
Gradle 8.9 to 8.11.1, and the SDK levels. The app builds and all tests pass.

Done early on purpose. Raising `targetSdk` is never really a one-line change — each level brings
behaviour changes around permissions, background work and storage — and it is far cheaper to
absorb those now, on five screens, than in a release week.

## 0.2 · Dating apps must block under-18s on Google Play

Since **January 2026**, Google Play requires dating apps to block under-18 users via the *Restrict
Declared Minors* setting in Play Console.

**Correction — we already have this, and I was wrong to say otherwise.** The backend collects
`date_of_birth` on the profile, enforces 18+ in `profiles.service.ts` via `isAtLeast18`, and the
public profile DTO exposes only a derived `age`, never the raw date. That last detail is good
privacy design and was already there.

What is true is narrower: the gate lives at **profile creation**, not in the sign-up flow. Someone
can create an account and verify a phone number before anything asks their age. For Play's
*Restrict Declared Minors* setting that is very likely fine — the account cannot become usable
without passing the check — but two things still need doing, and neither is code:

- The **Play Console setting itself** has to be switched on. It is a policy toggle, not something
  the app can satisfy on its own.
- Someone should confirm that gating at profile creation rather than at sign-up is acceptable,
  since the store's wording is about users, not profiles.

Separately, Apple has been tightening this: apps with user profiles or messaging need a high age
rating, and from February 2026 Apple blocks 18+ downloads in several countries unless the user has
been confirmed as an adult. The EU is pushing member states toward age verification by the end of
2026 under the DSA, though whether it becomes mandatory is still unsettled.

**Decision needed:** where the age gate lives in the flow, what we collect, and whether
self-declaration is enough for launch. This is a product and legal question, not an engineering one.

## 0.3 · Sign in with Apple cannot be dropped, and it is not free

**Recorded 31 August 2026 so it is not rediscovered late.**

App Store guideline **4.8** applies the moment an app uses a third-party login to set up the primary
account. We use two — Google and Facebook — so we must *also* offer a login that:

* limits data collection to the user's name and email address, and
* lets the user keep their email address private, and
* does not collect in-app behaviour for advertising without consent.

Sign in with Apple satisfies all three. It is not literally mandatory — any provider meeting the
criteria counts — but in practice it is the answer, and it is already in the design.

**The consequence to remember: the Apple button is a condition of offering the Google and Facebook
ones.** If a release is ever running late, dropping Apple sign-in to save time is not an option; it
would take Google and Facebook down with it, leaving only phone. Anyone tempted by that trade needs
to know it is not available.

**And it is a paid entitlement.** Sign in with Apple is not in Apple's free developer tier, so the
$99/year Apple Developer Program is needed as soon as the Apple button is wired up — a sub-task on
SHOWUP-144 — not at release.

### Developer accounts, what each is actually for

Publishing and signing-in are separate concerns, and conflating them causes people to think they are
blocked when they are not.

| Account | Cost | Needed for |
|---|---|---|
| Google Play Console | **$25 once, never again** | Publishing on Android. **Not** needed for Google sign-in |
| Apple Developer Program | **$99 per year** | Publishing on iOS **and** Sign in with Apple |
| Google Cloud project | free | Google sign-in (OAuth client) |
| Meta developer account | free | Facebook login |

Two lead-time traps:

* **An organisation Play account needs a D-U-N-S number** — a free business identifier from Dun &
  Bradstreet — plus incorporation or tax documents. Obtaining one is not instant. Start it before
  it is on the critical path.
* **Google sign-in breaks between testing and the store** unless one step is done. Google re-signs
  the app when it is published, which changes its signing fingerprint, and Google sign-in checks
  that fingerprint. The certificate from Play Console has to be added to the Google Cloud OAuth
  client at release. Five minutes, and it fails in exactly the build nobody tests by hand.

## 0.4 · Apple requires in-app account deletion

Guideline **5.1.1(v)**: any app that lets you create an account must let you delete it *from inside
the app*. Not by email, not by a web form.

We have the backend work for this (SHOWUP-15 and the deletion audit), but the known gap is recorded
already: the scrub updates rows instead of deleting them, so the database cascades never fire. That
has to be genuinely working before submission, because reviewers test it.

---

# 1 · Decide before writing code

Each of these is cheap now and expensive in six months.

| Decision | Why it is hard to reverse |
|---|---|
| **Minimum OS versions** | Raising it later drops existing users; lowering it later means retrofitting workarounds through the whole codebase |
| **One codebase or two** | Native ×2 vs Kotlin Multiplatform is a fork in the road, not a setting |
| **Navigation** | Every screen depends on it. Swapping it is a rewrite of the app's spine |
| **State and data layer** | Determines whether offline, caching and optimistic updates are possible at all |
| **Where the auth token lives** | Keychain / Keystore vs anything else is a security decision baked into every request |
| **Analytics and consent** | Retrofitting consent onto an SDK that already fired events is a compliance incident |
| **Crash reporting** | Cheap on day one, and you cannot recover the crashes you did not collect |
| **Design tokens** | Two hand-maintained colour lists drift. Ours already nearly did |

## 1.1 · Minimum OS versions — what ours cost

We currently target **iOS 16** and **Android API 24**.

**iOS 16** is a reasonable floor and roughly the industry norm. The cost is real though: a great deal
of good SwiftUI arrived in 17 and 18, and every use of it needs an availability check. We have hit
this twice already — `scrollBounceBehavior` and the two-parameter `onChange` — and both were caught
by a checker rather than by a compiler, because the compiler is happy until the device is not.

**Android API 24 (2016)** is unusually low. It means no guaranteed support for Java 8+ APIs without
desugaring, more OEM quirks, and testing on devices almost nobody uses. Android's own distribution
numbers put API 24-25 well under 1% of active devices. Recommend raising to **API 26 at minimum**,
realistically **API 28**, unless there is a specific market reason not to.

## 1.2 · One codebase or two

We are currently building **twice** — Kotlin/Compose and Swift/SwiftUI, screen for screen. That is
a deliberate and defensible choice for a UI-heavy consumer app, but it should be a choice, not a
default, because we are paying for it constantly: every fix so far has been made twice, and the
iOS half cannot even be compiled in this environment.

The honest options:

- **Native ×2** (today): best feel, full platform access, double the work, double the bugs.
- **Kotlin Multiplatform**: share the logic (networking, validation, models), keep the UI native.
  This is the option that fits us best — our duplicated pain is almost entirely *logic*
  (phone validation, country data, routing rules), not UI.
- **Compose Multiplatform / Flutter / React Native**: one UI codebase. Cheaper, and a real
  compromise on platform feel that a dating app's first impression may not survive.

**Worth a serious conversation before more screens are built.** The cost of switching grows with
every screen. Note what has already happened: `TutorialRouting.kt` and `TutorialRouting.swift` are
the same three-line rule written twice, and only one of them is tested.

---

# 2 · Kotlin and Android: the traps

## 2.1 · R8 is off in our release build

```kotlin
release {
    isMinifyEnabled = false
}
```

Fine for a preview project, wrong for production. R8 shrinks and optimises; leaving it off costs
app size and runtime performance. Turning it on late is the trap — it is when you discover that
reflection-based serialisation, or a library that depends on class names, breaks in release but not
in debug. **Turn it on early**, so the breakage is found while there is little code to fix.

## 2.2 · Compose recomposition and stability

The single biggest Compose performance failure mode: an *unstable* parameter causes a composable to
recompose on every frame. Documented production cases include a feed running at 3.5 FPS because one
list parameter was unstable.

What to do:
- Keep state out of composables — ours already do this, and it is why they are testable.
- Prefer immutable types and `ImmutableList` for parameters.
- Strong skipping mode (Compose 1.7+, on by default) helps a lot, but does not save you from
  passing a mutable object.
- Measure with the Layout Inspector's recomposition counts rather than guessing.

## 2.3 · Baseline profiles

Precompile the hot paths of the first launch. Reported gains are 20–30% off cold start. Cheap to
add, and cold start is the number a reviewer and a user both feel first.

## 2.4 · Process death

Android kills backgrounded apps and restores them later. If state lives only in memory, the user
comes back to a blank form. Our flow uses `rememberSaveable` throughout for exactly this — keep
that discipline as the app grows, and test it with *Don't keep activities* switched on in developer
options. Almost nobody does, and it is where a whole class of bugs hides.

## 2.5 · OEM behaviour

Samsung, Xiaomi, Huawei and others kill background work aggressively and inconsistently. Anything
that depends on a background task or a delayed notification needs testing on real devices from
those makers, not on a Pixel emulator.

## 2.6 · The Play Data Safety form

A public declaration of what data you collect and share. It must match reality — including what
your third-party SDKs do. Getting it wrong is a policy violation, not a paperwork error.

---

# 3 · Swift and iOS: the traps

## 3.1 · Swift 6 strict concurrency

Swift 6 turns on strict concurrency checking and **refuses to compile** until data-race issues are
resolved. It broke a meaningful share of existing codebases.

The recommended path is gradual: set `SWIFT_STRICT_CONCURRENCY = complete` to get warnings without
breaking the build, fix module by module, then switch language mode. SwiftUI is already
`@MainActor`-annotated, which helps.

**Our advantage: we have almost no Swift code yet.** Starting in Swift 6 mode now costs a little
discipline; arriving at it with 40 screens costs weeks. Do it now.

## 3.2 · Availability, which the compiler will not save you from

Targeting iOS 16 while writing iOS 17 APIs compiles cleanly and crashes or misbehaves on device.
We have hit this twice. Our `check-ios-availability.py` catches a curated list, which is better
than nothing and worse than a real build. **The real fix is a Mac in CI.**

## 3.3 · Privacy manifests

Since May 2024, App Store Connect rejects apps that use "required reason" APIs without declaring
them in `PrivacyInfo.xcprivacy`. This covers ordinary things — `UserDefaults`, file timestamps,
disk space — and it covers **third-party SDKs**, which must ship their own manifests. Xcode
combines them into the Privacy Report that becomes your App Store nutrition label.

Every SDK we add is a manifest question. Check before adopting, not at submission.

## 3.4 · The build is only reproducible if the project file is

Our `project.pbxproj` is generated by a script, which is unusual but deliberate — it means a new
Swift file cannot be silently missing from the target. Keep that. The normal failure is a file that
builds on one machine and not another because someone added it through the Xcode UI on a branch.

## 3.5 · Background modes, for the AI concierge calls

The planned CallKit work needs the right background modes and entitlements, and Apple reviews VoIP
usage carefully. Do not discover the entitlement requirements in the release week.

---

# 4 · Dating-app specific: what the stores enforce

App Store guideline **1.2** (user-generated content) and Play's equivalent policies require, at
minimum:

- A method for **filtering objectionable content**
- A mechanism for users to **report** offensive content, and a commitment to act
- The ability to **block abusive users**
- **Published contact information** so users can reach you

Ours in flight: photo moderation (`pending` → `approved`/`rejected` before display), moderation
standing on users/profiles/photos, blocks and reports. That is the right shape. The thing to be
sure of is that a photo is genuinely never displayed while `pending` — reviewers test this by
uploading something and looking.

## 4.1 · One gap found while checking this

A profile is only *discoverable* once it is complete — `matching.service.ts` filters candidates on
`isComplete: true`, which is right. But that is the only place completeness is checked anywhere in
the backend, and it constrains who can be **seen**, not who can **look**.

So today, as far as the code goes, somebody with a half-finished profile can still browse other
people. Whether that is intended is a product decision: some apps deliberately let you look before
you commit, others require you to put yourself on the table before you see anyone. Worth settling
explicitly rather than by omission — and if the answer is "no", it is a guard on three endpoints.

Two more:

- **Location privacy.** Never expose precise coordinates of another user to the client. Distance
  bands computed server-side, always. A "1.2 km away" that the client computes is a "here is their
  address" waiting to be reverse-engineered.
- **Face verification.** AWS Rekognition Face Liveness is biometric data under GDPR Art. 9 —
  special category, needing explicit consent and a clear retention position. This is why we are
  pinned to eu-west-1.

---

# 5 · The minimum bar for a quality app

Not aspirational. This is the floor.

## Correctness
- [ ] State survives rotation, backgrounding and process death
- [ ] Every network call has a visible loading, empty, error and offline state
- [ ] Errors say what happened and what to do, never a raw code
- [ ] Back navigation always works, including the system gesture
- [ ] No hardcoded strings that need translating later

## Performance
- [ ] Cold start under 2 seconds on a mid-range device
- [ ] Scrolling holds 60fps on a 3-year-old phone, not a flagship
- [ ] Release build has R8 / optimisation on
- [ ] Baseline profile for the first-run path
- [ ] App size is deliberate — ours already carries ~950 KB of flags

## Accessibility (a legal requirement in the EU from June 2025 under the EAA, not a nice-to-have)
- [ ] Everything reachable and labelled for VoiceOver and TalkBack
- [ ] Layout survives the largest system font size
- [ ] Contrast meets WCAG AA — 4.5:1 for text, 3:1 for controls
- [ ] Tap targets at least 44pt / 48dp
- [ ] Respects reduce-motion

## Fit and layout
- [ ] Every screen tested from the smallest supported phone to the largest
- [ ] Safe areas respected — notch, Dynamic Island, gesture bar
- [ ] Landscape either supported or explicitly locked
- [ ] Nothing clipped when text is at maximum size

## Security
- [ ] Tokens in Keychain / EncryptedSharedPreferences, never plain storage
- [ ] No secrets in the binary
- [ ] Certificate pinning considered for auth endpoints
- [ ] Screenshots of sensitive screens blocked in the app switcher if warranted

## Release engineering
- [ ] CI builds both platforms on every PR — **iOS is our gap**
- [ ] Automated tests run in CI
- [ ] Crash reporting live from the first beta
- [ ] Staged rollout, and a tested rollback plan
- [ ] Signing keys backed up somewhere that survives a laptop dying

## Privacy of other people's data
- [ ] Never send another user's precise coordinates to the client. Distance bands computed
      server-side, always — a client-side "1.2 km away" is an address waiting to be
      reverse-engineered from three readings
- [ ] Photos never leave the server while `pending` moderation
- [ ] Biometric data (Face Liveness) has an explicit consent and a stated retention period

## Compliance
- [ ] In-app account deletion that genuinely deletes
- [ ] Privacy manifest complete, including every SDK
- [ ] Play Data Safety form matches reality
- [ ] Age gate appropriate to a dating app
- [ ] Consent before any analytics that needs it
- [ ] Open source licences screen — required by the ten Apache-2.0 libraries and two OFL fonts we
      already ship

---

# 6 · What I would do next, in order

Ordered for the current plan: **no new UI screens until there is a Mac.** That is the right call —
every screen built now is a screen built blind on half the product — and it also makes this a good
window for the work below, none of which needs one.

1. ~~Move Android to `targetSdk` 36.~~ **Done, 31 August 2026.**
2. **Switch on *Restrict Declared Minors* in Play Console.** The code side is already there; this
   is the part the app cannot do for itself.
3. **Turn on R8** in the release build, so the breakage is found while there is little code to fix.
4. **Adopt Swift 6 strict concurrency**, while there are twelve Swift files instead of a hundred.
   Cheap now, weeks later.
5. **Decide whether an incomplete profile may browse.** See 4.1 — today it may.
6. **Raise `minSdk`** from 24 to 26 or 28.
7. **When the Mac arrives:** iOS build and tests in CI first, then the iOS half of the layout
   audit, then reconsider native-×2 vs Kotlin Multiplatform before the screen count doubles.

---

## Sources

- [Target API level requirements for Google Play apps — Play Console Help](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Google Play target API requirements for Android apps (2026)](https://median.co/blog/google-plays-target-api-level-requirement-for-android-apps)
- [How to Comply with Google Play's New Dating App Age Rules](https://eidas-pro.com/blog/google-play-dating-app-age-rules-eid)
- [Google Play and App Store Age Verification for Apps in 2026](https://blog.webvify.app/blogs/age-verification-mobile-apps-2026/)
- [Apple: Account deletion requirement](https://developer.apple.com/news/?id=12m75xbj)
- [Apple: Privacy updates for App Store submissions](https://developer.apple.com/news/?id=3d8a9yyh)
- [Apple App Store Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- [Apple: Age requirements for apps distributed in Brazil, Australia, Singapore, Utah, Louisiana](https://developer.apple.com/news/?id=f5zj08ey)
- [Jetpack Compose Performance — Android Developers](https://developer.android.com/develop/ui/compose/performance)
- [Swift 6 Migration Guide: What Actually Breaks](https://medium.com/@dhruvinbhalodiya752/swift-6-migration-guide-what-actually-breaks-and-how-to-fix-it-e3ce1b269421)
- [The EU Commission's Approach to Age Verification and DSA Enforcement — FPF](https://fpf.org/blog/the-eu-commissions-approach-to-age-verification-mobile-apps-dsa-enforcement-and-challenging-national-social-media-bans/)

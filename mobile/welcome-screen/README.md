# ShowUp — Welcome screen (SHOWUP-117) · native UI

Two files, one screen — the first screen after account creation, built to `00-welcome-spec-sheet`.

```
ios/WelcomeView.swift      → iPhone version  (Swift + SwiftUI)
android/WelcomeScreen.kt   → Android version (Kotlin + Jetpack Compose)
```

Same design, written twice because iPhone and Android are different platforms with different languages.

---

## What these files are (and aren't)

- They are the **exact code for this one screen**, matching the spec (colours, type, layout, the
  sunset button, the two `flex:1` spacers, safe-area anchoring).
- They are **not the ShowUp app.** When the real app repositories exist, these two source files move
  into them and the two project folders here can be deleted.

## How to run it

Both platforms have a ready-to-open project — nothing to assemble:

| Platform | Open this | Needs |
| --- | --- | --- |
| Android | `android-preview-project/` | Android Studio (Windows, macOS or Linux) |
| iOS | `ios-app/ShowUpWelcome.xcodeproj` | a Mac with Xcode |
| Neither | `preview/open-me-offline.html` | just a browser — double-click it |

Step-by-step instructions for both are in **`../README.md`** (the `mobile/` folder).

Fonts are already wired into both projects. There are no third-party dependencies on either side —
iOS uses only SwiftUI, Android only Compose.

**The source of truth is `android/WelcomeScreen.kt` and `ios/WelcomeView.swift`.** The copies inside
the project folders exist so the projects build; edit the source files and copy across, or the
change is lost when the real app projects are created.

## Fonts — included, not a dependency

**Lora** (wordmark, headline) and **Manrope** (everything else) are in `fonts/`. Both are SIL Open
Font Licence, so they ship with the project; the licences are alongside them.

They are static cuts generated from the official variable fonts at exactly the weights the spec
uses — 400/700 for Lora, 500/600/700 for Manrope. Static rather than variable because selecting a
weight from a variable font needs Android API 26+, and below that it silently renders Regular.

**iOS** — drag the seven `.ttf` files into the app target (tick *Copy items if needed* and the
target under *Add to targets*), then list each filename under `UIAppFonts` in `Info.plist`.

The names the code asks for are **PostScript names, not filenames**, and two are not what you would
guess:

| File | PostScript name to use |
| --- | --- |
| `Lora-Regular.ttf` | `Lora-Regular` |
| `Lora-Bold.ttf` | `Lora-Bold` |
| `Lora-Italic.ttf` | **`LoraItalic-Italic`** |
| `Lora-BoldItalic.ttf` | **`LoraItalic-BoldItalic`** |
| `Manrope-Medium.ttf` | `Manrope-Medium` |
| `Manrope-SemiBold.ttf` | `Manrope-SemiBold` |
| `Manrope-Bold.ttf` | `Manrope-Bold` |

They are already correct in `WelcomeView.swift` (the `PS` enum). To confirm after adding them:

```swift
for f in UIFont.familyNames.sorted() { print(f, UIFont.fontNames(forFamilyName: f)) }
```

**Android** — copy the files into `app/src/main/res/font/`, renamed to lowercase with underscores
(Android resource names allow nothing else):

```
Lora-Regular.ttf     -> lora_regular.ttf        Manrope-Medium.ttf   -> manrope_medium.ttf
Lora-Bold.ttf        -> lora_bold.ttf           Manrope-SemiBold.ttf -> manrope_semibold.ttf
Lora-Italic.ttf      -> lora_italic.ttf         Manrope-Bold.ttf     -> manrope_bold.ttf
Lora-BoldItalic.ttf  -> lora_bold_italic.ttf
```

Both files now reference the fonts directly instead of falling back to the system typeface. A
missing font therefore **fails the build** rather than quietly rendering in the wrong face, which
is how a screen ships looking nothing like its spec.

## The heart

There is no image asset. The heart, its highlight, the two accent dots and the sparkle are all
**drawn in code** from the same curves on both platforms — `HeartShape` in Swift, `heartPath()` in
Kotlin, matching the design SVG's 200 × 190 coordinates. It is therefore resolution-independent,
identical across platforms, and there is no PNG to export, scale at 2x/3x, or let drift.

## Still to wire (out of scope for this screen)

- Navigation: `onContinue` should push **onboarding card 01**.
- Analytics: the two tracking calls (`screen_view`, `cta_click`) are marked as comments — connect
  them to PostHog when analytics is set up.
- The shared design-system `Button` (variant `sunset`, size `lg`) — here it's a local `SunsetButton`;
  fold it into the app's component library when that exists.

## The easiest way to show a non-technical stakeholder — no install

Use the **web preview** — opens in any browser, nothing to install, shows the card at all three
device sizes side by side with the colour/type/spacing legend underneath:

**https://claude.ai/code/artifact/817501e4-3595-47fb-a9c0-b36839e7ad7b**

It is HTML/CSS, not part of the app. It exists so the layout can be measured and screenshotted
without a Mac, and so the three acceptance-criteria screenshots can be produced at all.

---

## Fixes — 19 Aug 2026

Reviewed both files against the SHOWUP-117 acceptance criteria. Three bugs and two spec gaps:

- **Android — orbs rendered as hard-edged circles below Android 12.** `Modifier.blur()` is a no-op
  under API 31. Replaced with radial gradients that fall off in alpha, which look the same on every
  API level and dither better against the banding the AC calls out.
- **iOS — headline line-height was ~1.24, spec says 1.05.** `.lineSpacing()` only *adds* to the
  font's own leading, so it cannot express a line-height tighter than the font already has. Now set
  absolutely through a paragraph style (`TypeMetrics`). Applied to the body copy too, so it matches
  Compose's `lineHeight = 24.sp`.
- **iOS — background orbs used centre-relative hard-coded Y offsets.** They landed differently on a
  667-tall frame than a 932-tall one. Now anchored to the screen corners, as Android already was.
- **Both — the headline underline accent (spec ⑤) was missing.** Added.
- **Android — the sparkle beside the heart was missing.** iOS had it; drawn on Canvas here.

Still open, needs design rather than code: the **button label size**. Both files use 17; the spec
sheet only says "Manrope Bold white" and never states a size.

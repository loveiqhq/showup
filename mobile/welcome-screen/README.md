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
- They are **not a runnable app on their own.** A screen lives *inside* an app project. A mobile
  developer copies the file into the ShowUp iOS / Android project, and it becomes part of the app.

## How a developer previews it (this is where you SEE it)

**iPhone (SwiftUI) — needs a Mac + Xcode (both free):**
1. Add `WelcomeView.swift` to the iOS project.
2. Open it in **Xcode** → the **Preview canvas** on the right renders the screen live (from the
   `#Preview` at the bottom of the file).
3. Or press ▶ to run it on the **iPhone Simulator** (a virtual iPhone on screen).

**Android (Compose) — needs Android Studio (free, runs on Windows too):**
1. Add `WelcomeScreen.kt` to the Android project.
2. Open it in **Android Studio** → **Split/Design** view shows the **@Preview** live.
3. Or press ▶ to run it on the **Android Emulator** (a virtual Android phone).

Both previews update instantly as the code changes — that's the fastest way to see and screenshot it.

## Fonts

The design uses **Lora** (headline/wordmark) and **Manrope** (everything else). Add both font files
to each project, then:
- iOS: replace the `.custom("Lora-…" / "Manrope-…")` names — they already point at the right names.
- Android: replace `FontFamily.Serif` / `FontFamily.SansSerif` with `FontFamily(Font(R.font.lora_…))`.

Until the fonts are added, both files fall back to the system serif/sans so they still render.

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

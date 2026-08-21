# Android preview project — SHOWUP-117 welcome card

A minimal Android project whose only purpose is to render `WelcomeScreen.kt` so it can be looked at
and screenshotted. It is **not** the ShowUp app — when the real Android app exists, the screen file
moves there and this folder can be deleted.

Everything is already wired: the fonts are in `res/font/` under their Android names, the Compose
dependencies are declared, and there are previews for all three device sizes in the acceptance
criteria.

---

## How to open it

1. Install Android Studio from **developer.android.com/studio** — accept the defaults, choose the
   **Standard** setup, and let it download the SDK.
2. **File ▸ Open**, and select this folder (`android-preview-project`). Not the parent folder, not a
   file inside it — this folder.
3. Wait for **Gradle sync**. First time it downloads Gradle and the Compose libraries, so give it
   5–15 minutes. The progress bar is at the bottom.
4. Open `app/src/main/java/com/showup/onboarding/WelcomeScreen.kt`.
5. Click **Split** or **Design** at the top right of the editor. The three previews render on the
   right — 375 × 667, 390 × 844 and 430 × 932.

To see it on a phone instead of in the preview panel, press ▶ and pick an emulator. Android Studio
will offer to create one.

---

## If Gradle sync complains

The versions here were pinned at the time of writing:

| | Version | Constraint |
| --- | --- | --- |
| Android Gradle Plugin | 8.7.3 | must be ≥ 8.6.0 for `compileSdk 35` |
| Gradle | 8.9 | AGP 8.7 requires ≥ 8.9 |
| Kotlin | 2.0.21 | must equal the Compose plugin version |
| Compose compiler plugin | 2.0.21 | must equal Kotlin |
| Compose BOM | 2024.10.00 | |
| compileSdk / targetSdk | 35 | |
| minSdk | 24 | |
| JDK | 17 | bundled with Android Studio |

These four move together — AGP, Gradle, Kotlin and the Compose plugin. Changing one usually means
changing another, which is why the constraints are written down.

If Android Studio is newer, it will offer an **AGP Upgrade Assistant** — accept it. That is the
normal path and it edits the two `build.gradle.kts` files for you.

`minSdk` is deliberately 24 rather than 31. The background orbs had a bug that only showed on
Android 11 and older, so the project should be able to target those devices in order to test them.

---

## What to check when it renders

The layout is the risky part, so it is worth measuring rather than glancing:

| Thing | Must be |
| --- | --- |
| Wordmark below the safe-area top | 20 |
| Heart → headline | 20 |
| Headline → subhead → body | 16 each |
| Button → caption | 10 |
| Caption above the content floor | 20 |
| Left/right gutter | 24 |
| Headline line-height | 44 (42 × 1.05) |
| Button height | 56 |

And on every one of the three frames: nothing clipped, no scrolling, the CTA fully visible.

Compare against `../screenshots/` and the browser preview in `../preview/` — those are the target.
If the Android render differs, the Android render is what is wrong.

---

## Known — not bugs

- **The CTA goes nowhere.** Onboarding card 01 does not exist yet, so `onContinue` is empty.
- **Tracking is commented out.** Analytics events are out of scope for this ticket, and there is no
  ingest endpoint or consent capture yet.
- **`SunsetButton` is local to this file.** It becomes a shared design-system component with variant
  `sunset` / size `lg` when a component library exists.

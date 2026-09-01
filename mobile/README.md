# mobile/

Screen implementations for the ShowUp iOS and Android apps, kept here until those app
repositories exist. **Nothing in this folder is built or run by the backend.**

There are **six screens** — the full tutorial flow:

| # | Screen | Ticket |
|---|---|---|
| 1 | Welcome | SHOWUP-117 |
| 2 | Meet in real life | SHOWUP-135 |
| 3 | Match on availability | SHOWUP-136 |
| 4 | Match means meet | SHOWUP-137 |
| 5 | 30 minutes | SHOWUP-138 |
| 6 | Show up, every time | SHOWUP-139 |

Note the wording: this is the **tutorial**, the short tour explaining how Show Up works. It is not
*onboarding*, which is the separate flow where someone completes their profile. The two are tracked
separately, so keeping the names apart matters.

```
mobile/welcome-screen/
  android-preview-project/   ← open THIS in Android Studio
  ios-app/                   ← open THIS in Xcode
  shared/                    ← the shared shell, both platforms (source of truth)
  screen-01..06-*/           ← per-screen sources (source of truth)
  fonts/                     ← Lora + Manrope, already wired into both projects
  preview/                   ← browser previews, nothing to install
  screenshots/               ← every screen at 375x667, 390x844, 430x932
  audit/                     ← the spec audit + a re-runnable conformance check
```

---

## Just want to look at it? Nothing to install

Open **`welcome-screen/preview/all-six-screens.html`** in any browser — double-click it.

All six screens at all three device sizes. Fonts are embedded, so it works offline and survives
being emailed. Use this to see or approve the design; the two projects below are for running the
real app code.

---

## Android — run it on an emulator

**You need:** Android Studio (free; Windows, macOS, Linux) from
<https://developer.android.com/studio>. Install with the **Standard** setup and let it download the
SDK.

### 1. Open the project

**File ▸ Open**, then select this exact folder:

```
mobile/welcome-screen/android-preview-project
```

The folder itself — not the parent, not a file inside it.

### 2. Let Gradle sync

It starts automatically; progress is at the bottom of the window. The first run downloads Gradle
and the Compose libraries — allow **5–15 minutes**.

> ### ⚠ If Gradle fails with a bare version number
>
> An error whose entire message is a Java version — for example:
> ```
> * What went wrong:
> 25.0.2
> ```
> means Gradle is running on a **JDK too new for it**. Gradle 8.9 (pinned here) does not support
> **JDK 25**, which recent Android Studio versions bundle as their JBR. It is not a problem with
> this project, and the message never says so.
>
> **Fix:** **Settings ▸ Build, Execution, Deployment ▸ Build Tools ▸ Gradle**, and set
> **Gradle JDK** to a **JDK 17 or 21**. Android Studio can download one from that same dropdown
> (*Download JDK…*). This was hit during development on JDK 25; JDK 21 works.

### 3. Pick an emulator

The toolbar device dropdown will say **No Devices** on a fresh install:

- **Tools ▸ Device Manager**
- **+ ▸ Create Virtual Device**
- Pick **Pixel 8** (close to the 390 × 844 reference frame), then a system image — **API 34 or 35**.
  The ⬇ arrow downloads it (**~1.5 GB**, one time). Avoid preview/beta images.
- **Finish**, then select the device in the toolbar

### 4. Run

Press green **▶**. The app opens on Welcome; each button advances. Back works from screen 3 onward,
as does the system back gesture.

### Or skip the emulator

Open any file in `app/src/main/java/com/showup/tutorial/` whose name ends in `Screen.kt`, then
click **Split** (top right). Three device sizes render at once, no emulator needed.

**Previews exist only in the six `*Screen.kt` files** — 18 in total. `TutorialShell.kt`,
`TutorialAnalytics.kt`, `DesignSystem.kt` and `MainActivity.kt` have none, because they draw no
screen of their own. "No preview found" on those files is correct, not an error.

Previews are static, so **the animations only show on the emulator**.

### Command line, if you prefer

```
cd mobile/welcome-screen/android-preview-project
./gradlew :app:assembleDebug          # macOS / Linux
gradlew.bat :app:assembleDebug        # Windows
```

Same JDK caveat applies — set `JAVA_HOME` to a JDK 17 or 21.

**Fonts:** nothing to do. They are in `app/src/main/res/font/`.

---

## iOS — run it on a simulator

**You need:** a **Mac** with **Xcode** (free, Mac App Store). Xcode does not exist for Windows —
there is no way around this.

### 1. Open the project

```
mobile/welcome-screen/ios-app/ShowUpWelcome.xcodeproj
```

No `.xcworkspace`, no CocoaPods, no Swift Package dependencies — pure SwiftUI, so nothing to fetch.

### 2. Pick an iPhone simulator

Next to the scheme name **ShowUpWelcome**, choose any iPhone. **iPhone SE (3rd gen)** is the useful
one — at 375 × 667 it is the frame where these layouts fail first.

### 3. Run

**▶** or `Cmd + R`.

**Or skip the simulator:** open any `*View.swift` and press `Cmd + Option + Enter` for the preview
canvas, then **Resume**.

**Fonts:** nothing to do — the seven `.ttf` files are in the target and listed under `UIAppFonts`.

**Signing:** the simulator needs none. Only a physical iPhone does — target ▸ **Signing &
Capabilities** ▸ pick your team.

> ### ⚠ This has never been compiled
>
> It was written on Windows, where no Xcode exists. The structure is verified and the project file
> validates, but no compiler has ever read it. **Expect to fix something on first build**, and
> please report what — it cannot be found from the authoring machine.

---

## What you are looking at

- **The illustrations are placeholders.** All six show a dashed 248 × 210 box. The artwork is
  design's to supply; the block holds the exact space it will occupy, so dropping it in changes no
  layout.
- **The final CTA restarts the tour.** The post-tutorial destination is not yet decided.
- **No analytics reach anywhere.** The call sites are real code (`TutorialAnalytics`) but the
  default tracker does nothing — there is no ingest endpoint and no consent capture yet.
- **Three animations**, all of which honour the OS reduce-motion setting: cards slide a sixth of a
  width in the direction of travel, progress segments tween, and the CTA circle dips on press.
- Every screen must show all content with nothing clipped and no scrolling at **375 × 667**,
  **390 × 844** and **430 × 932**. Compare against `welcome-screen/screenshots/`.

---

## Checking it still matches the design

```
cd mobile/welcome-screen
python audit/verify-spec.py
```

166 checks: colour tokens down to their alpha bytes, the type scale, the fixed spacing, per-screen
step / gap / underline width / art scale, and every copy string character for character including
the `Show-up Rate` casing. Run it after any change to the screens.

`audit/AUDIT-2026-08-23.md` records the audit these checks came from, what was found, and the two
items still blocked on other people.

---

## Which files are the real ones

`shared/` and `screen-01..06-*/` are the source of truth. The copies inside the two project folders
exist so the projects can build.

**If you change a screen, change the source-of-truth file and copy it across** — otherwise the edit
is lost when the real app projects are created. `verify-spec.py` reads the project copies, so a
drift between the two will not be caught by it.

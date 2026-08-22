# mobile/

Screen implementations for the ShowUp iOS and Android apps, kept here until those app
repositories exist. **Nothing in this folder is built or run by the backend.**

Right now there is one screen: the welcome card that appears after account creation
(**SHOWUP-117**, tutorial card 1).

Note the wording: this is the **tutorial**, the short tour explaining how Show Up works. It is not
*onboarding*, which is the separate flow where someone completes their profile. The two are tracked
separately, so keeping the names apart matters.

```
mobile/welcome-screen/
  android-preview-project/   ← open THIS in Android Studio
  ios-app/                   ← open THIS in Xcode
  android/WelcomeScreen.kt   ← the screen (source of truth)
  ios/WelcomeView.swift      ← the screen (source of truth)
  fonts/                     ← Lora + Manrope, already wired into both projects
  preview/                   ← browser preview, no install needed
  screenshots/               ← the card at 375x667, 390x844, 430x932
```

---

## Just want to look at it? No install needed

Open **`welcome-screen/preview/open-me-offline.html`** in any browser — double-click it. It shows
the card at all three device sizes, works offline, and needs nothing installed.

Use this if you only want to see or approve the design. The two projects below are for running the
real app code on an emulator.

---

## Android — run it on an emulator

**You need:** Android Studio (free, works on Windows, macOS and Linux) from
<https://developer.android.com/studio>. Install it with the **Standard** setup and let it download
the SDK.

**1. Open the project**

In Android Studio: **File ▸ Open**, then select this exact folder:

```
mobile/welcome-screen/android-preview-project
```

Select the folder itself — not the parent, not a file inside it.

**2. Let Gradle sync**

It starts automatically and the progress shows at the bottom of the window. The first run downloads
Gradle and the Compose libraries, so allow **5–15 minutes**. Wait for it to finish before doing
anything else.

If it offers an **"AGP Upgrade Assistant"**, accept it. That is normal when your Android Studio is
newer than the versions pinned here, and it edits the build files for you.

**3. Pick an emulator**

In the toolbar at the top there is a device dropdown. If it is empty:

- Open **Tools ▸ Device Manager**
- Click **Add a virtual device** (the `+`)
- Pick any phone — **Pixel 6** is a good default — then a system image (it will download one)
- Click **Finish**, and select that device in the toolbar dropdown

**4. Run**

Press the green **▶ Run** button. The emulator boots and the welcome card appears.

**Or skip the emulator:** open
`app/src/main/java/com/showup/tutorial/WelcomeScreen.kt` and click **Split** at the top right.
Previews for all three device sizes render instantly, without an emulator.

**Fonts:** nothing to do. They are already in `app/src/main/res/font/`.

---

## iOS — run it on a simulator

**You need:** a **Mac** with **Xcode** (free from the Mac App Store). Xcode does not exist for
Windows — there is no way around this.

**1. Open the project**

Double-click:

```
mobile/welcome-screen/ios-app/ShowUpWelcome.xcodeproj
```

Or in Xcode: **File ▸ Open** and select that `.xcodeproj`. There is no `.xcworkspace` and no
CocoaPods or Swift Package dependencies — the project uses only SwiftUI, so nothing needs
installing or fetching.

**2. Pick an iPhone simulator**

At the top of the Xcode window, next to the scheme name **ShowUpWelcome**, there is a device
dropdown. Choose any iPhone — **iPhone 15** or **iPhone 15 Pro Max** are good. Simulators come with
Xcode; no download needed.

**3. Run**

Press **▶** (or `Cmd + R`). The simulator boots and the welcome card appears.

**Or skip the simulator:** open `ShowUpWelcome/WelcomeView.swift` and press
`Cmd + Option + Enter` to show the preview canvas, then **Resume**.

**Fonts:** nothing to do. The seven `.ttf` files are in the target and already listed under
`UIAppFonts` in `Info.plist`.

**If Xcode complains about signing:** running on the *simulator* needs no signing. Only running on
a physical iPhone does — for that, select the **ShowUpWelcome** target ▸ **Signing & Capabilities**
and pick your team.

---

## What you are looking at

One screen, on its own. There is no ShowUp app to tap through yet.

- **The button goes nowhere on purpose.** Tutorial card 2 does not exist yet; it is out of scope
  for SHOWUP-117.
- **No analytics reach anywhere.** The call sites are real code now (`TutorialAnalytics`), but the
  default tracker does nothing — there is no ingest endpoint and no consent capture yet.
- The screen must show all its content with nothing clipped and no scrolling at **375 × 667**,
  **390 × 844** and **430 × 932**. Compare against `welcome-screen/screenshots/`.

---

## Which files are the real ones

`welcome-screen/android/WelcomeScreen.kt` and `welcome-screen/ios/WelcomeView.swift` are the source
of truth. The copies inside the two project folders exist so the projects can build.

**If you change the screen, change the source-of-truth file and copy it across** — otherwise the
edit is lost when the real app projects are created.

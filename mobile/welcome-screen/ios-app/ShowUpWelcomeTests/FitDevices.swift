//  FitDevices.swift
//  ShowUp · the phones the app has to fit on
//
//  The iOS copy of the matrix in `Devices.kt`, shared by every fit suite in this target so the
//  two cannot measure different phones. `audit/verify-welcome.py` compares this list against the
//  Kotlin one row for row — a copy nobody compares is a copy that drifts, and a drifted row means
//  one platform is being measured on a phone the other never sees.

import CoreGraphics
import Foundation

/// One phone the app has to fit on.
///
/// Insets are not decoration: the status bar and home indicator take up to 96pt on the taller
/// iPhones, so a harness rendering into the full frame is optimistic by exactly the amount that
/// decides whether a bottom band fits.
struct FitDevice {
    let name: String
    let width: CGFloat
    let height: CGFloat
    let top: CGFloat
    let bottom: CGFloat

    /// Usable height once the system bars have taken theirs.
    var safeHeight: CGFloat { height - top - bottom }
    var described: String { "\(name) (\(Int(width))x\(Int(height)))" }
}

/// The same seventeen rows as `Devices.kt`, smallest first because that is where things break.
let fitDevices: [FitDevice] = [
    FitDevice(name: "Galaxy Fold cover screen", width: 320, height: 686, top: 24, bottom: 24),
    FitDevice(name: "small Android (HD)", width: 360, height: 640, top: 24, bottom: 24),
    FitDevice(name: "Galaxy A / common Android", width: 360, height: 740, top: 24, bottom: 24),
    FitDevice(name: "iPhone 12 mini / 13 mini", width: 360, height: 780, top: 50, bottom: 34),
    FitDevice(name: "common modern Android", width: 360, height: 800, top: 24, bottom: 24),
    FitDevice(name: "iPhone SE (3rd gen)", width: 375, height: 667, top: 20, bottom: 0),
    FitDevice(name: "iPhone X / XS / 11 Pro", width: 375, height: 812, top: 44, bottom: 34),
    FitDevice(name: "iPhone 12 / 13 / 14", width: 390, height: 844, top: 47, bottom: 34),
    FitDevice(name: "Pixel 4a / 5", width: 393, height: 851, top: 24, bottom: 24),
    FitDevice(name: "iPhone 15 / 16", width: 393, height: 852, top: 59, bottom: 34),
    FitDevice(name: "iPhone 16 Pro", width: 402, height: 874, top: 62, bottom: 34),
    FitDevice(name: "Pixel 6 / 7", width: 411, height: 891, top: 24, bottom: 24),
    FitDevice(name: "Pixel 7 Pro / 8 Pro", width: 412, height: 915, top: 24, bottom: 24),
    FitDevice(name: "iPhone XR / 11", width: 414, height: 896, top: 48, bottom: 34),
    FitDevice(name: "iPhone 12/13/14 Pro Max", width: 428, height: 926, top: 47, bottom: 34),
    FitDevice(name: "iPhone 15/16 Pro Max", width: 430, height: 932, top: 59, bottom: 34),
    FitDevice(name: "iPhone 16 Pro Max", width: 440, height: 956, top: 62, bottom: 34),
]

//  WHY THERE IS NO ELEMENT-LEVEL FIT HARNESS ON iOS
//
//  Android's FitHarness.kt measures every screen element at every one of these sizes: text
//  clipped by its box, text collapsed to zero height, a tap target below the minimum. On
//  10 September 2026 three attempts were made to give iOS the same thing, and all three were run
//  in CI. None of them work, and the reasons are worth keeping so the fourth attempt starts
//  somewhere new.
//
//    1. `UIHostingController.view.accessibilityElements` — empty on every screen, at every size.
//    2. The other accessibility-container form, `accessibilityElementCount()` and
//       `accessibilityElement(at:)` — also empty. SwiftUI does not build an accessibility tree
//       for a hosting controller in a unit test unless an assistive technology is running, and
//       there is no public way to ask it to.
//    3. The raw UIView hierarchy, comparing a constrained render against an unconstrained one.
//       This one returned data, and then returned the reason it cannot work: a whole ShowUp
//       screen publishes THREE TO SIX UIViews. SwiftUI draws into a display list rather than
//       composing a view per element, so the legal line and the help line — the two things the
//       Android sweep found at zero height — are not views at all and cannot be measured. The
//       few real views it did find produced only false positives: our own `WashLabel` wrapper
//       reported as squeezed 45 times because the headline deliberately steps 40pt to 34pt below
//       a 700pt frame, and a `UITextField` reported as overflowing because it scrolls its own
//       content.
//
//  What would actually work is XCUITest. A UI test drives the real app with accessibility
//  genuinely enabled, so `XCUIElement.frame` is populated and every label is addressable. That
//  needs a UI test target, which this project does not have, and it runs the app rather than
//  hosting a view — a different and much heavier thing than the unit suites here. It is the right
//  next step and it is its own piece of work.
//
//  Until then: `ScreenFitTests.swift` measures the CTA in pixels across all seventeen sizes,
//  which is real coverage of the one control that matters most, and Android measures everything
//  else. Both apps render the same layouts from the same tokens, so a layout that breaks on one
//  is worth checking on the other by hand.

//  FitHarness.swift
//  ShowUp · renders a screen at a given phone size and reports what does not fit
//
//  The iOS counterpart to FitHarness.kt, added 10 September 2026. Until now the only thing iOS
//  measured was whether the CTA moved and whether it was on screen — a pixel probe over a
//  rendered image, which cannot see a label that was cut, a line that collapsed to nothing, or a
//  button squeezed under the tap minimum. Android has caught all three since August. On the day
//  Android's backlog reached zero, "iOS is clean" was still a sentence nobody could say.
//
//  HOW THIS MEASURES CLIPPING WITHOUT KNOWING ANYTHING ABOUT FONTS
//
//  Android asks the renderer directly: Compose hands back a TextLayoutResult that says the
//  paragraph is taller than the box it was drawn in. UIKit exposes no such thing through SwiftUI,
//  and reconstructing it would mean knowing each label's font, weight, tracking and line height
//  at the call site — which is the sort of parallel truth that goes stale in a week.
//
//  So the screen is rendered TWICE: once into the device's safe area, and once into the same
//  width with effectively unlimited height. The second render is what every element wants to be.
//  An element shorter in the first than in the second was squeezed, and by exactly the
//  difference. No font metrics, no per-screen knowledge, and it reports the overshoot in points
//  the same way the Android harness does.
//
//  WHAT IS MEASURED
//
//    1. an element squeezed below its natural height   (the clipping case, and the common one)
//    2. an element collapsed to nothing                (zero height, so it is simply gone)
//    3. a tap target below the 44pt minimum
//    4. an element positioned outside the safe area    (advisory inside a scroll view — reachable)
//
//  KNOWN BOUNDARY, and it is the same one Android has: only what the accessibility tree publishes
//  is visible here. That is every label, every button and every described icon, and nothing
//  purely decorative. It is the right boundary — the question is whether what a person reads and
//  taps survives the screen size — but it is a boundary, and a control with no accessibility
//  identity is invisible to this file as surely as it is to VoiceOver.

import XCTest
import SwiftUI
import UIKit

// MARK: - the device matrix

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
///
/// Copied rather than shared, because the two toolchains cannot see each other's source. The two
/// lists agreeing is checked by `audit/verify-welcome.py`, not by hope.
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

// MARK: - findings

/// One thing in the wrong place or the wrong size, named well enough to find it in the source.
struct FitViolation: CustomStringConvertible {
    let device: FitDevice
    let screen: String
    let element: String
    let problem: String
    let detail: String
    /// Advisory findings are worth seeing but are not failures.
    var advisory: Bool = false

    var description: String {
        let head = advisory ? "note " : "FAIL "
        return head
            + device.described.padded(to: 36) + " "
            + screen.padded(to: 22) + " "
            + String(element.prefix(44)).padded(to: 44) + " "
            + problem.padded(to: 22) + " " + detail
    }
}

private extension String {
    func padded(to n: Int) -> String {
        count >= n ? self : self + String(repeating: " ", count: n - count)
    }
}

// MARK: - the instrument

/// A tolerance, because sub-pixel rounding is not a bug. Anything past this is real.
private let fitSlack: CGFloat = 0.75

/// Apple's stated minimum and the smallest value this design uses on purpose.
private let minTapPt: CGFloat = 44

/// The height used for the "what does this want to be" render.
///
/// Large enough that nothing is ever constrained by it, small enough that a runaway layout fails
/// the test rather than the process.
private let unconstrainedHeight: CGFloat = 12_000

/// A window whose safe-area insets are stated rather than inherited.
///
/// `additionalSafeAreaInsets` is additive, and a window in a test inherits the host simulator's
/// own top inset — which showed up in the pixel probe as every device reporting 54pt less height
/// than it should, uniformly enough to look correct and be wrong everywhere.
final class FitInsetWindow: UIWindow {
    var fixedInsets: UIEdgeInsets = .zero
    override var safeAreaInsets: UIEdgeInsets { fixedInsets }
}

/// One measured element: what it says, where it ended up, and whether it is tappable.
private struct Probe {
    let label: String
    let frame: CGRect
    let isButton: Bool
    /// Whether any ancestor was a scroll view, so a position past the fold is reachable.
    let inScroll: Bool
}

/// Hosts `view` at a given size and returns everything the accessibility tree publishes.
///
/// `@MainActor` because every line of it is UIKit. The project builds with
/// SWIFT_STRICT_CONCURRENCY = complete, so this is stated rather than assumed.
@MainActor
private func probes(of view: some View, width: CGFloat, height: CGFloat,
                    insets: UIEdgeInsets) -> [Probe] {
    let controller = UIHostingController(rootView: AnyView(view))
    // The design is a light-mode design; a dark-mode run measures a layout nobody ships.
    controller.overrideUserInterfaceStyle = .light
    let window = FitInsetWindow(frame: CGRect(x: 0, y: 0, width: width, height: height))
    window.fixedInsets = insets
    window.rootViewController = controller
    window.isHidden = false
    window.layoutIfNeeded()
    controller.view.setNeedsLayout()
    controller.view.layoutIfNeeded()

    var found: [Probe] = []

    // Both shapes have to be handled. SwiftUI publishes some elements as accessible UIViews and
    // others as UIAccessibilityElement objects hung off a container's `accessibilityElements`,
    // and which one you get is an implementation detail that has changed between releases.
    func walk(_ node: Any, inScroll: Bool) {
        if let element = node as? UIAccessibilityElement {
            append(label: element.accessibilityLabel,
                   frame: element.accessibilityFrame,
                   traits: element.accessibilityTraits,
                   inScroll: inScroll)
            return
        }
        guard let view = node as? UIView else { return }
        let scrolled = inScroll || view is UIScrollView
        if view.isAccessibilityElement {
            append(label: view.accessibilityLabel,
                   frame: view.accessibilityFrame,
                   traits: view.accessibilityTraits,
                   inScroll: scrolled)
        }
        // `accessibilityElements`, when a view sets it, REPLACES the subtree for accessibility
        // purposes, so descending into subviews as well would double-count.
        if let published = view.accessibilityElements, !published.isEmpty {
            published.forEach { walk($0, inScroll: scrolled) }
        } else {
            view.subviews.forEach { walk($0, inScroll: scrolled) }
        }
    }

    func append(label: String?, frame: CGRect, traits: UIAccessibilityTraits, inScroll: Bool) {
        guard let label, !label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }
        guard frame.width.isFinite, frame.height.isFinite else { return }
        found.append(Probe(label: "\"\(label.replacingOccurrences(of: "\n", with: " "))\"",
                           frame: frame,
                           isButton: traits.contains(.button),
                           inScroll: inScroll))
    }

    walk(controller.view, inScroll: false)
    return found
}

/// Renders `view` into `device`'s SAFE area and returns everything wrong with the result.
///
/// - Parameter screen: the scenario name, used only in the report.
@MainActor
func measureFit(_ device: FitDevice, _ screen: String, _ view: some View) -> [FitViolation] {
    let insets = UIEdgeInsets(top: device.top, left: 0, bottom: device.bottom, right: 0)
    let actual = probes(of: view, width: device.width, height: device.height, insets: insets)
    // The same screen with room to be whatever it wants. Insets are kept so the two layouts make
    // the same decisions about safe-area padding; only the height differs.
    let natural = probes(of: view, width: device.width, height: unconstrainedHeight, insets: insets)

    // Matched by label and by ordinal, so two buttons reading the same thing do not swap.
    var naturalHeights: [String: [CGFloat]] = [:]
    for probe in natural {
        naturalHeights[probe.label, default: []].append(probe.frame.height)
    }
    var seen: [String: Int] = [:]

    var found: [FitViolation] = []
    let safeTop = device.top
    let safeBottom = device.top + device.safeHeight

    for probe in actual {
        let index = seen[probe.label, default: 0]
        seen[probe.label] = index + 1
        let wanted = naturalHeights[probe.label].flatMap { $0.indices.contains(index) ? $0[index] : $0.first }

        // 2. collapsed to nothing at all
        if probe.frame.height <= 0 {
            if (wanted ?? 0) > 0 {
                found.append(FitViolation(device: device, screen: screen, element: probe.label,
                                          problem: "TEXT COLLAPSED", detail: "zero height"))
            }
            continue
        }

        // 1. squeezed below what it wants to be
        if let wanted, wanted - probe.frame.height > fitSlack {
            found.append(FitViolation(
                device: device, screen: screen, element: probe.label,
                problem: "TEXT CLIPPED",
                detail: String(format: "%.0fpt of %.0f not drawn", wanted - probe.frame.height, wanted)))
        }

        // 3. a tap target squeezed below the stated minimum
        if probe.isButton && probe.frame.height < minTapPt - fitSlack {
            found.append(FitViolation(
                device: device, screen: screen, element: probe.label,
                problem: "TAP TARGET TOO SMALL",
                detail: String(format: "%.1fpt, unusable below %.0f", probe.frame.height, minTapPt)))
        }

        // 4. outside the safe area. Below the fold of something that scrolls is a scroll, not a
        //    loss, so it is reported and not failed — exactly as FitHarness.kt does it.
        if probe.frame.maxY > safeBottom + fitSlack {
            let over = probe.frame.maxY - safeBottom
            found.append(probe.inScroll
                ? FitViolation(device: device, screen: screen, element: probe.label,
                               problem: "BELOW THE FOLD",
                               detail: String(format: "by %.1fpt, reachable by scrolling", over),
                               advisory: true)
                : FitViolation(device: device, screen: screen, element: probe.label,
                               problem: "OFF THE BOTTOM", detail: String(format: "by %.1fpt", over)))
        }
        if probe.frame.minY < safeTop - fitSlack {
            found.append(FitViolation(
                device: device, screen: screen, element: probe.label, problem: "OFF THE TOP",
                detail: String(format: "by %.1fpt", safeTop - probe.frame.minY)))
        }
        if probe.frame.maxX > device.width + fitSlack {
            found.append(FitViolation(
                device: device, screen: screen, element: probe.label, problem: "OFF THE RIGHT",
                detail: String(format: "by %.1fpt", probe.frame.maxX - device.width)))
        }
        if probe.frame.minX < -fitSlack {
            found.append(FitViolation(
                device: device, screen: screen, element: probe.label, problem: "OFF THE LEFT",
                detail: String(format: "by %.1fpt", -probe.frame.minX)))
        }
    }

    // One report per element and problem, as the Android harness does: the same squeezed label
    // reached through three ancestors is one finding, not three.
    var seenKeys = Set<String>()
    return found.filter { seenKeys.insert($0.element + "|" + $0.problem).inserted }
}

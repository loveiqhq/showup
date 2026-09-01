//  ScreenFitTests.swift
//  ShowUp · every phone the app has to run on, measured rather than eyeballed
//
//  The iOS answer to "does it actually fit", and the twin of ScreenFitTest.kt / Devices.kt on
//  Android — same device matrix, same intent. Android has had this since the tutorial screens
//  landed; iOS had nothing, and the cost of that showed up on 2026-09-01, when running the app for
//  the first time found two layout faults that every text-based audit had passed for weeks.
//
//  WHAT IT GUARDS, and why these and not others.
//
//  SHOWUP-143 states one layout rule outright: the CTA does not move when a state goes wrong. Both
//  screens broke it, in different ways and by different amounts, and neither was visible in the
//  source — one was a `frame(minHeight:)` wrapped around a nil view, which reserves nothing at all.
//  A rule that can only be checked by measuring is exactly the rule to measure, so these tests
//  render the real views and compare the CTA's position between the calm state and the failed one.
//
//  Rendering is a hosted window captured through `layer.render(in:)`. Both halves of that were
//  arrived at by trying the alternative and looking at the result:
//
//    · `ImageRenderer` draws the backdrop and nothing else. It does not resolve the GeometryReader
//      and ScrollView these screens are built from, so every probe came back empty — which a less
//      careful harness would have reported as "no problems found".
//    · `drawHierarchy(afterScreenUpdates:)` returns a blank image offscreen, because there are no
//      screen updates to be after.
//
//  Rendering the layer tree of a real hosted window has neither problem, and it draws the number
//  field too, which is a UIViewRepresentable and therefore invisible to the other two.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class ScreenFitTests: XCTestCase {

    /// Every phone the app has to run on, mirroring Devices.kt.
    ///
    /// The 320-wide Galaxy Fold cover screen is kept even though it is not an iPhone: it is the
    /// narrowest thing either platform has to survive, and a reserve that holds at 320 holds
    /// everywhere. The iOS floor proper is the 375x667 SE, named by the product side.
    struct Device {
        let name: String, width: CGFloat, height: CGFloat
    }

    let devices: [Device] = [
        Device(name: "Galaxy Fold cover screen", width: 320, height: 686),
        Device(name: "small Android (HD)", width: 360, height: 640),
        Device(name: "Galaxy A / common Android", width: 360, height: 740),
        Device(name: "iPhone 12 mini / 13 mini", width: 360, height: 780),
        Device(name: "common modern Android", width: 360, height: 800),
        Device(name: "iPhone SE (3rd gen)", width: 375, height: 667),
        Device(name: "iPhone X / XS / 11 Pro", width: 375, height: 812),
        Device(name: "iPhone 12 / 13 / 14", width: 390, height: 844),
        Device(name: "Pixel 4a / 5", width: 393, height: 851),
        Device(name: "iPhone 15 / 16", width: 393, height: 852),
        Device(name: "iPhone 16 Pro", width: 402, height: 874),
        Device(name: "Pixel 6 / 7", width: 411, height: 891),
        Device(name: "Pixel 7 Pro / 8 Pro", width: 412, height: 915),
        Device(name: "iPhone XR / 11", width: 414, height: 896),
        Device(name: "iPhone 12/13/14 Pro Max", width: 428, height: 926),
        Device(name: "iPhone 15/16 Pro Max", width: 430, height: 932),
        Device(name: "iPhone 16 Pro Max", width: 440, height: 956),
    ]

    // MARK: - the instrument
    //
    // A green run means nothing unless the instrument fires, so `testTheProbeFindsAMovedCTA` below
    // moves a CTA on purpose and fails if the probe cannot see it. Same idea as HarnessSelfTest.kt.

    private func render(_ view: some View, on device: Device) throws -> CGImage {
        let controller = UIHostingController(rootView: AnyView(view))
        // The design is a light-mode design; a dark-mode run would probe for a colour that is not
        // there and report every screen as broken.
        controller.overrideUserInterfaceStyle = .light
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: device.width, height: device.height))
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1     // one pixel per point, so a row index IS a point offset
        let image = UIGraphicsImageRenderer(size: window.bounds.size, format: format).image { ctx in
            window.layer.render(in: ctx.cgContext)
        }
        return try XCTUnwrap(image.cgImage, "\(device.name): nothing rendered")
    }

    /// The rendered pixels as straight RGBA bytes.
    ///
    /// Read through a context of a known format rather than off the CGImage directly: the renderer
    /// hands back premultiplied-first byte order, so indexing it as RGBA silently reads the alpha
    /// channel as red and finds nothing anywhere.
    private func rgba(_ image: CGImage) -> [UInt8] {
        var buffer = [UInt8](repeating: 0, count: image.width * image.height * 4)
        buffer.withUnsafeMutableBytes { raw in
            let context = CGContext(
                data: raw.baseAddress, width: image.width, height: image.height,
                bitsPerComponent: 8, bytesPerRow: image.width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
            context?.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        }
        return buffer
    }

    /// The rows the sunset CTA occupies, found by its violet end.
    ///
    /// The gradient runs orange to violet, and violet is the half worth probing: the danger states
    /// paint red and pink over the top third of these screens, and orange is close enough to those
    /// to be ambiguous. Nothing else on either screen is this colour.
    private func ctaRows(in image: CGImage) -> ClosedRange<Int>? {
        let bytes = rgba(image)
        let x = Int(Double(image.width) * 0.88)
        var rows: [Int] = []
        for y in 0..<image.height {
            let p = (y * image.width + x) * 4
            let (r, g, b) = (Int(bytes[p]), Int(bytes[p + 1]), Int(bytes[p + 2]))
            if r < 180, g < 110, b > 190 { rows.append(y) }
        }
        guard let first = rows.first, let last = rows.last else { return nil }
        return first...last
    }

    // MARK: - the rule SHOWUP-143 states outright

    func testTheCTADoesNotMoveWhenTheNumberIsRejected() throws {
        var moved: [String] = []
        for device in devices {
            let calm = try ctaRows(in: render(PhoneNumberView(value: .constant("201")), on: device))
            // The longest real message, and the one that wraps at every width this ships at.
            let failed = try ctaRows(in: render(
                PhoneNumberView(value: .constant("201"), error: .tooShort), on: device))
            guard let calm, let failed else {
                moved.append("\(device.name): CTA not found at all"); continue
            }
            if calm.lowerBound != failed.lowerBound {
                moved.append("\(device.name): CTA moved \(failed.lowerBound - calm.lowerBound)pt")
            }
        }
        XCTAssertTrue(moved.isEmpty, "the CTA moved on rejection:\n" + moved.joined(separator: "\n"))
    }

    func testTheCTADoesNotMoveWhenTheCodeIsWrong() throws {
        var moved: [String] = []
        for device in devices {
            let calm = try ctaRows(in: render(VerifyCodeView(digits: .constant("482170")), on: device))
            let failed = try ctaRows(in: render(
                VerifyCodeView(digits: .constant("482170"), mismatch: true), on: device))
            guard let calm, let failed else {
                moved.append("\(device.name): CTA not found at all"); continue
            }
            if calm.lowerBound != failed.lowerBound {
                moved.append("\(device.name): CTA moved \(failed.lowerBound - calm.lowerBound)pt")
            }
        }
        XCTAssertTrue(moved.isEmpty, "the CTA moved on mismatch:\n" + moved.joined(separator: "\n"))
    }

    // MARK: - and the rule that makes the first one worth anything

    func testTheCTAIsOnScreenOnEveryDevice() throws {
        // A CTA that never moves because it is off the bottom of the screen would pass the tests
        // above and fail every user. Both screens scroll when the keyboard is up, so this measures
        // the keyboard-free layout: if it does not fit here it fits nowhere.
        var offscreen: [String] = []
        for device in devices {
            for (label, image) in [
                ("Phone", try render(PhoneNumberView(value: .constant("201555")), on: device)),
                ("Code", try render(VerifyCodeView(digits: .constant("482170")), on: device)),
            ] {
                guard let rows = ctaRows(in: image) else {
                    offscreen.append("\(device.name) \(label): CTA not drawn"); continue
                }
                if rows.upperBound >= Int(device.height) - 1 {
                    offscreen.append("\(device.name) \(label): CTA reaches the bottom edge")
                }
            }
        }
        XCTAssertTrue(offscreen.isEmpty, "CTA off screen:\n" + offscreen.joined(separator: "\n"))
    }

    // MARK: - the instrument, checked

    func testTheProbeFindsAMovedCTAWhenThereIsOne() throws {
        // Deliberately moves the CTA by padding the top, and fails if the probe cannot see it. A
        // suite that cannot detect the fault it exists to catch is worse than no suite: it reports
        // success either way.
        let device = devices.first { $0.name == "iPhone SE (3rd gen)" }!
        let base = try ctaRows(in: render(PhoneNumberView(value: .constant("201")), on: device))
        let shifted = try ctaRows(in: render(
            PhoneNumberView(value: .constant("201")).padding(.top, 20), on: device))
        let a = try XCTUnwrap(base, "probe found no CTA in the baseline render")
        let b = try XCTUnwrap(shifted, "probe found no CTA in the shifted render")
        XCTAssertEqual(b.lowerBound - a.lowerBound, 20,
                       "the probe did not see a CTA that moved by exactly 20pt")
    }
}

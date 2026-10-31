//
//  EvidenceShots.swift
//  ShowUp · the iOS half of a ticket's "evidence of done", rendered on CI's Mac
//
//  The tickets ask for screenshots on iOS and Android at 375 x 667, 390 x 844 and 430 x 932. Android's
//  come from `EvidenceScreenshots.kt`; nobody building these screens has a Mac, so these come from
//  the macOS runner: each frame is hosted in a window with that iPhone's exact insets, drawn through
//  the window's layer tree at 3x, and written as a PNG.
//
//  ON SCREEN FIRST, THE LAYER TREE AS A FALLBACK. Offscreen there are no screen updates to be after,
//  so `drawHierarchy(afterScreenUpdates:)` draws nothing -- `ScreenFitTests` found that first, and
//  the first CI run of this file wrote twelve pure-white PNGs and passed. The layer tree draws, but
//  not as the phone composites: it ignores blend modes (the Premium tag's inset shadows, cut with
//  `destinationOut`, came out as a black capsule) and draws a capsule's border square-cornered. So
//  the window joins the host app's scene and is drawn on screen; only if that draws nothing is the
//  layer tree used, and that picture's name ends `-layers`. A flat picture fails either way.
//
//  ONLY WHEN ASKED: the folder comes from `SHOWUP_EVIDENCE_DIR` (CI passes it as
//  `TEST_RUNNER_SHOWUP_EVIDENCE_DIR`). Without it the test is skipped, so an ordinary run writes
//  nothing anywhere.
//

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class EvidenceShots: XCTestCase {

    /// The three frames the tickets name.
    private let frames: [FitDevice] = fitDevices.filter {
        ($0.width, $0.height) == (375, 667) || ($0.width, $0.height) == (390, 844) || ($0.width, $0.height) == (430, 932)
    }

    private func folder(_ ticket: String) throws -> URL {
        guard let root = ProcessInfo.processInfo.environment["SHOWUP_EVIDENCE_DIR"], !root.isEmpty else {
            throw XCTSkip("SHOWUP_EVIDENCE_DIR is not set: evidence is rendered on CI only")
        }
        let dir = URL(fileURLWithPath: root).appendingPathComponent(ticket, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// `view` on `device`, saved as `<state>-<w>x<h>.png` in `dir`; a picture of one flat colour fails.
    private func shoot(_ view: some View, _ state: String, on device: FitDevice, into dir: URL) throws {
        let controller = UIHostingController(rootView: AnyView(view))
        controller.overrideUserInterfaceStyle = .light
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        let window = scene.map { FixedInsetWindow(windowScene: $0) }
            ?? FixedInsetWindow(frame: CGRect(x: 0, y: 0, width: device.width, height: device.height))
        window.frame = CGRect(x: 0, y: 0, width: device.width, height: device.height)
        window.windowLevel = .alert + 1
        window.fixedInsets = UIEdgeInsets(top: device.top, left: 0, bottom: device.bottom, right: 0)
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.3))
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 3
        let renderer = UIGraphicsImageRenderer(size: window.bounds.size, format: format)
        var drawn = false
        var image = renderer.image { _ in
            drawn = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        var suffix = ""
        if !drawn || image.cgImage.map { isOneColour($0) } ?? true {
            image = renderer.image { ctx in window.layer.render(in: ctx.cgContext) }
            suffix = "-layers"
        }
        window.isHidden = true
        let picture = try XCTUnwrap(image.cgImage, "\(state) on \(device.described): nothing rendered")
        XCTAssertFalse(isOneColour(picture), "\(state) on \(device.described): one flat colour, nothing drawn")
        let data = try XCTUnwrap(image.pngData(), "\(state) on \(device.described): nothing rendered")
        try data.write(to: dir.appendingPathComponent(
            "\(state)-\(Int(device.width))x\(Int(device.height))\(suffix).png"))
    }

    /// Whether `image`, sampled on a 24 x 24 grid, is a single colour -- what a failed draw leaves.
    private func isOneColour(_ image: CGImage) -> Bool {
        let side = 24
        var pixels = [UInt8](repeating: 0, count: side * side * 4)
        let drawn = pixels.withUnsafeMutableBytes { raw -> Bool in
            guard let ctx = CGContext(data: raw.baseAddress, width: side, height: side, bitsPerComponent: 8,
                                      bytesPerRow: side * 4, space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            ctx.interpolationQuality = .none
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: side, height: side))
            return true
        }
        guard drawn else { return true }
        let first = Array(pixels[0..<4])
        return stride(from: 0, to: pixels.count, by: 4).allSatisfy { Array(pixels[$0..<($0 + 4)]) == first }
    }

    /// SHOWUP-188: A, B and C on the Search tab, D over a Likes profile in B as the sheet draws it.
    func testLocationGateEvidence() throws {
        let dir = try folder("188-location-gate")
        let shots: [(String, LocationState, Bool)] = [
            ("A-never-granted", .ask, false),
            ("B-off-for-show-up", .denied, false),
            ("C-off-on-the-phone", .servicesOff, false),
            ("D-from-likes", .denied, true),
        ]
        XCTAssertEqual(frames.count, 3, "the three ticket frames")
        for device in frames {
            for (name, state, fromLikes) in shots {
                try shoot(LocationGateView(state: state, tab: fromLikes ? .likes : .search, likes: 2,
                                           onTab: { _ in }, onLikes: {}, onBack: fromLikes ? {} : nil),
                          name, on: device, into: dir)
            }
        }
    }

    /// SHOWUP-189: Filters v2 -- A both off, B two genders with age a dealbreaker, C both on; and A with
    /// the remote flag off at 390 x 844, Check-In 05's layout plus the gender change.
    func testFiltersV2Evidence() throws {
        let dir = try folder("189-filters-v2")
        let shots: [(String, CheckInFilters)] = [
            ("A-both-off", CheckInFilters(genders: ["woman"])),
            ("B-two-genders-age-strict", CheckInFilters(genders: ["woman", "non_binary"], minAge: 28, maxAge: 35,
                                                        ageDealbreaker: true)),
            ("C-both-strict", CheckInFilters(genders: ["woman", "man", "non_binary"], minAge: 25, maxAge: 40,
                                             distanceKm: 15, ageDealbreaker: true, distanceDealbreaker: true)),
        ]
        for device in frames {
            for (name, filters) in shots {
                try shoot(CheckInFiltersView(filters: filters, dealbreakers: true), name, on: device, into: dir)
            }
        }
        let mid = try XCTUnwrap(frames.first { $0.width == 390 })
        try shoot(CheckInFiltersView(filters: CheckInFilters(genders: ["woman"])), "A-flag-off", on: mid, into: dir)
    }
}

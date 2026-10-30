//
//  EvidenceShots.swift
//  ShowUp · the iOS half of a ticket's "evidence of done", rendered on CI's Mac
//
//  The tickets ask for screenshots on iOS and Android at 375 x 667, 390 x 844 and 430 x 932. Android's
//  come from `EvidenceScreenshots.kt`; nobody building these screens has a Mac, so these come from
//  the macOS runner: each frame is hosted in a window with that iPhone's exact insets, drawn through
//  `drawHierarchy` at 3x -- what the screen shows, not a layer dump -- and written as a PNG.
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

    /// `view` on `device`, as the phone draws it, saved as `<state>-<w>x<h>.png` in `dir`.
    private func shoot(_ view: some View, _ state: String, on device: FitDevice, into dir: URL) throws {
        let controller = UIHostingController(rootView: AnyView(view))
        controller.overrideUserInterfaceStyle = .light
        let window = FixedInsetWindow(frame: CGRect(x: 0, y: 0, width: device.width, height: device.height))
        window.fixedInsets = UIEdgeInsets(top: device.top, left: 0, bottom: device.bottom, right: 0)
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.1))
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 3
        let image = UIGraphicsImageRenderer(size: window.bounds.size, format: format).image { _ in
            _ = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        window.isHidden = true
        let data = try XCTUnwrap(image.pngData(), "\(state) on \(device.described): nothing rendered")
        try data.write(to: dir.appendingPathComponent("\(state)-\(Int(device.width))x\(Int(device.height)).png"))
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
}

// ModalShellFitTests.swift
// ShowUp · `ModalShell` takes its height from its content (SHOWUP-195), pinned once for every sheet drawn on it:
// Search's like sheets, Date 02's End date sheet and the arrival check-in sheets.
//
// The kit's sheet is `max-height: 88%; overflow-y: auto`, docked to the bottom: as tall as what it holds, cut at 88 %
// of the frame it is docked in and scrolling past that. The 14 Nov 2026 CI evidence had every sheet at the cap with
// its content floating in the middle: the cream was drawn on the cap's frame, which takes the height it is offered.
//
// Measured against the dock (`FitAnchorID.modalShellDock`, the frame `SheetScaffold` places the sheet in), not the
// phone's insets: the dock's foot is the sheet's foot, and a rise still settling moves both alike.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

/// The test sheets' one block of content, published under this id.
private let shellContent = "modal-shell-test:content"

/// What the shell adds to its content's height: the grabber (8 above it, 4 tall) and the default padding (24, 24).
private let shellChrome: CGFloat = 8 + 4 + 24 + 24

@MainActor
final class ModalShellFitTests: XCTestCase {

    private final class Sink {
        var frames: [String: CGRect] = [:]
        func record(_ value: [String: CGRect]) -> Bool {
            frames = value
            return true
        }
    }

    /// A sheet holding one block `height` tall, under the shell's default padding.
    private func sheet(_ height: CGFloat) -> some View {
        ModalShell(label: "Sheet", onDismiss: { _ in }) {
            Color.liqFaint.frame(height: height).fitAnchor(shellContent)
        }
    }

    /// The frames `view` publishes on `device` at the default type size, once the sheet has risen (28 pt, 0.36 s).
    private func frames(_ view: some View, on device: FitDevice) -> [String: CGRect] {
        autoreleasepool {
            let sink = Sink()
            let root = view.overlayPreferenceValue(FitFrameKey.self) { frames in
                let _ = sink.record(frames)
                Color.clear
            }
            let controller = UIHostingController(rootView: root)
            controller.overrideUserInterfaceStyle = .light
            let window = FixedInsetWindow(frame: CGRect(x: 0, y: 0, width: device.width, height: device.height))
            window.fixedInsets = UIEdgeInsets(top: device.top, left: 0, bottom: device.bottom, right: 0)
            window.rootViewController = controller
            window.isHidden = false
            window.layoutIfNeeded()
            controller.view.setNeedsLayout()
            controller.view.layoutIfNeeded()
            RunLoop.main.run(until: Date().addingTimeInterval(0.6))
            controller.view.layoutIfNeeded()
            window.isHidden = true
            window.rootViewController = nil
            return sink.frames
        }
    }

    private func phone() throws -> FitDevice {
        try XCTUnwrap(fitDevices.first { $0.width == 390 && $0.height == 844 }, "no 390 x 844 phone")
    }

    /// Two sheets of different content heights: each surface is its content plus the grabber and the padding, so the
    /// two differ by exactly what their contents do, and each rests on the dock's foot.
    func testTwoSheetsAreAsTallAsTheirContentAndRestOnTheFoot() throws {
        let device = try phone()
        var found: [String] = []
        var heights: [CGFloat] = []
        for height: CGFloat in [100, 300] {
            let f = frames(sheet(height), on: device)
            guard let surface = f[FitAnchorID.modalShell], let dock = f[FitAnchorID.modalShellDock],
                  let content = f[shellContent] else {
                found.append("content \(height): the sheet is NOT DRAWN"); continue
            }
            heights.append(surface.height)
            if abs(surface.height - (height + shellChrome)) > 1 {
                found.append("content \(height): the sheet is \(surface.height) tall, not \(height + shellChrome)")
            }
            if abs(surface.maxY - dock.maxY) > 1 { found.append("content \(height): the sheet ends \(dock.maxY - surface.maxY) above the foot") }
            if abs(content.minY - surface.minY - (8 + 4 + 24)) > 1 {
                found.append("content \(height): it starts \(content.minY - surface.minY) under the sheet's top, not 36")
            }
            if abs(dock.maxY - content.maxY - 24) > 1 { found.append("content \(height): it ends \(dock.maxY - content.maxY) above the foot, not 24") }
        }
        if heights.count == 2, abs(heights[1] - heights[0] - 200) > 1 {
            found.append("the sheets differ by \(heights[1] - heights[0]), their contents by 200")
        }
        XCTAssertTrue(found.isEmpty, found.joined(separator: " · "))
    }

    /// Content taller than the cap: the sheet is cut at 88 % of the dock and rests on its foot, and the content keeps
    /// its whole height, starting under the grabber at the top of a scroll -- not squeezed into the cap, and not
    /// hanging off the top of a sheet that grew past it.
    func testASheetTallerThanTheCapIsCutThereAndScrolls() throws {
        let device = try phone()
        let f = frames(sheet(2000), on: device)
        let surface = try XCTUnwrap(f[FitAnchorID.modalShell], "the sheet is NOT DRAWN")
        let dock = try XCTUnwrap(f[FitAnchorID.modalShellDock], "the dock is NOT DRAWN")
        let content = try XCTUnwrap(f[shellContent], "the content is NOT DRAWN")
        XCTAssertEqual(surface.height, dock.height * 0.88, accuracy: 1, "the sheet is cut at 88 % of \(dock.height)")
        XCTAssertEqual(surface.maxY, dock.maxY, accuracy: 1, "the sheet rests on the foot")
        XCTAssertEqual(content.height, 2000, accuracy: 1, "the content keeps its height")
        XCTAssertEqual(content.minY, surface.minY + 8 + 4 + 24, accuracy: 1, "the content starts at the top of the scroll")
        XCTAssertGreaterThan(content.maxY, surface.maxY, "the rest of the content is below, to scroll to")
    }
}

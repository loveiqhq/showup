// PartnerEndedFitTests.swift
// ShowUp · Date 08's three payloads on every phone at every Dynamic Type size (SHOWUP-199). Android's
// `PartnerEndedFitTest.kt`, payload for payload.
//
// Each payload on every phone of `fitDevices` and `edgeFitDevices` at all twelve Dynamic Type sizes, xSmall to AX5,
// under the shared checks (every touch area 44 both ways, and here exactly one: the CTA) and this ticket's own (R5,
// ruling R-b):
//
//  - the CTA is drawn, inside the screen's width;
//  - the CTA lies below the last row with the flexible space's 14 between them, never over it;
//  - the CTA is the column's last item, 6 above its end, so scrolling to the end reaches it;
//  - the column is at least the viewport tall and starts at its top, so on a phone where it fits the CTA sits at the foot.
//
// Whether the CTA NEEDS a scroll on 375 x 667 is measured and attached to the run (`XCTAttachment`), not asserted: it is
// a fact about the copy's length.
//
// THE RUNNER CUTS A TEST OFF AT TEN MINUTES (the 8 Nov 2026 CI runs), so the sweep is 18 tests: the three payloads times
// six pairs of two Dynamic Type sizes, every layout inside an `autoreleasepool`.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class PartnerEndedFitTests: XCTestCase {

    private final class Sink {
        var frames: [String: CGRect] = [:]
        var taps: [FitTap] = []
        func record(_ value: [String: CGRect]) -> Bool {
            frames = value
            return true
        }
        func record(taps value: [FitTap]) -> Bool {
            taps = value
            return true
        }
    }

    private struct Shot {
        let frames: [String: CGRect]
        let taps: [FitTap]
    }

    private func layout(_ view: some View, on device: FitDevice, type: DynamicTypeSize) -> Shot {
        autoreleasepool { shot(view, on: device, type: type) }
    }

    private func shot(_ view: some View, on device: FitDevice, type: DynamicTypeSize) -> Shot {
        let sink = Sink()
        let root = view
            .environment(\.dynamicTypeSize, type)
            .overlayPreferenceValue(FitFrameKey.self) { frames in
                let _ = sink.record(frames)
                Color.clear
            }
            .overlayPreferenceValue(FitTapKey.self) { taps in
                let _ = sink.record(taps: taps)
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
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        controller.view.layoutIfNeeded()
        window.isHidden = true
        window.rootViewController = nil
        return Shot(frames: sink.frames, taps: sink.taps)
    }

    private var phones: [FitDevice] { fitDevices + edgeFitDevices }

    /// Two Dynamic Type sizes a test, every size once.
    private static let pairs: [[DynamicTypeSize]] = [
        [.xSmall, .small], [.medium, .large], [.xLarge, .xxLarge],
        [.xxxLarge, .accessibility1], [.accessibility2, .accessibility3], [.accessibility4, .accessibility5],
    ]

    private func screen(_ payload: PartnerScreen) -> some View {
        PartnerEndedScreen(screen: payload, name: "Leonie", photoURL: nil)
    }

    // MARK: the rules

    /// Every touch area at least 44 both ways, and only one of them: Proceed to review is the screen's one control.
    private func tapFailures(_ shot: Shot, _ tag: String) -> [String] {
        var failures: [String] = []
        let areas = shot.taps.filter { $0.frame.width > 0 && $0.frame.height > 0 }
        if areas.count != 1 { failures.append("\(tag): \(areas.count) touch areas, the CTA must be the only one") }
        for tap in areas where tap.frame.width < 43.5 || tap.frame.height < 43.5 {
            failures.append("\(tag): a touch area \(tap.frame.width) x \(tap.frame.height) at \(tap.frame.origin), under 44")
        }
        return failures
    }

    private func failures(_ shot: Shot, _ payload: PartnerScreen, on device: FitDevice, type: DynamicTypeSize) -> [String] {
        let f = shot.frames
        let tag = "\(payload.rawValue) \(device.described) @\(type)"
        var found = tapFailures(shot, tag)
        guard let cta = f[FitAnchorID.partnerEndedCta], let rows = f[FitAnchorID.partnerEndedRows],
              let column = f[FitAnchorID.partnerEndedColumn], let viewport = f[FitAnchorID.partnerEndedViewport] else {
            return found + ["\(tag): the CTA, the rows, the column or the viewport is NOT DRAWN"]
        }
        if cta.minX < -0.5 || cta.maxX > device.width + 0.5 {
            found.append("\(tag): the CTA is off the side (\(cta.minX)...\(cta.maxX))")
        }
        if cta.minY < rows.maxY + 14 - 0.75 {
            found.append("\(tag): the CTA is OVER THE ROWS (rows end \(rows.maxY), CTA starts \(cta.minY))")
        }
        if abs(column.maxY - 6 - cta.maxY) > 0.75 {
            found.append("\(tag): the CTA is NOT THE LAST ITEM (CTA ends \(cta.maxY), column ends \(column.maxY))")
        }
        if column.height < viewport.height - 0.75 {
            found.append("\(tag): the column is SHORTER THAN THE VIEWPORT (\(column.height) of \(viewport.height))")
        }
        if abs(column.minY - viewport.minY) > 0.75 {
            found.append("\(tag): the column does NOT START AT THE TOP (column \(column.minY), viewport \(viewport.minY))")
        }
        if column.height <= viewport.height + 0.75, abs(viewport.maxY - 6 - cta.maxY) > 0.75 {
            found.append("\(tag): the CTA is NOT AT THE FOOT where the column fits (CTA ends \(cta.maxY), viewport ends \(viewport.maxY))")
        }
        return found
    }

    /// One entry per kind of problem, with how often it came and its first case.
    private func summary(_ failures: [String]) -> String {
        var order: [String] = []
        var seen: [String: (count: Int, first: String)] = [:]
        for failure in failures {
            let detail = failure.split(separator: ":", maxSplits: 1).last.map(String.init) ?? failure
            let kind = detail.replacingOccurrences(of: "[0-9.]+", with: "#", options: .regularExpression)
            if let entry = seen[kind] {
                seen[kind] = (entry.count + 1, entry.first)
            } else {
                seen[kind] = (1, failure)
                order.append(kind)
            }
        }
        return order.prefix(30).compactMap { kind in seen[kind].map { "\($0.count) x \($0.first)" } }
            .joined(separator: " · ")
    }

    private func sweep(_ payload: PartnerScreen, _ types: [DynamicTypeSize]) {
        var found: [String] = []
        for type in types {
            for device in phones {
                found += failures(layout(screen(payload), on: device, type: type), payload, on: device, type: type)
            }
        }
        XCTAssertTrue(found.isEmpty, "Date 08 \(payload.rawValue): \(found.count) problems: " + summary(found))
    }

    // MARK: the sweeps -- 18 tests, the three payloads times six pairs of sizes

    func testAIsCleanOnEveryPhoneAtXSmallAndSmall() { sweep(.youNoShowed, Self.pairs[0]) }
    func testAIsCleanOnEveryPhoneAtMediumAndTheDefault() { sweep(.youNoShowed, Self.pairs[1]) }
    func testAIsCleanOnEveryPhoneAtXLargeAndXXLarge() { sweep(.youNoShowed, Self.pairs[2]) }
    func testAIsCleanOnEveryPhoneAtXXXLargeAndAX1() { sweep(.youNoShowed, Self.pairs[3]) }
    func testAIsCleanOnEveryPhoneAtAX2AndAX3() { sweep(.youNoShowed, Self.pairs[4]) }
    func testAIsCleanOnEveryPhoneAtAX4AndAX5() { sweep(.youNoShowed, Self.pairs[5]) }

    func testBIsCleanOnEveryPhoneAtXSmallAndSmall() { sweep(.dateEndedDiscomfort, Self.pairs[0]) }
    func testBIsCleanOnEveryPhoneAtMediumAndTheDefault() { sweep(.dateEndedDiscomfort, Self.pairs[1]) }
    func testBIsCleanOnEveryPhoneAtXLargeAndXXLarge() { sweep(.dateEndedDiscomfort, Self.pairs[2]) }
    func testBIsCleanOnEveryPhoneAtXXXLargeAndAX1() { sweep(.dateEndedDiscomfort, Self.pairs[3]) }
    func testBIsCleanOnEveryPhoneAtAX2AndAX3() { sweep(.dateEndedDiscomfort, Self.pairs[4]) }
    func testBIsCleanOnEveryPhoneAtAX4AndAX5() { sweep(.dateEndedDiscomfort, Self.pairs[5]) }

    func testCIsCleanOnEveryPhoneAtXSmallAndSmall() { sweep(.dateClosedPositive, Self.pairs[0]) }
    func testCIsCleanOnEveryPhoneAtMediumAndTheDefault() { sweep(.dateClosedPositive, Self.pairs[1]) }
    func testCIsCleanOnEveryPhoneAtXLargeAndXXLarge() { sweep(.dateClosedPositive, Self.pairs[2]) }
    func testCIsCleanOnEveryPhoneAtXXXLargeAndAX1() { sweep(.dateClosedPositive, Self.pairs[3]) }
    func testCIsCleanOnEveryPhoneAtAX2AndAX3() { sweep(.dateClosedPositive, Self.pairs[4]) }
    func testCIsCleanOnEveryPhoneAtAX4AndAX5() { sweep(.dateClosedPositive, Self.pairs[5]) }

    /// Measured, not asserted (ruling R-b): the column's height against the 375 x 667 safe area at the default size.
    func testReportWhetherTheCtaNeedsAScrollOn375x667() throws {
        let phone = try XCTUnwrap(fitDevices.first { $0.width == 375 && $0.height == 667 })
        let lines = [PartnerScreen.youNoShowed, .dateEndedDiscomfort, .dateClosedPositive].map { payload -> String in
            let f = layout(screen(payload), on: phone, type: .large).frames
            guard let cta = f[FitAnchorID.partnerEndedCta], let viewport = f[FitAnchorID.partnerEndedViewport] else {
                return "\(payload.rawValue): not drawn"
            }
            return "\(payload.rawValue): CTA bottom \(cta.maxY) of \(viewport.maxY) usable, scroll needed: \(cta.maxY > viewport.maxY + 0.75)"
        }
        let attachment = XCTAttachment(string: lines.joined(separator: "\n"))
        attachment.name = "partner-ended-scroll"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testTheSixPairsCoverEveryTypeSize() {
        XCTAssertEqual(Set(Self.pairs.flatMap { $0 }), Set(DynamicTypeSize.allCases))
        XCTAssertEqual(Self.pairs.flatMap { $0 }.count, DynamicTypeSize.allCases.count)
    }
}

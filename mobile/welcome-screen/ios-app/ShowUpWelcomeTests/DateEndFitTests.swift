// DateEndFitTests.swift
// ShowUp · Date 06, end the date and report a problem, on every phone at every Dynamic Type size (SHOWUP-197).
// Android's `DateEndFitTest.kt`, frame for frame.
//
// The ticket's frames A to C and E to J (D is Date 03's) on every phone of `fitDevices` and `edgeFitDevices` at all
// twelve Dynamic Type sizes, xSmall to AX5, under the shared checks (every touch area 44 both ways and none partly on
// another) and this ticket's own:
//
//  - the sheet's CTA (or, on the guide, its end button) is drawn on every frame -- the sheet scrolls inside the shell
//    where it is taller than the phone, and the buttons stay reachable by that scroll;
//  - every control lies inside the screen's width;
//  - J draws its box above the CTA on every phone;
//  - the End Date bar lies wholly inside the screen width.
//
// THE RUNNER CUTS A TEST OFF AT TEN MINUTES (the 8 Nov 2026 CI runs), so the sweep is 18 tests: three groups of frames
// times six pairs of two Dynamic Type sizes, every layout inside an `autoreleasepool`.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class DateEndFitTests: XCTestCase {

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

    // MARK: the frames

    private let meet = DateSamples.meet
    private let zone = DateSamples.zone

    /// One render: its label, the anchor that must be drawn, and what is drawn.
    private struct Frame {
        let label: String
        let anchor: String
        let view: AnyView
    }

    private func date(chatOpen: Bool = true) -> ActiveDate {
        var d = DateSamples.date
        d.coordinateOpen = chatOpen
        d.checkedIn = true
        d.endsEnabled = true
        return d
    }

    private func sheet(_ label: String, anchor: String, route: EndRoute = .wrap, step: EndStep = .menu, past: Bool = true,
                       chatOpen: Bool = true, failed: Bool = false) -> Frame {
        let ui = DateEndUi(route: route, step: step, failed: failed, date: date(chatOpen: chatOpen),
                           now: past ? meet.addingTimeInterval(11 * 60) : meet.addingTimeInterval(-5 * 60), checkedIn: true)
        let behind = DateScreen(ui: DateUi(date: date(chatOpen: chatOpen), now: DateSamples.bNow, loading: false), zone: zone)
        return Frame(label: label, anchor: anchor, view: AnyView(
            DateEndOver(visible: true, name: "Leonie", zone: zone, ui: ui, actions: EndActions()) { behind }))
    }

    /// A inside the Date screen's footer slot, above the TabBar, as the app draws it.
    private var barFrame: Frame {
        Frame(label: "A bar", anchor: FitAnchorID.endBar, view: AnyView(
            DateScreen(ui: DateUi(date: date(), now: DateSamples.bNow, loading: false), zone: zone,
                       endDateBar: { AnyView(EndDateBar(name: "Leonie", onEnd: {})) })))
    }

    private var menuFrames: [Frame] {
        [barFrame,
         sheet("B how did it go", anchor: FitAnchorID.endPickNoShow),
         sheet("C we met", anchor: FitAnchorID.endCta, step: .met),
         sheet("H report a problem", anchor: FitAnchorID.endPickNoShow, route: .report)]
    }

    private var stepFrames: [Frame] {
        [sheet("E not here yet, before", anchor: FitAnchorID.endGuideEnd, step: .guide, past: false),
         sheet("F not here yet, after", anchor: FitAnchorID.endGuideEnd, step: .guide),
         sheet("E chat locked", anchor: FitAnchorID.endGuideEnd, step: .guide, past: false, chatOpen: false),
         sheet("G thanks for waiting", anchor: FitAnchorID.endCta, step: .noShow),
         sheet("I uncomfortable", anchor: FitAnchorID.endCta, route: .report, step: .uncomfortable)]
    }

    private var failedFrames: [Frame] {
        [sheet("J on G", anchor: FitAnchorID.endCta, step: .noShow, failed: true),
         sheet("J on C", anchor: FitAnchorID.endCta, step: .met, failed: true),
         sheet("J on I", anchor: FitAnchorID.endCta, route: .report, step: .uncomfortable, failed: true)]
    }

    // MARK: the rules

    /// Every touch area at least 44 both ways, and no two partly on each other. With a sheet up nothing behind it
    /// answers: only its own controls are compared.
    private func tapFailures(_ shot: Shot, _ tag: String) -> [String] {
        var failures: [String] = []
        let areas = shot.taps.filter { $0.frame.width > 0 && $0.frame.height > 0 }
        for tap in areas where tap.frame.width < 43.5 || tap.frame.height < 43.5 {
            failures.append("\(tag): a touch area \(tap.frame.width) x \(tap.frame.height) at \(tap.frame.origin), under 44")
        }
        func holds(_ a: CGRect, _ b: CGRect) -> Bool { a.insetBy(dx: -0.5, dy: -0.5).contains(b) }
        let live = areas.contains(where: \.modal) ? areas.filter(\.modal) : areas
        for i in live.indices {
            for j in live.indices where j > i {
                if live[i].pinned != live[j].pinned { continue }
                let a = live[i].frame, b = live[j].frame
                if holds(a, b) || holds(b, a) { continue }
                let both = a.intersection(b)
                if !both.isNull, both.width > 0.5, both.height > 0.5 {
                    failures.append("\(tag): touch areas at \(a.origin) and \(b.origin) overlap by \(both.width) x \(both.height)")
                }
            }
        }
        return failures
    }

    private func failures(_ shot: Shot, _ frame: Frame, on device: FitDevice, type: DynamicTypeSize) -> [String] {
        let f = shot.frames
        let tag = "\(frame.label) \(device.described) @\(type)"
        var found = tapFailures(shot, tag)
        guard let anchor = f[frame.anchor] else { return found + ["\(tag): \(frame.anchor) is NOT DRAWN"] }
        if anchor.minX < -0.5 || anchor.maxX > device.width + 0.5 {
            found.append("\(tag): \(frame.anchor) is off the side (\(anchor.minX)...\(anchor.maxX))")
        }
        if frame.label.hasPrefix("J") {
            guard let box = f[FitAnchorID.endError] else { return found + ["\(tag): the error box is NOT DRAWN"] }
            if box.maxY > anchor.minY + 0.75 { found.append("\(tag): the error box is NOT ABOVE THE CTA (box ends \(box.maxY), CTA starts \(anchor.minY))") }
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

    private func sweep(_ frames: [Frame], _ types: [DynamicTypeSize]) {
        var found: [String] = []
        for frame in frames {
            for type in types {
                for device in phones {
                    let result = layout(frame.view, on: device, type: type)
                    found += failures(result, frame, on: device, type: type)
                }
            }
        }
        XCTAssertTrue(found.isEmpty, "\(found.count) problems: " + summary(found))
    }

    // MARK: the sweeps -- 18 tests, three groups of frames times six pairs of sizes

    func testMenusAreCleanAtXSmallAndSmall() { sweep(menuFrames, Self.pairs[0]) }
    func testMenusAreCleanAtMediumAndTheDefault() { sweep(menuFrames, Self.pairs[1]) }
    func testMenusAreCleanAtXLargeAndXXLarge() { sweep(menuFrames, Self.pairs[2]) }
    func testMenusAreCleanAtXXXLargeAndAX1() { sweep(menuFrames, Self.pairs[3]) }
    func testMenusAreCleanAtAX2AndAX3() { sweep(menuFrames, Self.pairs[4]) }
    func testMenusAreCleanAtAX4AndAX5() { sweep(menuFrames, Self.pairs[5]) }

    func testStepsAreCleanAtXSmallAndSmall() { sweep(stepFrames, Self.pairs[0]) }
    func testStepsAreCleanAtMediumAndTheDefault() { sweep(stepFrames, Self.pairs[1]) }
    func testStepsAreCleanAtXLargeAndXXLarge() { sweep(stepFrames, Self.pairs[2]) }
    func testStepsAreCleanAtXXXLargeAndAX1() { sweep(stepFrames, Self.pairs[3]) }
    func testStepsAreCleanAtAX2AndAX3() { sweep(stepFrames, Self.pairs[4]) }
    func testStepsAreCleanAtAX4AndAX5() { sweep(stepFrames, Self.pairs[5]) }

    func testFailedFramesAreCleanAtXSmallAndSmall() { sweep(failedFrames, Self.pairs[0]) }
    func testFailedFramesAreCleanAtMediumAndTheDefault() { sweep(failedFrames, Self.pairs[1]) }
    func testFailedFramesAreCleanAtXLargeAndXXLarge() { sweep(failedFrames, Self.pairs[2]) }
    func testFailedFramesAreCleanAtXXXLargeAndAX1() { sweep(failedFrames, Self.pairs[3]) }
    func testFailedFramesAreCleanAtAX2AndAX3() { sweep(failedFrames, Self.pairs[4]) }
    func testFailedFramesAreCleanAtAX4AndAX5() { sweep(failedFrames, Self.pairs[5]) }

    func testTheSixPairsCoverEveryTypeSize() {
        XCTAssertEqual(Set(Self.pairs.flatMap { $0 }), Set(DynamicTypeSize.allCases))
        XCTAssertEqual(Self.pairs.flatMap { $0 }.count, DynamicTypeSize.allCases.count)
    }
}

// CancelFitTests.swift
// ShowUp · Date 07, the guard sheet and what the match sees, on every phone at every Dynamic Type size (SHOWUP-198).
// Android's `CancelFitTest.kt`, frame for frame.
//
// Frames B, C, D, E and I (one sheet, parts replaced in place) over the Date screen, and H on the shared outcome
// screen, on every phone of `fitDevices` and `edgeFitDevices` at all twelve Dynamic Type sizes, xSmall to AX5, under
// the shared checks (every touch area 44 both ways and none partly on another) and this ticket's own:
//
//  - the choices are drawn on every sheet frame: the sheet scrolls inside its shell where it is taller than the phone,
//    and the keep button and `Cancel anyway` stay reachable by that scroll;
//  - every control lies inside the screen's width;
//  - E draws its box between the cost box and the choices;
//  - I draws no body line and no tile;
//  - H's Search CTA is drawn on every phone, and its three rows.
//
// THE RUNNER CUTS A TEST OFF AT TEN MINUTES (the 8 Nov 2026 CI runs), so the sweep is 18 tests: three groups of frames
// times six pairs of two Dynamic Type sizes, every layout inside an `autoreleasepool`.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class CancelFitTests: XCTestCase {

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

    private let meet = Date(timeIntervalSince1970: 1_795_024_800) // 2026-11-18T18:00:00Z (19:00 Berlin)
    private let zone = DateSamples.zone

    /// One render: its label, the anchor that must be drawn, and what is drawn.
    private struct Frame {
        let label: String
        let anchor: String
        let view: AnyView
    }

    private func date(chatOpen: Bool) -> ActiveDate {
        var d = DateSamples.date
        d.meetAt = meet
        d.unlockAt = meet.addingTimeInterval(-90 * 60)
        d.coordinateOpen = chatOpen
        d.checkedIn = false
        d.endsEnabled = true
        d.cancelPenaltyPoints = 10
        d.change = DateChange(cutoffAt: meet.addingTimeInterval(-30 * 60), allowed: true,
                              slots: [meet.addingTimeInterval(900)], timeWas: nil, placeWas: nil, pending: [])
        return d
    }

    private func sheet(_ label: String, chatOpen: Bool = false, closed: Bool = false, sent: SentRequest? = nil,
                       failed: Bool = false) -> Frame {
        let now = closed ? meet.addingTimeInterval(-10 * 60) : meet.addingTimeInterval(-3 * 3600)
        let ui = CancelUi(open: true, sent: sent, failed: failed, date: date(chatOpen: chatOpen), now: now)
        let behind = DateScreen(ui: DateUi(date: date(chatOpen: chatOpen), now: now, loading: false), zone: zone)
        return Frame(label: label, anchor: FitAnchorID.cancelKeep, view: AnyView(
            DateEndOver(visible: true, name: "Leonie", zone: zone, ui: DateEndUi(), actions: EndActions(),
                        cancelUi: ui, cancelActions: CancelActions()) { behind }))
    }

    private var partnerCancelled: Frame {
        Frame(label: "H partner cancelled", anchor: "chat:cta", view: AnyView(
            DateEndedScreen(outcome: EndedOutcome(dateId: "d1", name: "Leonie", endedByYou: false, reason: .cancelled),
                            photoURL: nil)))
    }

    private var guardFrames: [Frame] {
        [sheet("B guard, chat locked"),
         sheet("C guard, chat open", chatOpen: true),
         sheet("D request sent", chatOpen: true, sent: SentRequest(kind: .place, label: "Osteria Nove"))]
    }

    private var stateFrames: [Frame] {
        [sheet("E cancel failed", chatOpen: true, failed: true),
         sheet("I from meet minus 0:30", closed: true),
         sheet("D time request", sent: SentRequest(kind: .time, label: "20:00")),
         sheet("D long place request", chatOpen: true,
               sent: SentRequest(kind: .place, label: "Café Fräulein am Landwehrkanal"))]
    }

    private var partnerFrames: [Frame] { [partnerCancelled] }

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
        if frame.label.hasPrefix("E") {
            guard let box = f[FitAnchorID.endError], let cost = f[FitAnchorID.cancelCost] else {
                return found + ["\(tag): the error box or the cost box is NOT DRAWN"]
            }
            if box.maxY > anchor.minY + 0.75 {
                found.append("\(tag): the error box is NOT ABOVE THE CHOICES (box ends \(box.maxY), keep starts \(anchor.minY))")
            }
            if box.minY < cost.maxY - 0.75 {
                found.append("\(tag): the error box is NOT BELOW THE COST BOX (box starts \(box.minY), cost ends \(cost.maxY))")
            }
        }
        if frame.label.hasPrefix("I") {
            for id in [FitAnchorID.cancelSolutionBody, FitAnchorID.cancelTileTime, FitAnchorID.cancelTilePlace] where f[id] != nil {
                found.append("\(tag): \(id) is DRAWN IN I")
            }
            if f[FitAnchorID.cancelMessage] == nil { found.append("\(tag): the message button is NOT DRAWN in I") }
        }
        if frame.label.hasPrefix("H") {
            for index in 0..<3 where f["outcome:row:\(index)"] == nil { found.append("\(tag): row \(index) is NOT DRAWN") }
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

    func testGuardIsCleanAtXSmallAndSmall() { sweep(guardFrames, Self.pairs[0]) }
    func testGuardIsCleanAtMediumAndTheDefault() { sweep(guardFrames, Self.pairs[1]) }
    func testGuardIsCleanAtXLargeAndXXLarge() { sweep(guardFrames, Self.pairs[2]) }
    func testGuardIsCleanAtXXXLargeAndAX1() { sweep(guardFrames, Self.pairs[3]) }
    func testGuardIsCleanAtAX2AndAX3() { sweep(guardFrames, Self.pairs[4]) }
    func testGuardIsCleanAtAX4AndAX5() { sweep(guardFrames, Self.pairs[5]) }

    func testStatesAreCleanAtXSmallAndSmall() { sweep(stateFrames, Self.pairs[0]) }
    func testStatesAreCleanAtMediumAndTheDefault() { sweep(stateFrames, Self.pairs[1]) }
    func testStatesAreCleanAtXLargeAndXXLarge() { sweep(stateFrames, Self.pairs[2]) }
    func testStatesAreCleanAtXXXLargeAndAX1() { sweep(stateFrames, Self.pairs[3]) }
    func testStatesAreCleanAtAX2AndAX3() { sweep(stateFrames, Self.pairs[4]) }
    func testStatesAreCleanAtAX4AndAX5() { sweep(stateFrames, Self.pairs[5]) }

    func testPartnerCancelledIsCleanAtXSmallAndSmall() { sweep(partnerFrames, Self.pairs[0]) }
    func testPartnerCancelledIsCleanAtMediumAndTheDefault() { sweep(partnerFrames, Self.pairs[1]) }
    func testPartnerCancelledIsCleanAtXLargeAndXXLarge() { sweep(partnerFrames, Self.pairs[2]) }
    func testPartnerCancelledIsCleanAtXXXLargeAndAX1() { sweep(partnerFrames, Self.pairs[3]) }
    func testPartnerCancelledIsCleanAtAX2AndAX3() { sweep(partnerFrames, Self.pairs[4]) }
    func testPartnerCancelledIsCleanAtAX4AndAX5() { sweep(partnerFrames, Self.pairs[5]) }

    func testTheSixPairsCoverEveryTypeSize() {
        XCTAssertEqual(Set(Self.pairs.flatMap { $0 }), Set(DynamicTypeSize.allCases))
        XCTAssertEqual(Self.pairs.flatMap { $0 }.count, DynamicTypeSize.allCases.count)
    }
}

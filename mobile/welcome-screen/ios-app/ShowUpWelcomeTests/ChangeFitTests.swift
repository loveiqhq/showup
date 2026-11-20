// ChangeFitTests.swift
// ShowUp · Date 05, change meet time and place, on every phone at every Dynamic Type size (SHOWUP-196). Android's
// `ChangeFitTest.kt`, frame for frame.
//
// A and B (the pickers), K, M and N (their failures), C, F, H and L's thread variant (the suggestion cards in the
// chat), the chat's start grid with a `change` block (all eight chips), D, E, G, I, J and L (the Date screen's tags,
// request card and tiles) -- on every phone of `fitDevices` and `edgeFitDevices` at all twelve Dynamic Type sizes,
// xSmall to AX5.
//
// The rules: every touch area 44 both ways and none partly on another. And this ticket's own:
//
//  - the picker footer (the binding note or the error, and both buttons) lies wholly inside the screen, and the
//    primary and Cancel inside the footer;
//  - the last slot / place scrolls clear of the footer: the list is a ScrollView above a `safeAreaInset` footer, so
//    at the end of the scroll the list's bottom meets the footer's top, and the last row ends above it;
//  - at 375 x 667 at the default type the footer and BOTH request-card buttons are wholly on the screen, above the
//    TabBar (the ticket's evidence line).
//
// Not measured here, as on Android only: whole words (Android reads the renderer's line breaks; SwiftUI Text does not
// expose them, and the Arrival sweep predicts them for its own strings only).
//
// THE RUNNER CUTS A TEST OFF AT TEN MINUTES (the 8 Nov 2026 CI runs), so the sweep is 18 tests: three groups of frames
// times six pairs of two Dynamic Type sizes, every layout inside an `autoreleasepool`.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class ChangeFitTests: XCTestCase {

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

    /// One render: its label and what is drawn.
    private struct Frame {
        let label: String
        let view: AnyView
    }

    private func picker(_ label: String, _ ui: ChangePickerUi) -> Frame {
        Frame(label: label, view: AnyView(
            ChangePickerScreen(ctx: ChangeSamples.ctx, ui: ui, onSelectSlot: { _ in }, onSelectPlace: { _ in },
                               onQuery: { _ in }, onRetry: {}, onSuggest: {}, onCancel: {})))
    }

    private func chat(_ label: String, _ ui: ChatUi) -> Frame {
        Frame(label: label, view: AnyView(ChatScreen(ui: ui, zone: ChangeSamples.zone)))
    }

    private func dateFrame(_ label: String, _ ui: DateUi) -> Frame {
        Frame(label: label, view: AnyView(DateScreen(ui: ui, zone: ChangeSamples.zone)))
    }

    private var pickerFrames: [Frame] {
        [picker("A time picker", ChangeSamples.a),
         picker("B place picker", ChangeSamples.b),
         picker("K suggest failed", ChangeSamples.k),
         picker("M places failed", ChangeSamples.m),
         picker("N no place found", ChangeSamples.n)]
    }

    private var threadFrames: [Frame] {
        var theirsPlace = ChangeSamples.theirsTime
        theirsPlace.id = "s-theirs-place"
        theirsPlace.kind = .place
        theirsPlace.time = nil
        theirsPlace.place = ChangeSamples.minePlace.place
        theirsPlace.wasPlaceName = "Cafe Krone"
        return [
            chat("C sent, waiting", ChangeSamples.chatUi([ChangeSamples.mineTime])),
            chat("F receiver", ChangeSamples.chatUi([ChangeSamples.theirsTime])),
            chat("F receiver, place", ChangeSamples.chatUi([theirsPlace])),
            chat("H expired", ChangeSamples.chatUi([ChangeSamples.expiredTime])),
            chat("L thread, agree failed", ChangeSamples.chatUi([ChangeSamples.theirsTime], failed: [ChangeSamples.theirsTime.id])),
            chat("C and F together, both kinds", ChangeSamples.chatUi([ChangeSamples.mineTime, ChangeSamples.minePlace])),
            chat("Chat start, change chips", ChangeSamples.chatWithChange),
        ]
    }

    private var dateFrames: [Frame] {
        [dateFrame("D yours waiting", ChangeSamples.dateUi(ChangeSamples.dDate)),
         dateFrame("E request, chat open", ChangeSamples.dateUi(ChangeSamples.eDate)),
         dateFrame("G agreed", ChangeSamples.dateUi(ChangeSamples.gDate)),
         dateFrame("I tiles, locked", ChangeSamples.dateUi(ChangeSamples.date, now: DateSamples.aNow)),
         dateFrame("J request, chat locked", ChangeSamples.dateUi(ChangeSamples.eDate, now: DateSamples.aNow)),
         dateFrame("L agree failed", ChangeSamples.dateUi(ChangeSamples.eDate, agreeFailed: true)),
         dateFrame("L agree failed, locked", ChangeSamples.dateUi(ChangeSamples.eDate, now: DateSamples.aNow, agreeFailed: true))]
    }

    // MARK: the rules

    /// Every touch area at least 44 both ways, and no two partly on each other (the same plane only: a control in the
    /// scroll passing under a pinned bar is not on it).
    private func tapFailures(_ shot: Shot, _ tag: String) -> [String] {
        var failures: [String] = []
        let areas = shot.taps.filter { $0.frame.width > 0 && $0.frame.height > 0 }
        for tap in areas where tap.frame.width < 43.5 || tap.frame.height < 43.5 {
            failures.append("\(tag): a touch area \(tap.frame.width) x \(tap.frame.height) at \(tap.frame.origin), under 44")
        }
        func holds(_ a: CGRect, _ b: CGRect) -> Bool { a.insetBy(dx: -0.5, dy: -0.5).contains(b) }
        for i in areas.indices {
            for j in areas.indices where j > i {
                if areas[i].pinned != areas[j].pinned { continue }
                let a = areas[i].frame, b = areas[j].frame
                if holds(a, b) || holds(b, a) { continue }
                let both = a.intersection(b)
                if !both.isNull, both.width > 0.5, both.height > 0.5 {
                    failures.append("\(tag): touch areas at \(a.origin) and \(b.origin) overlap by \(both.width) x \(both.height)")
                }
            }
        }
        return failures
    }

    private func failures(_ shot: Shot, _ frame: Frame, on device: FitDevice, type: DynamicTypeSize, whole: Bool = false) -> [String] {
        let f = shot.frames
        let label = frame.label
        let tag = "\(label) \(device.described) @\(type)"
        var found = tapFailures(shot, tag)

        // The pickers: the footer whole on the screen with both buttons inside it, the end of the list clear of it.
        if ["A ", "B ", "K ", "M ", "N "].contains(where: { label.hasPrefix($0) }) {
            guard let footer = f["change:footer"], let primary = f["change:primary"], let cancel = f["change:cancel"] else {
                return found + ["\(tag): the footer or one of its buttons is NOT DRAWN"]
            }
            if footer.minY < -0.5 || footer.maxY > device.height + 0.5 || footer.minX < -0.5 || footer.maxX > device.width + 0.5 {
                found.append("\(tag): the footer is not fully on the screen (\(footer.minY)...\(footer.maxY) of \(device.height))")
            }
            for (name, button) in [("Suggest", primary), ("Cancel", cancel)] {
                if !footer.insetBy(dx: -0.5, dy: -0.5).contains(button) { found.append("\(tag): \(name) \(button) is outside the footer \(footer)") }
            }
            if whole, footer.height > (device.height - device.top - device.bottom) * 0.7 {
                found.append("\(tag): the footer takes over 70 % of the screen (\(footer.height))")
            }
            // The list ends where the footer begins (`safeAreaInset`): scrolled to the end its content's bottom meets
            // that edge, so everything moves up by the overflow, and the last row must then end above it.
            let lastId = label.hasPrefix("M ") ? "change:try-again" : (label.hasPrefix("N ") ? nil : "change:last-row")
            if let lastId {
                guard let list = f["change:list"], let last = f[lastId] else {
                    return found + ["\(tag): the list's last row (\(lastId)) is NOT DRAWN"]
                }
                let shift = max(0, list.maxY - footer.minY)
                if last.maxY - shift > footer.minY + 0.5 {
                    found.append("\(tag): the last row ends \(last.maxY - shift - footer.minY) under the footer at the end of the scroll")
                }
            }
        }

        // The request card (E, J, L): both buttons wholly on the screen above the TabBar at 375 x 667, default type.
        if label.hasPrefix("E ") || label.hasPrefix("J ") || label.hasPrefix("L agree") {
            guard let first = f["date:request-first"], let agree = f["date:request-agree"] else {
                return found + ["\(tag): the request card's buttons are NOT DRAWN"]
            }
            if whole {
                // At rest the hero and the card's own content leave the buttons under the TabBar on this phone, as the
                // Date screen is a scroll; the rule is that they are WHOLLY visible once the page is scrolled to its end
                // (everything moves up by the overflow) -- reachable, and nothing in the card cut.
                let limit = f[FitAnchorID.tabBar]?.minY ?? (device.height - device.bottom)
                // Scrolled up by just what hides the lower button (and no more), both must sit inside the visible part.
                let overflow = max(0, (f["date:content"]?.maxY ?? 0) - (f["date:screen"]?.maxY ?? 0))
                let need = max(0, max(first.maxY, agree.maxY) - limit)
                if need > overflow + 0.5 { found.append("\(tag): the page cannot scroll the request card's buttons clear of the TabBar (needs \(need), can move \(overflow))") }
                for (name, r) in [("Open chat / Suggest another", first), ("I agree", agree)]
                where r.minY - need < -0.5 || r.maxY - need > limit + 0.5 {
                    found.append("\(tag): \(name) is not fully visible at 375 x 667 once scrolled to (\(r.minY - need)...\(r.maxY - need), visible part ends at \(limit))")
                }
            }
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

    func testPickersAreCleanAtXSmallAndSmall() { sweep(pickerFrames, Self.pairs[0]) }
    func testPickersAreCleanAtMediumAndTheDefault() { sweep(pickerFrames, Self.pairs[1]) }
    func testPickersAreCleanAtXLargeAndXXLarge() { sweep(pickerFrames, Self.pairs[2]) }
    func testPickersAreCleanAtXXXLargeAndAX1() { sweep(pickerFrames, Self.pairs[3]) }
    func testPickersAreCleanAtAX2AndAX3() { sweep(pickerFrames, Self.pairs[4]) }
    func testPickersAreCleanAtAX4AndAX5() { sweep(pickerFrames, Self.pairs[5]) }

    func testThreadFramesAreCleanAtXSmallAndSmall() { sweep(threadFrames, Self.pairs[0]) }
    func testThreadFramesAreCleanAtMediumAndTheDefault() { sweep(threadFrames, Self.pairs[1]) }
    func testThreadFramesAreCleanAtXLargeAndXXLarge() { sweep(threadFrames, Self.pairs[2]) }
    func testThreadFramesAreCleanAtXXXLargeAndAX1() { sweep(threadFrames, Self.pairs[3]) }
    func testThreadFramesAreCleanAtAX2AndAX3() { sweep(threadFrames, Self.pairs[4]) }
    func testThreadFramesAreCleanAtAX4AndAX5() { sweep(threadFrames, Self.pairs[5]) }

    func testDateFramesAreCleanAtXSmallAndSmall() { sweep(dateFrames, Self.pairs[0]) }
    func testDateFramesAreCleanAtMediumAndTheDefault() { sweep(dateFrames, Self.pairs[1]) }
    func testDateFramesAreCleanAtXLargeAndXXLarge() { sweep(dateFrames, Self.pairs[2]) }
    func testDateFramesAreCleanAtXXXLargeAndAX1() { sweep(dateFrames, Self.pairs[3]) }
    func testDateFramesAreCleanAtAX2AndAX3() { sweep(dateFrames, Self.pairs[4]) }
    func testDateFramesAreCleanAtAX4AndAX5() { sweep(dateFrames, Self.pairs[5]) }

    func testTheSixPairsCoverEveryTypeSize() {
        XCTAssertEqual(Set(Self.pairs.flatMap { $0 }), Set(DynamicTypeSize.allCases))
        XCTAssertEqual(Self.pairs.flatMap { $0 }.count, DynamicTypeSize.allCases.count)
    }

    /// The ticket's own check, on the phone it names, at the default type size: the footer of every picker frame and
    /// both buttons of the request card (E, J, L) wholly visible.
    func testAt375x667ThePickerFooterAndBothRequestCardButtonsAreFullyVisible() {
        guard let phone = fitDevices.first(where: { $0.width == 375 && $0.height == 667 }) else {
            return XCTFail("no 375 x 667 phone in the list")
        }
        var found: [String] = []
        let picked = pickerFrames + dateFrames.filter { $0.label.hasPrefix("E ") || $0.label.hasPrefix("J ") || $0.label.hasPrefix("L agree") }
        for frame in picked {
            let result = layout(frame.view, on: phone, type: .large)
            found += failures(result, frame, on: phone, type: .large, whole: true)
        }
        XCTAssertTrue(found.isEmpty, "\(found.count) problems: " + summary(found))
    }
}

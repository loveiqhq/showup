// ArrivalFitTests.swift
// ShowUp · the arrival check-in on every phone at every Dynamic Type size (SHOWUP-195). Android's
// `ArrivalFitTest.kt`, frame for frame.
//
// A (the Date screen with the Check-in bar, and with the End date link switched on), B, B with a note, C, D,
// E (after Close: the footer empty), F (the chat's start grid with B or C over it), H (the arrival card hoisted,
// the chat open and still locked) and G (the date gate: not determined, denied, services off; today with the
// pill, after the meet time with the late pill, tomorrow with none) -- on every phone of `fitDevices` and
// `edgeFitDevices` at all twelve Dynamic Type sizes, xSmall to AX5.
//
// The rules are the 193 / 194 sweeps': every touch area 44 both ways and none partly on another, nothing off the
// screen, cards in the 16 gutters and the end of the Date screen's scroll resting above the footer. And this
// ticket's own:
//
//  - the bar (R12): the text column keeps its longest word whole at every size, the button inside the bar and
//    inside its 16 padding, the bar on the TabBar -- or, once bar + TabBar pass 60 % of the screen, in the scroll
//    above it;
//  - the sheets (R9): at 375 x 667 at the default type `Not yet`, `Yes, I've arrived` / `Try again`, C's error box,
//    D's `Close` and `Undo check-in` lie wholly inside the sheet's visible part -- under the 88 % cap's scroll edge
//    and off the bottom of the screen alike count as not visible (the ticket's evidence line); no title or body
//    word wider than the sheet's text column up to the largest size Android draws (200 %, AX2 here);
//  - the gate (G): the headline's widest word, as drawn, inside its box at every size (`FitWords`), and the late
//    pill inside the card -- it wraps rather than runs out of it.
//
// THE RUNNER CUTS A TEST OFF AT TEN MINUTES (the 8 Nov 2026 CI runs), so the sweep is 18 tests: three groups of
// frames (the bar frames, the sheets over the Date screen, the chat's and the gate's) times six pairs of two
// Dynamic Type sizes, every layout inside an `autoreleasepool`.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class ArrivalFitTests: XCTestCase {

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

    /// `view` laid out on `device` at `type`, given `settle` seconds for a sheet's rise (28 pt over 0.36 s) to end.
    private func layout(_ view: some View, on device: FitDevice, type: DynamicTypeSize, settle: TimeInterval = 0.05) -> Shot {
        // Each layout's window and controller let go at once: a sweep lays out thousands, and held to the end
        // of the test they ran the runner out before the sweep ended (the 8 Nov 2026 CI run, at twelve sizes).
        autoreleasepool { shot(view, on: device, type: type, settle: settle) }
    }

    private func shot(_ view: some View, on device: FitDevice, type: DynamicTypeSize, settle: TimeInterval) -> Shot {
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
        RunLoop.main.run(until: Date().addingTimeInterval(settle))
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

    /// The type size as a factor of the default, as `@ScaledMetric(relativeTo: .body)` has it.
    private func scale(_ type: DynamicTypeSize) -> CGFloat {
        let category: UIContentSizeCategory
        switch type {
        case .xSmall: category = .extraSmall
        case .small: category = .small
        case .medium: category = .medium
        case .large: category = .large
        case .xLarge: category = .extraLarge
        case .xxLarge: category = .extraExtraLarge
        case .xxxLarge: category = .extraExtraExtraLarge
        case .accessibility1: category = .accessibilityMedium
        case .accessibility2: category = .accessibilityLarge
        case .accessibility3: category = .accessibilityExtraLarge
        case .accessibility4: category = .accessibilityExtraExtraLarge
        case .accessibility5: category = .accessibilityExtraExtraExtraLarge
        @unknown default: category = .large
        }
        return UIFontMetrics(forTextStyle: .body)
            .scaledValue(for: 100, compatibleWith: UITraitCollection(preferredContentSizeCategory: category)) / 100
    }

    // MARK: the frames

    private let name = DateSamples.date.match.name
    private var openUi: DateUi { DateUi(date: DateSamples.date, now: DateSamples.bNow, loading: false) }
    private var locked: DateUi { DateUi(date: DateSamples.date, now: DateSamples.aNow, loading: false) }
    private var checkedIn: DateUi {
        var date = DateSamples.date
        date.checkedIn = true
        return DateUi(date: date, now: DateSamples.bNow, loading: false)
    }

    private func arrived(_ ui: DateUi) -> DateUi {
        var out = ui
        out.date?.arrival = DateArrival(checkedInAt: ui.now.addingTimeInterval(-10))
        return out
    }

    /// One render: its label and what is drawn. `scrolls`: the Date screen's end-of-scroll rule applies.
    private struct Frame {
        let label: String
        let scrolls: Bool
        let view: AnyView
    }

    private func dateScreen(_ ui: DateUi, link: Bool = false) -> AnyView {
        let who = name
        return AnyView(DateScreen(ui: ui, zone: DateSamples.zone,
                                  checkInBar: { AnyView(CheckInBar(name: who, onCheckIn: {}, onEnd: link ? {} : nil)) }))
    }

    private func barFrame(_ label: String, _ ui: DateUi, link: Bool = false) -> Frame {
        Frame(label: label, scrolls: true, view: dateScreen(ui, link: link))
    }

    private func sheet(_ label: String, _ phase: GatePhase) -> Frame {
        let who = name
        let behind = dateScreen(openUi)
        return Frame(label: label, scrolls: false, view: AnyView(
            ArrivalOver(ui: GateUi(phase: phase), name: who, onYes: {}, onNotYet: {}, onClose: {}, onUndo: {},
                        content: { behind })))
    }

    private func confirm(locError: Bool = false, busy: Bool = false, note: String? = nil) -> GatePhase {
        .confirm(entry: .dateScreen, locError: locError, busy: busy, note: note)
    }

    private var barFrames: [Frame] {
        [barFrame("A bar", openUi),
         barFrame("A bar, End date link", openUi, link: true),
         barFrame("A bar, locked", locked),
         barFrame("E after Close", checkedIn),
         barFrame("H arrival card", arrived(openUi)),
         barFrame("H arrival card, locked", arrived(locked))]
    }

    private var sheetFrames: [Frame] {
        [sheet("B guard", confirm()),
         sheet("B guard + note", confirm(note: ArrivalCopy.reportNote)),
         sheet("B guard, busy", confirm(busy: true)),
         sheet("C no location fix", confirm(locError: true)),
         sheet("C no location fix + note", confirm(locError: true, note: ArrivalCopy.reportNote)),
         sheet("D done", .done(entry: .dateScreen))]
    }

    private func chatSheet(_ label: String, locError: Bool) -> Frame {
        let who = name
        return Frame(label: label, scrolls: false, view: AnyView(
            ArrivalOver(ui: GateUi(phase: .confirm(entry: .chatChip, locError: locError, busy: false, note: nil)),
                        name: who, onYes: {}, onNotYet: {}, onClose: {}, onUndo: {},
                        content: { ChatScreen(ui: ChatSamples.b, zone: DateSamples.zone) })))
    }

    private func gate(_ label: String, _ state: LocationState, now: Date, day: String = "today",
                      place: String? = "Cafe Krone") -> Frame {
        let who = name
        return Frame(label: label, scrolls: false, view: AnyView(
            ProfileLocationView(state: state, context: .date(LocationDateContext(
                name: who, meetDay: day, meetAt: DateSamples.date.meetAt, place: place, photoUrl: nil, now: now,
                zone: DateSamples.zone, onTab: { _ in })))))
    }

    private var chatAndGateFrames: [Frame] {
        let meet = DateSamples.date.meetAt
        return [chatSheet("F chat start + B", locError: false),
                chatSheet("F chat start + C", locError: true),
                gate("G not determined, today", .ask, now: DateSamples.bNow),
                gate("G denied, today", .denied, now: DateSamples.bNow),
                gate("G services off, today", .servicesOff, now: DateSamples.bNow),
                gate("G denied, after the meet", .denied, now: meet.addingTimeInterval(10 * 60)),
                gate("G denied, tomorrow", .denied, now: meet.addingTimeInterval(-20 * 3600), day: "tomorrow"),
                gate("G denied, no place", .denied, now: DateSamples.bNow, place: nil)]
    }

    // MARK: the rules

    private let cards = ["date:hero", "date:arrival-card", "date:time-card", "date:coordinate", "date:report",
                         "date:intent", "date:actions", "date:about", "date:common", "date:works"]

    /// Every touch area at least 44 both ways, and no two partly on each other. With a sheet up nothing behind it
    /// answers: only its own controls are compared. Otherwise a control in the scroll passing under the pinned
    /// footer is not an overlap (the footer is on top and the control scrolls clear).
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

    /// Whether `text`'s widest word, in `font` at the type size, is wider than `width`: a word the label would break
    /// between two letters.
    private func brokenWord(_ text: String, _ font: UIFont, type: DynamicTypeSize, within width: CGFloat) -> String? {
        let scaled = font.withSize(font.pointSize * scale(type))
        let widest = widestWordWidth(text, scaled)
        return widest > width + 0.5 ? "a word of \"\(text.prefix(30))\" is \(widest) wide in \(width)" : nil
    }

    /// `strict`: the 375 x 667 statement -- the sheet's controls wholly inside its visible part.
    private func failures(_ shot: Shot, _ frame: Frame, on device: FitDevice, type: DynamicTypeSize, strict: Bool = false) -> [String] {
        let f = shot.frames
        let label = frame.label
        let tag = "\(label) \(device.described) @\(type)"
        var found = tapFailures(shot, tag)

        // The bar (R12): inside the screen, the text column keeps its longest word, the button inside the bar and
        // its 16 padding, the bar on the TabBar -- or in the scroll above it past 60 %.
        let bar = f[FitAnchorID.arrivalBar]
        let tab = f[FitAnchorID.tabBar]
        let footer = f["date:footer"]
        if let bar {
            if bar.minX < -0.5 || bar.maxX > device.width + 0.5 { found.append("\(tag): the bar is outside the screen (\(bar.minX)...\(bar.maxX))") }
            if let button = f[FitAnchorID.arrivalCheckIn] {
                if !bar.insetBy(dx: -0.5, dy: -0.5).contains(button) { found.append("\(tag): Check-in \(button) is outside the bar \(bar)") }
                if button.maxX > bar.maxX - 16 + 0.5 { found.append("\(tag): Check-in runs \(button.maxX - (bar.maxX - 16)) past the bar's 16 padding") }
            }
            if let text = f[FitAnchorID.arrivalBarText] {
                let title = widestWordWidth(ArrivalCopy.barTitle(name), chatFont(PS.loraBold, 18, .bold))
                let line = widestWordWidth(ArrivalCopy.barLine, chatFont(PS.manropeRegular, 12.5, .regular))
                let need = max(title, line) * scale(type)
                if text.width + 0.5 < need { found.append("\(tag): the bar's text is \(text.width) wide, its longest word needs \(need)") }
            }
            if let tab, let footer, let screen = f["date:screen"] {
                let shouldPin = bar.height + tab.height <= screen.height * 0.6 + 1
                let pinned = footer.height > tab.height + 0.5
                if shouldPin && !pinned { found.append("\(tag): the bar is not on the TabBar (bar \(bar.height), TabBar \(tab.height) of \(screen.height))") }
                if !shouldPin && pinned { found.append("\(tag): the bar is pinned though it and the TabBar take over 60 % (\(bar.height) + \(tab.height) of \(screen.height))") }
            }
        }

        // The Date screen's scroll: scrolled to the very end the content's bottom meets the scroll's, everything
        // moves up by the overflow; Cancel then rests above the footer and a bar that sits in the scroll above the
        // TabBar. As DateFitTests.
        if frame.scrolls {
            guard let cancel = f["date:cancel"], let screen = f["date:screen"], let footer, let content = f["date:content"] else {
                return found + ["\(tag): an anchor is missing"]
            }
            let shift = max(0, content.maxY - screen.maxY)
            if cancel.maxY - shift > footer.minY + 0.5 {
                found.append("\(tag): Cancel ends \(cancel.maxY - shift - footer.minY) under the footer at the end of the scroll")
            }
            if let bar, let tab, footer.height <= tab.height + 0.5, bar.maxY - shift > footer.minY + 0.5 {
                found.append("\(tag): the bar in the scroll ends \(bar.maxY - shift - footer.minY) under the TabBar")
            }
            // The cards: a column, every one in the 16 gutters, none on the next (H's included).
            let column = cards.compactMap { id in f[id].map { (id, $0) } }.sorted { $0.1.minY < $1.1.minY }
            for (id, card) in column where card.minX < 15.5 || card.maxX > device.width - 15.5 {
                found.append("\(tag): \(id) outside the 16 gutters (\(card.minX)...\(card.maxX))")
            }
            for (a, b) in zip(column, column.dropFirst()) where b.1.minY < a.1.maxY - 0.5 {
                found.append("\(tag): \(a.0) and \(b.0) overlap by \(a.1.maxY - b.1.minY)")
            }
        }

        // The sheets: every control inside the screen's width; at 375 x 667 at the default type, wholly inside the
        // visible part -- under the lower of the 88 % cap's top and the sheet's own top, above the frame's foot.
        if let sheet = f[FitAnchorID.arrivalSheet] {
            let visibleTop = max(device.height * (1 - 0.88), sheet.minY)
            let controls = [FitAnchorID.arrivalCircle, FitAnchorID.arrivalNotYet, FitAnchorID.arrivalYes, FitAnchorID.arrivalError,
                            FitAnchorID.arrivalClose, FitAnchorID.arrivalUndo]
            for id in controls {
                guard let r = f[id] else { continue }
                if r.minX < -0.5 || r.maxX > device.width + 0.5 { found.append("\(tag): \(id) is off the side (\(r.minX)...\(r.maxX))") }
                if strict, r.minY < visibleTop - 0.5 || r.maxY > device.height - device.bottom + 0.5 {
                    found.append("\(tag): \(id) is not fully inside the sheet's visible part at 375 x 667 (\(r.minY)...\(r.maxY) of \(visibleTop)...\(device.height - device.bottom))")
                }
            }
            // No word broken between two letters, up to the largest type Android draws (200 %: AX2).
            if scale(type) <= 2.0 {
                let wide = device.width - 48
                var words: [String?] = []
                if label.hasPrefix("D") {
                    words.append(brokenWord(ArrivalCopy.doneTitle, chatFont(PS.loraBold, 26, .bold), type: type, within: min(300, wide)))
                    words.append(brokenWord(ArrivalCopy.doneBody(name), chatFont(PS.manropeRegular, 15, .regular), type: type, within: min(300, wide)))
                } else {
                    words.append(brokenWord(ArrivalCopy.confirmTitle(name), chatFont(PS.loraBold, 26, .bold), type: type,
                                            within: min(confirmTitleMax, wide)))
                    if label.contains("note") {
                        words.append(brokenWord(ArrivalCopy.reportNote, chatFont(PS.manropeRegular, 14, .regular), type: type, within: min(310, wide)))
                    }
                    if label.hasPrefix("C") || label.hasSuffix("+ C") {
                        words.append(brokenWord(ArrivalCopy.locError, chatFont(PS.manropeSemi, 13.5, .semibold), type: type,
                                                within: wide - 54))
                    }
                }
                for case let problem? in words { found.append("\(tag): WORD BROKEN, \(problem)") }
            }
        }

        // A frame that draws nothing it is meant to would pass every rule above: say so.
        if label.hasPrefix("A bar"), bar == nil { found.append("\(tag): the bar is NOT DRAWN") }
        if label.hasPrefix("B") || label.hasPrefix("C") || label.hasPrefix("F"), f[FitAnchorID.arrivalNotYet] == nil {
            found.append("\(tag): Not yet is NOT DRAWN")
        }
        if label.hasPrefix("C") || label.hasSuffix("+ C"), f[FitAnchorID.arrivalError] == nil { found.append("\(tag): C's error box is NOT DRAWN") }
        if label.hasPrefix("D"), f[FitAnchorID.arrivalClose] == nil { found.append("\(tag): Close is NOT DRAWN") }
        if label.hasPrefix("H"), f["date:arrival-card"] == nil { found.append("\(tag): the arrival card is NOT DRAWN") }

        // The gate (G): its date card and CTA whole on the screen, the CTA above the TabBar, the headline's widest
        // word inside its box, the late pill inside the card.
        if label.hasPrefix("G") {
            guard let cta = f[FitAnchorID.gateCta] else { return found + ["\(tag): the gate's CTA is NOT DRAWN"] }
            if cta.maxY > device.height + 0.5 { found.append("\(tag): the gate's CTA runs \(cta.maxY - device.height) off the bottom") }
            if let tab, cta.maxY > tab.minY + 0.5 { found.append("\(tag): the gate's CTA is \(cta.maxY - tab.minY) behind the TabBar") }
            if let card = f[FitAnchorID.dateGateCard] {
                if card.minX < 15.5 || card.maxX > device.width - 15.5 { found.append("\(tag): the date card is outside the gutters (\(card.minX)...\(card.maxX))") }
                if let pill = f[FitAnchorID.dateGatePill], pill.maxX > card.maxX - 14 + 0.5 {
                    found.append("\(tag): the pill runs \(pill.maxX - (card.maxX - 14)) past the card's padding")
                }
            } else {
                found.append("\(tag): the date card is NOT DRAWN")
            }
            if let headline = f[FitAnchorID.gateHeadline], let word = f[FitAnchorID.dateGateHeadlineWord] {
                if word.width > headline.width + 0.5 { found.append("\(tag): the headline's widest word is \(word.width) wide in \(headline.width)") }
            } else {
                found.append("\(tag): the gate headline's anchors are NOT DRAWN")
            }
        }
        return found
    }

    /// One entry per kind of problem -- its device and type size aside -- with how often it came and its first
    /// case, so one run shows every kind rather than the first forty of one.
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
                    let shot = layout(frame.view, on: device, type: type)
                    found += failures(shot, frame, on: device, type: type)
                }
            }
        }
        XCTAssertTrue(found.isEmpty, "\(found.count) problems: " + summary(found))
    }

    // MARK: the sweeps -- 18 tests, three groups of frames times six pairs of sizes

    func testBarFramesAreCleanAtXSmallAndSmall() { sweep(barFrames, Self.pairs[0]) }
    func testBarFramesAreCleanAtMediumAndTheDefault() { sweep(barFrames, Self.pairs[1]) }
    func testBarFramesAreCleanAtXLargeAndXXLarge() { sweep(barFrames, Self.pairs[2]) }
    func testBarFramesAreCleanAtXXXLargeAndAX1() { sweep(barFrames, Self.pairs[3]) }
    func testBarFramesAreCleanAtAX2AndAX3() { sweep(barFrames, Self.pairs[4]) }
    func testBarFramesAreCleanAtAX4AndAX5() { sweep(barFrames, Self.pairs[5]) }

    func testSheetsOverTheDateScreenAreCleanAtXSmallAndSmall() { sweep(sheetFrames, Self.pairs[0]) }
    func testSheetsOverTheDateScreenAreCleanAtMediumAndTheDefault() { sweep(sheetFrames, Self.pairs[1]) }
    func testSheetsOverTheDateScreenAreCleanAtXLargeAndXXLarge() { sweep(sheetFrames, Self.pairs[2]) }
    func testSheetsOverTheDateScreenAreCleanAtXXXLargeAndAX1() { sweep(sheetFrames, Self.pairs[3]) }
    func testSheetsOverTheDateScreenAreCleanAtAX2AndAX3() { sweep(sheetFrames, Self.pairs[4]) }
    func testSheetsOverTheDateScreenAreCleanAtAX4AndAX5() { sweep(sheetFrames, Self.pairs[5]) }

    func testChatSheetsAndTheGateAreCleanAtXSmallAndSmall() { sweep(chatAndGateFrames, Self.pairs[0]) }
    func testChatSheetsAndTheGateAreCleanAtMediumAndTheDefault() { sweep(chatAndGateFrames, Self.pairs[1]) }
    func testChatSheetsAndTheGateAreCleanAtXLargeAndXXLarge() { sweep(chatAndGateFrames, Self.pairs[2]) }
    func testChatSheetsAndTheGateAreCleanAtXXXLargeAndAX1() { sweep(chatAndGateFrames, Self.pairs[3]) }
    func testChatSheetsAndTheGateAreCleanAtAX2AndAX3() { sweep(chatAndGateFrames, Self.pairs[4]) }
    func testChatSheetsAndTheGateAreCleanAtAX4AndAX5() { sweep(chatAndGateFrames, Self.pairs[5]) }

    func testTheSixPairsCoverEveryTypeSize() {
        XCTAssertEqual(Set(Self.pairs.flatMap { $0 }), Set(DynamicTypeSize.allCases))
        XCTAssertEqual(Self.pairs.flatMap { $0 }.count, DynamicTypeSize.allCases.count)
    }

    /// The ticket's own check, on the phone it names, at the default type size: B, B with a note, C, C with a note,
    /// D (Close and Undo) and F over the chat, once the sheet has risen.
    func testAt375x667TheGuidesButtonsAndTheErrorAreFullyVisible() {
        guard let phone = fitDevices.first(where: { $0.width == 375 && $0.height == 667 }) else {
            return XCTFail("no 375 x 667 phone in the list")
        }
        var found: [String] = []
        let picked = [sheetFrames[0], sheetFrames[1], sheetFrames[3], sheetFrames[4], sheetFrames[5]]
            + [chatSheet("F chat start + B", locError: false), chatSheet("F chat start + C", locError: true)]
        for frame in picked {
            let shot = layout(frame.view, on: phone, type: .large, settle: 0.6)
            found += failures(shot, frame, on: phone, type: .large, strict: true)
        }
        XCTAssertTrue(found.isEmpty, "\(found.count) problems: " + summary(found))
    }

    // MARK: the guard's title

    /// `text` as TextKit breaks it at `width` -- the guard title's own recipe (Lora 700 26 / 1.15, -0.01em, centred,
    /// pretty) -- one string a line.
    private func titleLines(_ text: String, width: CGFloat) -> [String] {
        let font = chatFont(PS.loraBold, 26, .bold)
        let para = NSMutableParagraphStyle()
        para.lineHeightMultiple = 26 * 1.15 / font.lineHeight
        para.alignment = .center
        let attributed = NSAttributedString(string: text, attributes: [.font: font, .paragraphStyle: para, .kern: -0.01 * 26])
        let used = CSSTextLayout.wrapWidth(attributed, within: width, pretty: true, balance: false)
        let storage = NSTextStorage(attributedString: CSSTextLayout.wrapping(attributed))
        let manager = NSLayoutManager()
        let container = NSTextContainer(size: CGSize(width: max(used, 1), height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        storage.addLayoutManager(manager)
        manager.addTextContainer(container)
        manager.ensureLayout(for: container)
        var lines: [String] = []
        manager.enumerateLineFragments(forGlyphRange: NSRange(location: 0, length: manager.numberOfGlyphs)) { _, _, _, glyphs, _ in
            let chars = manager.characterRange(forGlyphRange: glyphs, actualGlyphRange: nil)
            lines.append((text as NSString).substring(with: chars).trimmingCharacters(in: .whitespacesAndNewlines))
        }
        return lines
    }

    /// The guard's title breaks as the spec sheet draws it (Chrome with the bundled Lora): at 390 wide, default
    /// type, for `Leonie`. Pins `confirmTitleMax`, which is tuned to this one string.
    func testTheGuardsTitleBreaksIntoThreeLinesAsTheSpecSheetDraws() {
        let wide: CGFloat = 390 - 48
        let lines = titleLines(ArrivalCopy.confirmTitle("Leonie"), width: min(confirmTitleMax, wide))
        XCTAssertEqual(lines, ["Have you arrived at the", "meeting location for", "your date with Leonie?"])
    }
}

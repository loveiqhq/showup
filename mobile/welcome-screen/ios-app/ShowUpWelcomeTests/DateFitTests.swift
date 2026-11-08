// DateFitTests.swift
// ShowUp · the Date screen on every phone at every type size (SHOWUP-193)
//
// Android's `DateFitTest.kt`: the sheet's three states, after check-in, tomorrow, a date with nothing
// optional (no venue, no age, no district, no distance), the longest names, and the failed first read
// -- each must keep its cards inside the 16 gutters, in the ticket's order (C's Coordinate card above
// time + place), and the footer bar on the screen.
//
// THE 4 NOV 2026 DEVICE AUDIT widened it to every phone and every type size there is -- the shared
// list plus `edgeFitDevices` (Display Zoom, split screen, foldables open and folded, 16:9, budget HD+,
// the smallest display size, a tablet) and, since 8 Nov 2026, all twelve Dynamic Type sizes from xSmall
// to AX5 -- and added Android's new rules:
// every touch area 44 both ways and none partly on another, cards that never overlap, the screen
// exactly the phone's width, and the end of the scroll resting above the footer.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class DateFitTests: XCTestCase {

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

    /// Every anchor's frame and every touch area, in the window's coordinates.
    private struct Shot {
        let frames: [String: CGRect]
        let taps: [FitTap]
    }

    /// `view` laid out on `device` at `type`.
    private func layout(_ view: some View, on device: FitDevice, type: DynamicTypeSize) -> Shot {
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
        return Shot(frames: sink.frames, taps: sink.taps)
    }

    /// Every phone: the shared list and the edge cases it does not hold yet.
    private var phones: [FitDevice] { fitDevices + edgeFitDevices }

    /// Every Dynamic Type size there is, xSmall to AX5: the seven standard and the five accessibility sizes.
    private let types = DynamicTypeSize.allCases

    private var frames: [(String, DateUi)] {
        var long = DateSamples.date
        long.place = DatePlace(name: "The Very Long Name Café and Wine Bar Berlin",
                               address: "Am Kupfergraben 6a, Hinterhaus, 3. Etage, 10117 Berlin-Mitte")
        long.match.name = "Maximiliana-Konstantina"
        long.match.district = "Friedrichshain-Kreuzberg"
        var sparse = DateSamples.date
        sparse.place = nil
        sparse.match = DateMatch(userId: "u2", name: "Leonie", age: nil, district: nil, distanceKm: nil,
                                 photoUrl: nil, attendedCount: 0)
        sparse.motivationThem = "casual"
        sparse.sharedVibes = []
        sparse.sharedInterests = []
        var unread = DateSamples.date
        unread.unread = DateSamples.unread
        var tomorrow = DateSamples.date
        tomorrow.meetDay = "tomorrow"
        var checkedIn = DateSamples.date
        checkedIn.checkedIn = true
        return [
            ("A locked", DateUi(date: DateSamples.date, now: DateSamples.aNow, loading: false)),
            ("B open", DateUi(date: DateSamples.date, now: DateSamples.bNow, loading: false)),
            ("C unread", DateUi(date: unread, now: DateSamples.bNow, loading: false)),
            ("long names", DateUi(date: long, now: DateSamples.bNow, loading: false)),
            ("sparse", DateUi(date: sparse, now: DateSamples.aNow, loading: false)),
            ("tomorrow", DateUi(date: tomorrow, now: DateSamples.aNow.addingTimeInterval(-20 * 3600), loading: false)),
            ("checked in", DateUi(date: checkedIn, now: DateSamples.bNow, loading: false)),
        ]
    }

    private let cards = ["date:hero", "date:time-card", "date:coordinate", "date:report", "date:intent",
                         "date:actions", "date:about", "date:common", "date:works"]

    /// Every touch area at least 44 both ways, and no two partly on each other -- where a tap could land
    /// on the wrong one. One inside another is a nested control; a control in the scroll passing under
    /// the pinned footer is not an overlap, the footer is on top and the control scrolls clear.
    private func tapFailures(_ taps: [FitTap], _ tag: String) -> [String] {
        var failures: [String] = []
        let areas = taps.filter { $0.frame.width > 0 && $0.frame.height > 0 }
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

    func testEveryStateOfTheDateScreenIsCleanOnEveryPhoneAtEveryTypeSize() {
        var failures: [String] = []
        for (name, ui) in frames {
            for type in types {
                for device in phones {
                    let shot = layout(DateScreen(ui: ui, zone: DateSamples.zone), on: device, type: type)
                    let f = shot.frames
                    let tag = "\(name) \(device.described) @\(type)"
                    guard let time = f["date:time-card"], let coordinate = f["date:coordinate"],
                          let bar = f["date:footer-bar"], let cancel = f["date:cancel"], let screen = f["date:screen"],
                          let footer = f["date:footer"], let content = f["date:content"] else {
                        failures.append("\(tag): an anchor is missing"); continue
                    }
                    // The ticket's order: C's Coordinate card above time + place, below it otherwise.
                    if name == "C unread" {
                        if coordinate.maxY > time.minY + 0.5 { failures.append("\(tag): C's card is not above time + place") }
                    } else if coordinate.minY < time.maxY - 0.5 {
                        failures.append("\(tag): Coordinate is not under time + place")
                    }
                    // A column: every card inside the 16 gutters, none on the next.
                    let column = cards.compactMap { id in f[id].map { (id, $0) } }.sorted { $0.1.minY < $1.1.minY }
                    for (id, card) in column where card.minX < 15.5 || card.maxX > device.width - 15.5 {
                        failures.append("\(tag): \(id) outside the 16 gutters (\(card.minX)...\(card.maxX))")
                    }
                    for (a, b) in zip(column, column.dropFirst()) where b.1.minY < a.1.maxY - 0.5 {
                        failures.append("\(tag): \(a.0) and \(b.0) overlap by \(a.1.maxY - b.1.minY)")
                    }
                    // The screen is the phone's width, exactly -- a point wider moved every card (3 Nov 2026).
                    if abs(screen.width - device.width) > 0.5 || abs(footer.width - device.width) > 0.5 {
                        failures.append("\(tag): widths screen \(screen.width) footer \(footer.width) on a \(device.width) phone")
                    }
                    if bar.minX < -0.5 || bar.maxX > device.width + 0.5 { failures.append("\(tag): the bar is wider than the phone") }
                    if bar.minY < 0 { failures.append("\(tag): the bar starts above the screen") }
                    if footer.maxY > device.height + 0.5 { failures.append("\(tag): the footer runs off the bottom") }
                    // Scrolled to the very end, the content's bottom meets the scroll's: everything moves up
                    // by the overflow. Cancel must then rest above the footer, and a bar that moved into the
                    // scroll above the TabBar -- nothing reachable only behind them.
                    let shift = max(0, content.maxY - screen.maxY)
                    if cancel.maxY - shift > footer.minY + 0.5 {
                        failures.append("\(tag): Cancel ends \(cancel.maxY - shift - footer.minY) under the footer at the end of the scroll")
                    }
                    let barPinned = bar.minY >= footer.minY - 0.5
                    if !barPinned, bar.maxY - shift > footer.minY + 0.5 {
                        failures.append("\(tag): the bar in the scroll ends \(bar.maxY - shift - footer.minY) under the TabBar")
                    }
                    failures += tapFailures(shot.taps, tag)
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, "\(failures.count) problems: " + failures.prefix(40).joined(separator: " · "))
    }

    /// A with the change tiles switched on (5 Nov 2026), as Date 05 will show them: the card a column inside
    /// the gutters, every touch area 44 and none on another, on every phone at every size.
    func testTheChangeTilesAreCleanOnEveryPhoneAtEveryTypeSize() {
        var failures: [String] = []
        let ui = DateUi(date: DateSamples.date, now: DateSamples.aNow, loading: false)
        for type in types {
            for device in phones {
                let shot = layout(DateScreen(ui: ui, zone: DateSamples.zone, changeTiles: [.changeTime, .changePlace]),
                                  on: device, type: type)
                let tag = "A change tiles \(device.described) @\(type)"
                guard let coordinate = shot.frames["date:coordinate"] else { failures.append("\(tag): no Coordinate card"); continue }
                if coordinate.minX < 15.5 || coordinate.maxX > device.width - 15.5 {
                    failures.append("\(tag): the card is outside the 16 gutters")
                }
                failures += tapFailures(shot.taps, tag)
            }
        }
        XCTAssertTrue(failures.isEmpty, "\(failures.count) problems: " + failures.prefix(40).joined(separator: " · "))
    }

    /// The first read failed with nothing to show: the statement and the locked TabBar, on every phone.
    func testTheFailedFirstReadIsCleanOnEveryPhoneAtEveryTypeSize() {
        var failures: [String] = []
        let ui = DateUi(date: nil, now: DateSamples.aNow, loading: false, failed: .network)
        for type in types {
            for device in phones {
                let shot = layout(DateScreen(ui: ui, zone: DateSamples.zone), on: device, type: type)
                let tag = "failed \(device.described) @\(type)"
                guard let tab = shot.frames[FitAnchorID.tabBar] else { failures.append("\(tag): no TabBar"); continue }
                if tab.minX < -0.5 || tab.maxX > device.width + 0.5 || tab.maxY > device.height + 0.5 {
                    failures.append("\(tag): the TabBar is off the screen (\(tab))")
                }
                failures += tapFailures(shot.taps, tag)
            }
        }
        XCTAssertTrue(failures.isEmpty, "\(failures.count) problems: " + failures.prefix(40).joined(separator: " · "))
    }

    func testTheReadOnlyProfileHasNoActionBarOnAnyPhone() {
        var failures: [String] = []
        let state = SearchUiState(loading: false, queue: [SearchSamples.full])
        for type in [DynamicTypeSize.large, .accessibility5] {
            for device in phones {
                let f = layout(SearchProfileView(state: state, zone: DateSamples.zone, readOnly: true,
                                                 backLabel: DateCopy.profileBack, disabledTabs: dateLockedTabs,
                                                 disabledHint: DateCopy.tabHint),
                               on: device, type: type).frames
                let tag = "\(device.described) @\(type)"
                if f[FitAnchorID.searchActionBar] != nil { failures.append("\(tag): an action bar") }
                if f[FitAnchorID.searchTopBar] == nil { failures.append("\(tag): no top bar") }
                if let tab = f[FitAnchorID.tabBar], tab.maxX > device.width + 0.5 || tab.minX < -0.5 {
                    failures.append("\(tag): the TabBar is wider than the phone")
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }
}

// DateFitTests.swift
// ShowUp · the Date screen on every phone at the default and the largest accessibility type (SHOWUP-193)
//
// Android's `DateFitTest.kt`: the sheet's three states, after check-in, a date with nothing optional
// (no venue, no age, no district, no distance), and the longest names -- each must keep its cards inside
// the 16 gutters, in the ticket's order (C's Coordinate card above time + place), and the footer bar on
// the screen.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class DateFitTests: XCTestCase {

    private final class Sink {
        var frames: [String: CGRect] = [:]
        func record(_ value: [String: CGRect]) -> Bool {
            frames = value
            return true
        }
    }

    /// Every anchor's frame, in the window's coordinates, for `view` on `device`.
    private func layout(_ view: some View, on device: FitDevice, type: DynamicTypeSize) -> [String: CGRect] {
        let sink = Sink()
        let root = view
            .environment(\.dynamicTypeSize, type)
            .overlayPreferenceValue(FitFrameKey.self) { frames in
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
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        controller.view.layoutIfNeeded()
        window.isHidden = true
        return sink.frames
    }

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
        var checkedIn = DateSamples.date
        checkedIn.checkedIn = true
        return [
            ("A locked", DateUi(date: DateSamples.date, now: DateSamples.aNow, loading: false)),
            ("B open", DateUi(date: DateSamples.date, now: DateSamples.bNow, loading: false)),
            ("C unread", DateUi(date: unread, now: DateSamples.bNow, loading: false)),
            ("long names", DateUi(date: long, now: DateSamples.bNow, loading: false)),
            ("sparse", DateUi(date: sparse, now: DateSamples.aNow, loading: false)),
            ("checked in", DateUi(date: checkedIn, now: DateSamples.bNow, loading: false)),
        ]
    }

    private let cards = ["date:hero", "date:time-card", "date:coordinate", "date:report", "date:intent",
                         "date:actions", "date:about", "date:works"]

    func testEveryStateOfTheDateScreenIsCleanOnEveryPhoneAtTheDefaultAndTheLargestType() {
        var failures: [String] = []
        for (name, ui) in frames {
            for type in [DynamicTypeSize.large, .accessibility3] {
                for device in fitDevices {
                    let f = layout(DateScreen(ui: ui, zone: DateSamples.zone), on: device, type: type)
                    let tag = "\(name) \(device.described) @\(type)"
                    guard let time = f["date:time-card"], let coordinate = f["date:coordinate"],
                          let bar = f["date:footer-bar"], f["date:cancel"] != nil else {
                        failures.append("\(tag): an anchor is missing"); continue
                    }
                    // The ticket's order: C's Coordinate card above time + place, below it otherwise.
                    if name == "C unread" {
                        if coordinate.maxY > time.minY + 0.5 { failures.append("\(tag): C's card is not above time + place") }
                    } else if coordinate.minY < time.maxY - 0.5 {
                        failures.append("\(tag): Coordinate is not under time + place")
                    }
                    for id in cards {
                        guard let card = f[id] else { continue }
                        if card.minX < 15.5 || card.maxX > device.width - 15.5 {
                            failures.append("\(tag): \(id) outside the 16 gutters (\(card.minX)...\(card.maxX))")
                        }
                    }
                    if bar.minX < -0.5 || bar.maxX > device.width + 0.5 { failures.append("\(tag): the bar is wider than the phone") }
                    // Which piece is the widest, when anything is: the screen, the footer, the bar.
                    if let screen = f["date:screen"], let footer = f["date:footer"],
                       max(screen.width, footer.width, bar.width) > device.width + 0.5 {
                        failures.append("\(tag): widths screen \(screen.width) footer \(footer.width) bar \(bar.width)")
                    }
                    if bar.minY < 0 { failures.append("\(tag): the bar starts above the screen") }
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.prefix(40).joined(separator: " · "))
    }

    func testTheReadOnlyProfileHasNoActionBarOnAnyPhone() {
        var failures: [String] = []
        let state = SearchUiState(loading: false, queue: [SearchSamples.full])
        for device in fitDevices {
            let f = layout(SearchProfileView(state: state, zone: DateSamples.zone, readOnly: true,
                                             backLabel: DateCopy.profileBack, disabledTabs: dateLockedTabs,
                                             disabledHint: DateCopy.tabHint),
                           on: device, type: .large)
            if f[FitAnchorID.searchActionBar] != nil { failures.append("\(device.described): an action bar") }
            if f[FitAnchorID.searchTopBar] == nil { failures.append("\(device.described): no top bar") }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }
}

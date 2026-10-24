//
//  SearchFitTests.swift
//  ShowUp · Date search 01, measured on every phone (SHOWUP-184)
//
//  The Swift twin of `SearchFitTest.kt`'s geometry. The screen is hosted in a window with each
//  device's exact insets, as `ScreenFitTests` hosts its screens, and the views a test needs publish
//  their bounds (`FitAnchors.swift`) -- the iOS answer to Compose's test tags, because a pixel probe
//  cannot tell the centred Like pill from the orange chips under the action bar's fade. Held, on all
//  the phones, at the default type size and at the largest accessibility size:
//
//    · the action bar sits on the TabBar's top edge, never over it and never under the top bar
//    · Like is wholly on screen
//    · the coach sits above the buttons with its 14 gap and, at the default size, under nothing
//    · the meet line is one line, and the meet card is the drawn row from 390 wide
//

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class SearchFitTests: XCTestCase {

    /// Where the anchors' frames land, recorded during the layout pass.
    private final class Sink {
        var frames: [String: CGRect] = [:]
        func record(_ value: [String: CGRect]) -> Bool {
            frames = value
            return true
        }
    }

    /// Every anchor's frame, in the window's coordinates, for `state` on `device`.
    private func layout(_ state: SearchUiState, on device: FitDevice,
                        type: DynamicTypeSize = .large) -> [String: CGRect] {
        let sink = Sink()
        let root = SearchProfileView(state: state, zone: SearchSamples.zone)
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
        // One turn of the run loop: a preference resolved in the first pass is read in the next.
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        controller.view.layoutIfNeeded()
        window.isHidden = true
        return sink.frames
    }

    private func coached() -> SearchUiState {
        var s = SearchSamples.rest()
        s.coach = true
        return s
    }

    private let sizes: [DynamicTypeSize] = [.large, .accessibility3]

    func testTheActionBarSitsOnTheTabBarWhollyOnScreenOnEveryPhone() {
        var failures: [String] = []
        for device in fitDevices {
            for type in sizes {
                let f = layout(SearchSamples.rest(), on: device, type: type)
                guard let bar = f[FitAnchorID.searchActionBar], let tab = f[FitAnchorID.tabBar],
                      let top = f[FitAnchorID.searchTopBar], let like = f[FitAnchorID.searchLike] else {
                    failures.append("\(device.described) \(type): an anchor is missing"); continue
                }
                if bar.minY < top.maxY - 0.5 { failures.append("\(device.described) \(type): the bar reaches the top bar") }
                if abs(bar.maxY - tab.minY) > 1 { failures.append("\(device.described) \(type): bar \(bar.maxY) not on the TabBar \(tab.minY)") }
                if like.maxY > device.height - device.bottom + 0.5 { failures.append("\(device.described) \(type): Like is off the screen") }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    func testTheCoachSitsAboveTheButtonsAndUnderNothingOnEveryPhone() {
        var failures: [String] = []
        var minimum = SearchSamples.rest(SearchSamples.minimum)
        minimum.coach = true
        for state in [coached(), minimum] {
            for device in fitDevices {
                for type in sizes {
                    let f = layout(state, on: device, type: type)
                    guard let coach = f[FitAnchorID.searchCoach], let like = f[FitAnchorID.searchLike],
                          let top = f[FitAnchorID.searchTopBar] else {
                        failures.append("\(device.described) \(type): an anchor is missing"); continue
                    }
                    // The caret hangs 7 into the 14 gap; the card itself ends 14 above the buttons.
                    if coach.maxY > like.minY - 13.5 { failures.append("\(device.described) \(type): the coach overlaps the buttons") }
                    if type == .large && coach.minY < top.maxY - 0.5 { failures.append("\(device.described): the coach is under the top bar") }
                    if coach.minY < device.top { failures.append("\(device.described) \(type): the coach is above the screen") }
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    func testTheMeetLineIsOneLineAndTheMeetCardTheDrawnRowFrom390Wide() {
        var failures: [String] = []
        for profile in [SearchSamples.full, SearchSamples.minimum] {
            for device in fitDevices {
                let f = layout(SearchSamples.rest(profile), on: device)
                guard let line = f[FitAnchorID.searchMeetLine], let count = f[FitAnchorID.searchMeetCount] else {
                    failures.append("\(device.described): an anchor is missing"); continue
                }
                // Manrope 700 / 15: one line is under 22 tall; a second would take it past 40.
                if line.height > 30 { failures.append("\(device.described): the meet line wraps (\(line.height))") }
                let oneRow = count.minY < line.maxY && count.maxY > line.minY - 20
                if device.width >= 390 && !oneRow { failures.append("\(device.described): the count left the meet card's row") }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    /// The instrument fires: a frame with no Search profile publishes no action bar.
    func testTheAnchorsReportWhatIsThereAndNothingElse() throws {
        let device = try XCTUnwrap(fitDevices.first { $0.width == 390 })
        let loaded = layout(SearchSamples.rest(), on: device)
        XCTAssertNotNil(loaded[FitAnchorID.searchLike])
        XCTAssertNotNil(loaded[FitAnchorID.tabBar])
        var empty = SearchUiState()
        empty.loading = false
        let none = layout(empty, on: device)
        XCTAssertNil(none[FitAnchorID.searchLike], "no profile, no action bar")
        XCTAssertNotNil(none[FitAnchorID.tabBar])
    }
}

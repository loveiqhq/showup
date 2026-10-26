//
//  PremiumFitTests.swift
//  ShowUp · the Premium grid (SHOWUP-185) and the Likes tab (SHOWUP-186), measured on every phone
//
//  The Swift twin of `PremiumFitTest.kt`'s geometry. Each screen is hosted in a window with each
//  device's exact insets, as `SearchFitTests` hosts Search 01, and the views a test needs publish their
//  frames (`FitAnchors.swift`). Held on all the phones, at the default type size and at the largest
//  accessibility size:
//
//    · the grid is two columns, 16 from each side with a 12 gap, every card 248 -- and at a large type
//      every card the same, taller height
//    · the meet line on a card is one line from 375 wide
//    · the top bar and the TabBar frame the one scroll region, nothing off the screen
//    · the undo toast floats 16 from each side and 12 above the TabBar
//    · the Likes cards fill the width, `View profile` inside its card; the empty state's action is
//      reachable
//    · a profile opened from the grid shows its back pill, and its actions sit on the TabBar
//    · the sorry sheet's button is on the screen
//

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class PremiumFitTests: XCTestCase {

    /// Where the anchors' frames land, recorded during the layout pass.
    private final class Sink {
        var frames: [String: CGRect] = [:]
        func record(_ value: [String: CGRect]) -> Bool {
            frames = value
            return true
        }
    }

    /// Every anchor's frame, in the window's coordinates, for `view` on `device`.
    private func layout(_ view: some View, on device: FitDevice, type: DynamicTypeSize = .large) -> [String: CGRect] {
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
        // Two turns of the run loop: the grid's probe reports the tallest info block in the first
        // pass, and the cards take that height in the next.
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        controller.view.layoutIfNeeded()
        window.isHidden = true
        return sink.frames
    }

    private let sizes: [DynamicTypeSize] = [.large, .accessibility3]

    private let leonie = "sample-leonie"
    private let sofia = "sample-sofia"
    private let anna = "sample-anna"

    private func grid(_ state: SearchUiState = SearchSamples.gridRest()) -> some View {
        SearchGridView(state: state, zone: SearchSamples.zone)
    }

    private func likes(_ state: SearchUiState = SearchSamples.likesRest()) -> some View {
        LikesReceivedView(state: state, zone: SearchSamples.zone)
    }

    // MARK: - the grid

    func testTheGridIsTwoColumnsOf248WithItsGapsOnEveryPhone() {
        var failures: [String] = []
        for device in fitDevices {
            let f = layout(grid(), on: device)
            guard let list = f[FitAnchorID.searchGrid], let first = f[FitAnchorID.gridCard(leonie)],
                  let second = f[FitAnchorID.gridCard(sofia)] else {
                failures.append("\(device.described): an anchor is missing"); continue
            }
            let tag = device.described
            if abs(first.minX - 16) > 0.5 { failures.append("\(tag): first column at \(first.minX), not 16") }
            if abs(second.maxX - (device.width - 16)) > 0.5 { failures.append("\(tag): second column ends at \(second.maxX)") }
            if abs(second.minX - first.maxX - 12) > 0.5 { failures.append("\(tag): column gap \(second.minX - first.maxX)") }
            if abs(first.width - second.width) > 0.5 { failures.append("\(tag): columns of \(first.width) and \(second.width)") }
            if abs(first.height - gridCardHeight) > 0.5 { failures.append("\(tag): a card \(first.height) tall, not 248") }
            if abs(first.minY - list.minY - 4) > 0.5 { failures.append("\(tag): \(first.minY - list.minY) above the first row, not 4") }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    func testEveryGridCardIsTheSameHeightAndAtLeast248AtTheLargestType() {
        var failures: [String] = []
        for device in fitDevices {
            let f = layout(grid(), on: device, type: .accessibility3)
            let heights = SearchSamples.grid.compactMap { f[FitAnchorID.gridCard($0.profile.userId)]?.height }
            guard let tallest = heights.max(), let shortest = heights.min() else {
                failures.append("\(device.described): no card measured"); continue
            }
            if tallest - shortest > 0.5 { failures.append("\(device.described): cards of \(shortest) and \(tallest)") }
            if shortest < gridCardHeight - 0.5 { failures.append("\(device.described): a card under 248 (\(shortest))") }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    func testTheMeetLineOnACardIsOneLineFrom375Wide() {
        var failures: [String] = []
        for device in fitDevices where device.width >= 375 {
            let f = layout(grid(), on: device)
            guard let meta = f[FitAnchorID.gridMeta(leonie)] else {
                failures.append("\(device.described): no meta line"); continue
            }
            // Manrope 700 / 11.5: one line is under 17 tall; two would pass 30.
            if meta.height > 24 { failures.append("\(device.described): the meta line wraps (\(meta.height))") }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    func testTheTopBarAndTheTabBarFrameTheGridWithNothingOffTheScreen() {
        var failures: [String] = []
        for device in fitDevices {
            for type in sizes {
                let f = layout(grid(), on: device, type: type)
                guard let top = f[FitAnchorID.searchTopBar], let list = f[FitAnchorID.searchGrid],
                      let tab = f[FitAnchorID.tabBar] else {
                    failures.append("\(device.described) \(type): an anchor is missing"); continue
                }
                let tag = "\(device.described) \(type)"
                if top.maxX > device.width + 0.5 { failures.append("\(tag): the top bar runs off the screen") }
                if top.minY < device.top - 0.5 { failures.append("\(tag): the top bar is under the status bar") }
                if abs(list.minY - top.maxY) > 1 { failures.append("\(tag): the grid starts at \(list.minY), the bar ends at \(top.maxY)") }
                if abs(list.maxY - tab.minY) > 1 { failures.append("\(tag): the grid ends at \(list.maxY), the TabBar starts at \(tab.minY)") }
                if tab.minY > device.height - device.bottom + 0.5 { failures.append("\(tag): the TabBar is below the screen") }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    func testTheUndoToastFloats16FromEachSideAnd12AboveTheTabBar() {
        var state = SearchSamples.gridRest()
        state.grid.cards[3].gone = true
        state.undo = UndoToast(userId: anna, name: "Anna", from: .grid, position: 4)
        var failures: [String] = []
        for device in fitDevices {
            for type in sizes {
                let f = layout(grid(state), on: device, type: type)
                guard let toast = f[FitAnchorID.undoToast], let tab = f[FitAnchorID.tabBar] else {
                    failures.append("\(device.described) \(type): an anchor is missing"); continue
                }
                let tag = "\(device.described) \(type)"
                if abs(toast.minX - 16) > 0.5 || abs(toast.maxX - (device.width - 16)) > 0.5 {
                    failures.append("\(tag): the toast spans \(toast.minX)...\(toast.maxX)")
                }
                if abs(toast.maxY - (tab.minY - 12)) > 1 { failures.append("\(tag): the toast ends at \(toast.maxY), not 12 above \(tab.minY)") }
                if toast.height < 52 - 0.5 { failures.append("\(tag): the toast is under 52 tall") }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    // MARK: - the Likes tab

    func testTheLikesCardsFillTheWidthWithViewProfileInsideItsCard() {
        var failures: [String] = []
        for device in fitDevices {
            for type in sizes {
                let f = layout(likes(), on: device, type: type)
                guard let top = f[FitAnchorID.searchTopBar], let list = f[FitAnchorID.likesList],
                      let tab = f[FitAnchorID.tabBar], let card = f[FitAnchorID.likesCard(anna)] else {
                    failures.append("\(device.described) \(type): an anchor is missing"); continue
                }
                let tag = "\(device.described) \(type)"
                if abs(list.minY - top.maxY) > 1 || abs(list.maxY - tab.minY) > 1 { failures.append("\(tag): the list is not the region") }
                if abs(card.minX - 16) > 0.5 || abs(card.maxX - (device.width - 16)) > 0.5 {
                    failures.append("\(tag): a card spans \(card.minX)...\(card.maxX)")
                }
                if let button = f[FitAnchorID.viewProfile], button.maxX > device.width - 16 - 14 + 0.5 {
                    failures.append("\(tag): View profile ends at \(button.maxX), past its card's padding")
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    func testTheEmptyLikesActionIsAboveTheTabBarAtTheDefaultType() {
        var state = SearchSamples.likesRest()
        state.likes = PremiumList(loading: false)
        var failures: [String] = []
        for device in fitDevices {
            let f = layout(likes(state), on: device)
            guard let action = f[FitAnchorID.likesEmptyAction], let tab = f[FitAnchorID.tabBar] else {
                failures.append("\(device.described): an anchor is missing"); continue
            }
            if action.maxY > tab.minY + 0.5 { failures.append("\(device.described): Back to search is under the TabBar") }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    // MARK: - the profile opened from a list

    private func opened(_ id: String, from: PremiumSurface = .grid) -> SearchUiState {
        var s = from == .grid ? SearchSamples.gridRest() : SearchSamples.likesRest()
        s.opened = Opened(from: from, userId: id)
        return s
    }

    func testAProfileOpenedFromAListShowsItsBackPillAndItsActionsSitOnTheTabBar() {
        var failures: [String] = []
        for state in [opened(leonie), opened(anna, from: .likes)] {
            for device in fitDevices {
                for type in sizes {
                    let f = layout(SearchProfileView(state: state, zone: SearchSamples.zone), on: device, type: type)
                    guard let pill = f[FitAnchorID.backPill], let bar = f[FitAnchorID.searchActionBar],
                          let tab = f[FitAnchorID.tabBar], let like = f[FitAnchorID.searchLike],
                          let top = f[FitAnchorID.searchTopBar] else {
                        failures.append("\(device.described) \(type): an anchor is missing"); continue
                    }
                    let tag = "\(device.described) \(type) \(state.opened?.from == .likes ? "likes" : "grid")"
                    if pill.minY < device.top - 0.5 || pill.maxY > top.maxY + 0.5 { failures.append("\(tag): the back pill is out of the bar") }
                    if top.maxX > device.width + 0.5 { failures.append("\(tag): the top bar runs off the screen") }
                    if abs(bar.maxY - tab.minY) > 1 { failures.append("\(tag): the action bar is not on the TabBar") }
                    if like.minX < -0.5 || like.maxX > device.width + 0.5 { failures.append("\(tag): Confirm date is off the screen") }
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    func testTheSorrySheetsButtonIsOnTheScreenOnEveryPhone() {
        var failures: [String] = []
        for reason in CloseReason.allCases {
            var state = opened(leonie)
            state.sheet = .refused
            state.refused = reason
            for device in fitDevices {
                let f = layout(SearchProfileView(state: state, zone: SearchSamples.zone), on: device)
                guard let button = f[FitAnchorID.sorryButton] else {
                    failures.append("\(device.described) \(reason): no button"); continue
                }
                if button.maxY > device.height - device.bottom + 0.5 || button.minY < device.top {
                    failures.append("\(device.described) \(reason): the button is off the screen (\(button.minY)...\(button.maxY))")
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }

    /// The instrument fires: a grid that has not loaded publishes no cards, and a loaded one does.
    func testTheAnchorsReportWhatIsThereAndNothingElse() throws {
        let device = try XCTUnwrap(fitDevices.first { $0.width == 390 })
        var loading = SearchSamples.gridRest()
        loading.grid = PremiumList()
        let none = layout(grid(loading), on: device)
        XCTAssertNil(none[FitAnchorID.gridCard(leonie)], "no grid, no cards")
        XCTAssertNotNil(none[FitAnchorID.tabBar])
        let loaded = layout(grid(), on: device)
        XCTAssertNotNil(loaded[FitAnchorID.gridCard(leonie)])
    }
}

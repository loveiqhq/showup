//
//  GateFitTests.swift
//  ShowUp · the Search location gate (SHOWUP-188), measured on every phone
//
//  The Swift twin of `GateFitTest.kt`'s geometry. Each frame -- A, B, C on the Search tab, D over a
//  Likes profile, in B as drawn and in A, the tightest -- is hosted in a window with each device's
//  exact insets, at the default type size and at the largest accessibility size, and held to the
//  ticket's promises:
//
//    · "the CTA sits directly above the TabBar in every state": 22 above it, wholly on screen
//    · the headline below the top bar and above the CTA
//    · the TabBar at the bottom of the screen
//

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class GateFitTests: XCTestCase {

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

    private let frames: [(String, LocationState, Bool)] = [
        ("A never granted", .ask, false),
        ("B off for Show Up", .denied, false),
        ("C off on the phone", .servicesOff, false),
        ("D from Likes (B)", .denied, true),
        ("D from Likes (A)", .ask, true),
    ]

    private func gate(_ state: LocationState, fromLikes: Bool) -> some View {
        LocationGateView(state: state, tab: fromLikes ? .likes : .search, likes: 2, onTab: { _ in }, onLikes: {},
                         onBack: fromLikes ? {} : nil)
    }

    func testTheCTASits22AboveTheTabBarInEveryStateOnEveryPhone() {
        var failures: [String] = []
        for (name, state, fromLikes) in frames {
            for type in [DynamicTypeSize.large, .accessibility3] {
                for device in fitDevices {
                    let f = layout(gate(state, fromLikes: fromLikes), on: device, type: type)
                    let tag = "\(name) \(device.described) @\(type)"
                    guard let cta = f[FitAnchorID.gateCta], let tabs = f[FitAnchorID.tabBar],
                          let top = f[FitAnchorID.searchTopBar], let headline = f[FitAnchorID.gateHeadline] else {
                        failures.append("\(tag): an anchor is missing"); continue
                    }
                    if abs(tabs.minY - cta.maxY - 22) > 0.6 { failures.append("\(tag): CTA ends \(tabs.minY - cta.maxY) above the TabBar") }
                    if cta.minY < top.maxY { failures.append("\(tag): CTA under the top bar") }
                    if cta.maxY > device.height - device.bottom + 0.5 { failures.append("\(tag): CTA off the screen") }
                    if abs(tabs.maxY - device.height) > 0.6 && abs(tabs.maxY - (device.height - device.bottom)) > 0.6 {
                        failures.append("\(tag): TabBar ends at \(tabs.maxY) of \(device.height)")
                    }
                    if cta.minX < 23.5 || cta.maxX > device.width - 23.5 { failures.append("\(tag): CTA not inside the 24 gutters") }
                    // At the default type the headline is laid out between the bar and the CTA.
                    if type == .large, headline.minY < top.maxY || headline.maxY > cta.minY {
                        failures.append("\(tag): headline outside the content area")
                    }
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.prefix(40).joined(separator: " · "))
    }
}

//
//  EvidenceShots.swift
//  ShowUp · the iOS half of a ticket's "evidence of done", rendered on CI's Mac
//
//  The tickets ask for screenshots on iOS and Android at 375 x 667, 390 x 844 and 430 x 932. Android's
//  come from `EvidenceScreenshots.kt`; nobody building these screens has a Mac, so these come from
//  the macOS runner: each frame is hosted in a window with that iPhone's exact insets, drawn through
//  the window's layer tree at 3x, and written as a PNG.
//
//  ON SCREEN FIRST, THE LAYER TREE AS A FALLBACK. Offscreen there are no screen updates to be after,
//  so `drawHierarchy(afterScreenUpdates:)` draws nothing -- `ScreenFitTests` found that first, and
//  the first CI run of this file wrote twelve pure-white PNGs and passed. The layer tree draws, but
//  not as the phone composites: it ignores blend modes (the Premium tag's inset shadows, cut with
//  `destinationOut`, came out as a black capsule) and draws a capsule's border square-cornered. So
//  the window joins the host app's scene and is drawn on screen; only if that draws nothing is the
//  layer tree used, and that picture's name ends `-layers`. A flat picture fails either way.
//
//  ONLY WHEN ASKED: the folder comes from `SHOWUP_EVIDENCE_DIR` (CI passes it as
//  `TEST_RUNNER_SHOWUP_EVIDENCE_DIR`). Without it the test is skipped, so an ordinary run writes
//  nothing anywhere.
//

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class EvidenceShots: XCTestCase {

    /// The three frames the tickets name.
    private let frames: [FitDevice] = fitDevices.filter {
        ($0.width, $0.height) == (375, 667) || ($0.width, $0.height) == (390, 844) || ($0.width, $0.height) == (430, 932)
    }

    private func folder(_ ticket: String) throws -> URL {
        guard let root = ProcessInfo.processInfo.environment["SHOWUP_EVIDENCE_DIR"], !root.isEmpty else {
            throw XCTSkip("SHOWUP_EVIDENCE_DIR is not set: evidence is rendered on CI only")
        }
        let dir = URL(fileURLWithPath: root).appendingPathComponent(ticket, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// `view` on `device` at `type`, saved as `<state>-<w>x<h>.png` in `dir`; a picture of one flat
    /// colour fails.
    private func shoot(_ view: some View, _ state: String, on device: FitDevice, type: DynamicTypeSize = .large,
                       into dir: URL) throws {
        let controller = UIHostingController(rootView: AnyView(view.environment(\.dynamicTypeSize, type)))
        controller.overrideUserInterfaceStyle = .light
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        let window = scene.map { FixedInsetWindow(windowScene: $0) }
            ?? FixedInsetWindow(frame: CGRect(x: 0, y: 0, width: device.width, height: device.height))
        window.frame = CGRect(x: 0, y: 0, width: device.width, height: device.height)
        window.windowLevel = .alert + 1
        window.fixedInsets = UIEdgeInsets(top: device.top, left: 0, bottom: device.bottom, right: 0)
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.3))
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 3
        // 8-bit sRGB, as Android's evidence and the spec sheets are. The simulator's default is a
        // 16-bit Display P3 PNG, which a viewer that ignores its profile shows with paler greens --
        // the 189 switch read as a different colour from Android's though both are #00AB55.
        format.preferredRange = .standard
        let renderer = UIGraphicsImageRenderer(size: window.bounds.size, format: format)
        var drawn = false
        var image = renderer.image { _ in
            drawn = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        var suffix = ""
        if !drawn || image.cgImage.map { isOneColour($0) } ?? true {
            image = renderer.image { ctx in window.layer.render(in: ctx.cgContext) }
            suffix = "-layers"
        }
        window.isHidden = true
        let picture = try XCTUnwrap(image.cgImage, "\(state) on \(device.described): nothing rendered")
        XCTAssertFalse(isOneColour(picture), "\(state) on \(device.described): one flat colour, nothing drawn")
        let data = try XCTUnwrap(image.pngData(), "\(state) on \(device.described): nothing rendered")
        try data.write(to: dir.appendingPathComponent(
            "\(state)-\(Int(device.width))x\(Int(device.height))\(suffix).png"))
    }

    /// Whether `image`, sampled on a 24 x 24 grid, is a single colour -- what a failed draw leaves.
    private func isOneColour(_ image: CGImage) -> Bool {
        let side = 24
        var pixels = [UInt8](repeating: 0, count: side * side * 4)
        let drawn = pixels.withUnsafeMutableBytes { raw -> Bool in
            guard let ctx = CGContext(data: raw.baseAddress, width: side, height: side, bitsPerComponent: 8,
                                      bytesPerRow: side * 4, space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            ctx.interpolationQuality = .none
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: side, height: side))
            return true
        }
        guard drawn else { return true }
        let first = Array(pixels[0..<4])
        return stride(from: 0, to: pixels.count, by: 4).allSatisfy { Array(pixels[$0..<($0 + 4)]) == first }
    }

    /// SHOWUP-193: the Date screen's A, B and C at the three ticket frames, D as the sheet's full scroll,
    /// and the two states the sheet does not draw -- the match's read-only profile and a failed first read.
    /// SHOWUP-194's evidence: A to H at the three frames the acceptance criteria name -- 24 images -- and C to E
    /// again with the bottom taken by a keyboard-sized inset (the harness has no keyboard; only the evidence
    /// simulates one).
    func testCoordinateChatEvidence() throws {
        let dir = try folder("194-coordinate-chat")
        XCTAssertEqual(frames.count, 3, "the three ticket frames")
        let chats: [(String, ChatUi)] = [("B-start", ChatSamples.b), ("C-thread", ChatSamples.c),
                                         ("D-cancel-guide", ChatSamples.d), ("E-end-card", ChatSamples.e),
                                         ("F-end-sheet", ChatSamples.f)]
        for device in frames {
            try shoot(ChatTutorialScreen(name: "Leonie"), "A-tutorial", on: device, into: dir)
            for (name, ui) in chats {
                try shoot(ChatScreen(ui: ui, zone: DateSamples.zone), name, on: device, into: dir)
            }
            try shoot(DateEndedScreen(outcome: ChatSamples.g), "G-you-ended", on: device, into: dir)
            try shoot(DateEndedScreen(outcome: ChatSamples.h), "H-match-ended", on: device, into: dir)
            for (name, ui) in chats where ["C-thread", "D-cancel-guide", "E-end-card"].contains(name) {
                try shoot(ChatScreen(ui: ui, zone: DateSamples.zone).safeAreaInset(edge: .bottom, spacing: 0) {
                    Color(white: 0.82).frame(height: 291)
                }, name + "-keyboard", on: device, into: dir)
            }
        }
    }

    func testDateScreenEvidence() throws {
        let dir = try folder("193-date-screen")
        var unread = DateSamples.date
        unread.unread = DateSamples.unread
        let shots: [(String, DateUi)] = [
            ("A-announced", DateUi(date: DateSamples.date, now: DateSamples.aNow, loading: false)),
            ("B-available", DateUi(date: DateSamples.date, now: DateSamples.bNow, loading: false)),
            ("C-unread", DateUi(date: unread, now: DateSamples.bNow, loading: false)),
            ("F-failed", DateUi(now: DateSamples.aNow, loading: false, failed: .network)),
        ]
        XCTAssertEqual(frames.count, 3, "the three ticket frames")
        for device in frames {
            for (name, ui) in shots {
                try shoot(DateScreen(ui: ui, zone: DateSamples.zone), name, on: device, into: dir)
            }
            // Philipp's 5 Nov update: A once with the change tiles switched on, as Date 05 will show them.
            try shoot(DateScreen(ui: shots[0].1, zone: DateSamples.zone, changeTiles: [.changeTime, .changePlace]),
                      "A-change-tiles", on: device, into: dir)
            try shoot(SearchProfileView(state: SearchUiState(loading: false, queue: [SearchSamples.full]),
                                        zone: DateSamples.zone, readOnly: true, backLabel: DateCopy.profileBack,
                                        disabledTabs: dateLockedTabs, disabledHint: DateCopy.tabHint),
                      "E-profile", on: device, into: dir)
        }
        // D: every section in order, the footer at the foot of the scroll -- a frame as tall as the content.
        let tall = FitDevice(name: "full length", width: 390, height: 2350, top: 0, bottom: 0)
        try shoot(DateScreen(ui: DateUi(date: DateSamples.date, now: DateSamples.aNow, loading: false),
                             zone: DateSamples.zone), "D-full", on: tall, into: dir)

        // THE EXTREMES (the 4 Nov 2026 device audit), to be looked at, not only measured: the smallest
        // screen a supported iPhone shows (an SE with Display Zoom) at the largest type, its whole scroll
        // at that size, and the widest phone (an unfolded Fold 7) at the default.
        let smallest = try XCTUnwrap(edgeFitDevices.first { $0.width == 320 })
        let widest = try XCTUnwrap(edgeFitDevices.first { $0.width == 984 })
        var checkedIn = DateSamples.date
        checkedIn.checkedIn = true
        let extremes: [(String, DateUi)] = [
            ("G-A-AX5", DateUi(date: DateSamples.date, now: DateSamples.aNow, loading: false)),
            ("G-C-AX5", DateUi(date: unread, now: DateSamples.bNow, loading: false)),
            ("G-checked-in-AX5", DateUi(date: checkedIn, now: DateSamples.bNow, loading: false)),
        ]
        for (name, ui) in extremes {
            try shoot(DateScreen(ui: ui, zone: DateSamples.zone), name, on: smallest, type: .accessibility5, into: dir)
        }
        // 5450 tall, not more: at the shots' 3x a taller window passes the 16384 px a drawn image may
        // have, and the fallback renderer draws shapes wrong -- capsule chips grew straight edges.
        let smallTall = FitDevice(name: "full length, smallest", width: 320, height: 5450, top: 0, bottom: 0)
        try shoot(DateScreen(ui: DateUi(date: DateSamples.date, now: DateSamples.aNow, loading: false),
                             zone: DateSamples.zone), "H-full-AX5", on: smallTall, type: .accessibility5, into: dir)
        // AX3, the largest size every other screen is held to, on the same phone.
        let smallAX3 = FitDevice(name: "full length, smallest, AX3", width: 320, height: 4200, top: 0, bottom: 0)
        try shoot(DateScreen(ui: DateUi(date: DateSamples.date, now: DateSamples.aNow, loading: false),
                             zone: DateSamples.zone), "H-full-AX3", on: smallAX3, type: .accessibility3, into: dir)
        // The two chip cards on their own at the largest type, drawn by the ordinary renderer.
        let cards = FitDevice(name: "chip cards", width: 320, height: 1800, top: 0, bottom: 0)
        try shoot(VStack(spacing: 8) {
            IntentCard(date: DateSamples.date)
            CommonCard(date: DateSamples.date)
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(Color.liqCream), "J-chips-AX5", on: cards, type: .accessibility5, into: dir)
        try shoot(DateScreen(ui: DateUi(date: DateSamples.date, now: DateSamples.aNow, loading: false),
                             zone: DateSamples.zone), "I-widest", on: widest, into: dir)
    }

    /// SHOWUP-188: A, B and C on the Search tab, D over a Likes profile in B as the sheet draws it.
    func testLocationGateEvidence() throws {
        let dir = try folder("188-location-gate")
        let shots: [(String, LocationState, Bool)] = [
            ("A-never-granted", .ask, false),
            ("B-off-for-show-up", .denied, false),
            ("C-off-on-the-phone", .servicesOff, false),
            ("D-from-likes", .denied, true),
        ]
        XCTAssertEqual(frames.count, 3, "the three ticket frames")
        for device in frames {
            for (name, state, fromLikes) in shots {
                try shoot(LocationGateView(state: state, tab: fromLikes ? .likes : .search, likes: 2,
                                           onTab: { _ in }, onLikes: {}, onBack: fromLikes ? {} : nil),
                          name, on: device, into: dir)
            }
        }
    }

    /// SHOWUP-189: Filters v2 -- A both off, B two genders with age a dealbreaker, C both on; and A with
    /// the remote flag off at 390 x 844, Check-In 05's layout plus the gender change.
    func testFiltersV2Evidence() throws {
        let dir = try folder("189-filters-v2")
        let shots: [(String, CheckInFilters)] = [
            ("A-both-off", CheckInFilters(genders: ["woman"])),
            ("B-two-genders-age-strict", CheckInFilters(genders: ["woman", "non_binary"], minAge: 28, maxAge: 35,
                                                        ageDealbreaker: true)),
            ("C-both-strict", CheckInFilters(genders: ["woman", "man", "non_binary"], minAge: 25, maxAge: 40,
                                             distanceKm: 15, ageDealbreaker: true, distanceDealbreaker: true)),
        ]
        for device in frames {
            for (name, filters) in shots {
                try shoot(CheckInFiltersView(filters: filters, dealbreakers: true), name, on: device, into: dir)
            }
        }
        let mid = try XCTUnwrap(frames.first { $0.width == 390 })
        try shoot(CheckInFiltersView(filters: CheckInFilters(genders: ["woman"])), "A-flag-off", on: mid, into: dir)
    }

    /// SHOWUP-184: the interim no-results screen (27 Oct 2026), the kit's search-empty in normal mode,
    /// in the Freemium queue and the Premium grid.
    func testSearchNoResultsEvidence() throws {
        let dir = try folder("184-no-results")
        var queue = SearchUiState()
        queue.loading = false
        var grid = SearchSamples.gridRest()
        grid.grid = PremiumList(loading: false)
        for device in frames {
            try shoot(SearchProfileView(state: queue, zone: SearchSamples.zone), "queue", on: device, into: dir)
            try shoot(SearchGridView(state: grid, zone: SearchSamples.zone), "grid", on: device, into: dir)
        }
    }
}

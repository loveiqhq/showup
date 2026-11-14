//
//  FitAnchors.swift
//  ShowUp · a view's frame, published for the fit tests -- the twin of Android's `testTag`
//
//  The Compose fit harness reads tagged nodes' boxes to hold geometry the ticket promises -- the
//  action bar on the TabBar, the coach above the buttons and under nothing. SwiftUI has no tags, and
//  the iOS harness's pixel probes cannot tell a centred pill from the chips under it, so a view that
//  a fit test measures publishes its frame here, in window coordinates, and the test reads them from
//  the window it hosts. Nothing in the app reads the preference; an unread one costs a dictionary entry.
//
//  Frames, not anchors: a `CGRect` is `Sendable`, so the key's default can be a stored `let` -- the
//  shape `UpliftFieldFrameKey` already has -- with no static state for strict concurrency to question.
//

import SwiftUI

struct FitFrameKey: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]

    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue()) { _, new in new }
    }
}

extension View {
    /// Publishes this view's frame, in window coordinates, under `id`, for the fit tests.
    func fitAnchor(_ id: String) -> some View {
        background(GeometryReader { g in
            Color.clear.preference(key: FitFrameKey.self, value: [id: g.frame(in: .global)])
        })
    }

    /// `fitAnchor(id)` when there is an id, the view as it is when there is not.
    @ViewBuilder func fitAnchor(ifGiven id: String?) -> some View {
        if let id { fitAnchor(id) } else { self }
    }
}

/// One touch area, in window coordinates, and whether it sits in a bar pinned over a scroll. A
/// control in the scroll lying under that bar at rest is passing beneath it, not sharing its place,
/// and only the structure can tell the two apart -- a card wholly below the bar's top edge looks,
/// by position alone, like part of the bar. `modal`: in a sheet over the screen, under whose backdrop
/// nothing answers -- the sheet's panel covers the controls below it, so position cannot tell either.
struct FitTap: Equatable, Sendable {
    let frame: CGRect
    let pinned: Bool
    var modal = false
}

/// Every touch area a fit test measures. The tap floor and "no two on each other" are about the area
/// a finger hits, which for `overflowTapTarget` is not the drawn frame (the 4 Nov 2026 device audit).
/// Like `FitFrameKey`, nothing in the app reads it.
struct FitTapKey: PreferenceKey {
    static let defaultValue: [FitTap] = []

    static func reduce(value: inout [FitTap], nextValue: () -> [FitTap]) {
        value.append(contentsOf: nextValue())
    }
}

private struct FitTapPinnedKey: EnvironmentKey {
    static let defaultValue = false
}

private struct FitTapModalKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// True inside a bar pinned over a scroll, for `fitTapArea`.
    var fitTapPinned: Bool {
        get { self[FitTapPinnedKey.self] }
        set { self[FitTapPinnedKey.self] = newValue }
    }

    /// True inside a sheet over the screen, for `fitTapArea`.
    var fitTapModal: Bool {
        get { self[FitTapModalKey.self] }
        set { self[FitTapModalKey.self] = newValue }
    }
}

private struct FitTapArea: ViewModifier {
    @Environment(\.fitTapPinned) private var pinned
    @Environment(\.fitTapModal) private var modal

    func body(content: Content) -> some View {
        content.background(GeometryReader { g in
            Color.clear.preference(key: FitTapKey.self,
                                   value: [FitTap(frame: g.frame(in: .global), pinned: pinned, modal: modal)])
        })
    }
}

extension View {
    /// Publishes this view's frame as a touch area, for the fit tests' tap checks.
    func fitTapArea() -> some View {
        modifier(FitTapArea())
    }

    /// Marks this subtree as a bar pinned over a scroll, for the fit tests' tap checks.
    func fitPinnedBar() -> some View {
        environment(\.fitTapPinned, true)
    }

    /// Marks this subtree as a sheet over the screen, for the fit tests' tap checks.
    func fitModal() -> some View {
        environment(\.fitTapModal, true)
    }
}

/// The ids, one place, so the screens and the tests cannot spell one differently.
enum FitAnchorID {
    static let searchTopBar = "search-top-bar"
    static let searchStack = "search-stack"
    static let searchActionBar = "search-action-bar"
    static let searchLike = "search-like"
    static let searchCoach = "search-coach"
    static let searchMeetLine = "search-meet-line"
    static let searchMeetCount = "search-meet-count"
    static let tabBar = "tab-bar"

    // The Premium grid and the Likes tab (SHOWUP-185, 186).
    static let searchGrid = "search-grid"
    static let likesList = "likes-list"
    static let likesEmpty = "likes-empty"
    static let likesEmptyAction = "likes-empty-action"
    static let undoToast = "undo-toast"
    static let sorryButton = "sorry-button"
    static let backPill = "back-pill"
    static let viewProfile = "view-profile"
    static func gridCard(_ userId: String) -> String { "grid-card-\(userId)" }
    static func gridMeta(_ userId: String) -> String { "grid-meta-\(userId)" }
    static func likesCard(_ userId: String) -> String { "likes-card-\(userId)" }

    // The Search location gate (SHOWUP-188).
    static let gateCta = "location-gate-cta"
    static let gateHeadline = "location-gate-headline"

    // The media step's filled video card (SHOWUP-161 / SHOWUP-191 item 4).
    static let mediaRecordedLabel = "media-recorded-label"
    static let mediaRetake = "media-retake"
    static let mediaDelete = "media-delete"
    /// That whole row, inside its padding: the width the one-line form has to fit.
    static let mediaRecordedRow = "media-recorded-row"

    // The arrival check-in (SHOWUP-195): the sheets' controls and the bar's, for `ArrivalFitTests`.
    static let arrivalSheet = "arrival:sheet"
    static let arrivalCircle = "arrival:circle"
    static let arrivalNotYet = "arrival:not-yet"
    static let arrivalYes = "arrival:yes"
    static let arrivalError = "arrival:error"
    static let arrivalClose = "arrival:close"
    static let arrivalUndo = "arrival:undo"
    static let arrivalBar = "arrival:bar"
    static let arrivalCheckIn = "arrival:check-in"
    static let dateGateCard = "date-gate:card"
    static let dateGatePill = "date-gate:pill"
    /// The bar's text column: the width its longest word has to fit (R12).
    static let arrivalBarText = "arrival:bar-text"
    /// The gate headline's widest word as drawn, published by `FitWords`.
    static let dateGateHeadlineWord = "date-gate:headline-word"

    // Search 01's interim no-results screen (27 Oct 2026).
    static let searchEmptyCta = "search-empty-cta"
    static let searchEmptyHeadline = "search-empty-headline"
}

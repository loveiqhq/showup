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
}

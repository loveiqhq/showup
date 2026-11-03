// DateAnalytics.swift
// ShowUp · the Date screen's events (SHOWUP-193; family K, registry 1.4.32 / taxonomy 1.4.25)
//
// Builders only: each returns the name and the payload, and the model reports them. Names and values
// are the registry's, never typed at a call site. Android's `DateAnalytics.kt`.
//
// NOT FROM HERE: `match_mate_*` (Date 10), `coordinate_viewed` (Date 02), `arrival_*` (Date 03),
// `date_status_*` (Date 06), `date_cancelled` (Date 07). Their entry points are on this screen; their
// events belong to their tickets. `coordinate_unlocked` is the server's.

import Foundation

/// `enums.json` §38 `date_quick_action`, minus `ride`: Order Ride is hidden until a ride-app API exists,
/// so `ride` is reserved and never emitted.
enum QuickAction: String, CaseIterable, Sendable {
    case directions = "directions"
    case share = "share"
    case calendar = "calendar"
    case copyAddress = "copy_address"

    var wire: String { rawValue }
}

enum DateAnalytics {
    static let dateScreenViewedName = "date_screen_viewed"
    static let quickActionTappedName = "date_quick_action_tapped"

    /// Once per visit, beside `screen_viewed` -- never on the in-place unlock. `state` is the Coordinate
    /// card's (`locked` / `available`); C is `available` with `has_unread`.
    static func dateScreenViewed(_ date: ActiveDate, state: DateState, now: Date) -> (String, [String: any Sendable]) {
        (dateScreenViewedName, [
            "date_id": date.id,
            "state": state == .locked ? "locked" : "available",
            "has_unread": state == .unread,
            "checked_in": date.checkedIn,
            "minutes_before_start": minutesBeforeStart(meetAt: date.meetAt, now: now),
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// On the tap, before the OS hand-off. Whether the OS sheet is completed is not ours.
    static func quickActionTapped(_ date: ActiveDate, action: QuickAction, now: Date) -> (String, [String: any Sendable]) {
        (quickActionTappedName, [
            "date_id": date.id,
            "action": action.wire,
            "minutes_before_start": minutesBeforeStart(meetAt: date.meetAt, now: now),
        ].merging(Stamp.of(0)) { a, _ in a })
    }
}

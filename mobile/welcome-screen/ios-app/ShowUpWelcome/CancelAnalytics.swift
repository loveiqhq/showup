// CancelAnalytics.swift
// ShowUp · Date 07's client events (SHOWUP-198; family K, registry 1.4.37). Android's `CancelAnalytics.kt`.
//
// NOT FROM HERE: `date_cancelled` (the server's, on success only), the retired cancel_link_tapped /
// partner_cancellation_received / rematch_started, `date_ended`, `date_ended_in_chat`, and anything carrying the
// match's id, a place, a coordinate, free text -- or the removed `initiator` and `reason`.

import Foundation

/// Registry section 46 `exit`: how the guard sheet closed without a cancel. The raw value is the wire value.
enum CancelExit: String, CaseIterable, Sendable { case keep, dismissed, message }

enum CancelAnalytics {
    static let guardShownName = "cancel_guard_shown"
    static let abandonedName = "cancel_abandoned_at_guard"

    /// The sheet opens, every time. There is no separate link-tap event.
    static func guardShown(dateId: String, chatOpen: Bool, penaltyPoints: Int, minutesBeforeStart: Int) -> ChatAnalytics.Event {
        (guardShownName, [
            "date_id": dateId,
            "chat_open": chatOpen,
            "penalty_points": penaltyPoints,
            "minutes_before_start": minutesBeforeStart,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// The sheet closes without a cancel. `changeRequested`: a picker sent a request from this sheet this session.
    static func abandoned(dateId: String, exit: CancelExit, changeRequested: Bool) -> ChatAnalytics.Event {
        (abandonedName, [
            "date_id": dateId,
            "exit": exit.rawValue,
            "change_requested": changeRequested,
        ].merging(Stamp.of(0)) { a, _ in a })
    }
}

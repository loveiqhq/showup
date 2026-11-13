// ArrivalAnalytics.swift
// ShowUp · the arrival check-in's client events (SHOWUP-195; family M, registry 1.4.34). Android's
// `ArrivalAnalytics.kt`.
//
// NOT FROM HERE: `arrival_confirmed` and `arrival_undone` (the server's), `screen_viewed` for B, C or D, and
// anything carrying a coordinate, an accuracy, a distance or a place.

import Foundation

enum ArrivalAnalytics {
    static let arrivalConfirmShown = "arrival_confirm_shown"
    static let arrivalConfirmSubmitted = "arrival_confirm_submitted"

    /// B is shown (Idle to Confirm only).
    static func confirmShown(dateId: String, entry: ArrivalEntry, minutesVsStart: Int) -> ChatAnalytics.Event {
        (arrivalConfirmShown, payload(dateId, entry, minutesVsStart))
    }

    /// The primary button tapped: `Yes, I've arrived` or `Try again`.
    static func confirmSubmitted(dateId: String, entry: ArrivalEntry, minutesVsStart: Int) -> ChatAnalytics.Event {
        (arrivalConfirmSubmitted, payload(dateId, entry, minutesVsStart))
    }

    private static func payload(_ dateId: String, _ entry: ArrivalEntry, _ minutes: Int) -> [String: any Sendable] {
        let own: [String: any Sendable] = [
            "date_id": dateId,
            "entry": entry.wire,
            "minutes_vs_start": minutes,
        ]
        return own.merging(Stamp.of(0)) { a, _ in a }
    }
}

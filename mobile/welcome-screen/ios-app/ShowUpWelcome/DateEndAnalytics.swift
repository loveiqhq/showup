// DateEndAnalytics.swift
// ShowUp · Date 06's client events (SHOWUP-197; family N, registry 1.4.39). Android's `DateEndAnalytics.kt`.
//
// NOT FROM HERE: `date_ended` and `problem_stay_flagged` (the server's), `screen_viewed` for B to J (they are sheets
// on `date_screen`), the retired wrapup_prompted / wrapup_confirmed / grace_period_* / no_penalty_exit_granted, and
// anything carrying a coordinate, a distance, a place or free text.

import Foundation

enum DateEndAnalytics {
    static let wrapupOpenedName = "wrapup_opened"
    static let wrapupOutcomeSelectedName = "wrapup_outcome_selected"
    static let problemReportOpenedName = "problem_report_opened"
    static let problemCategorySelectedName = "problem_category_selected"

    /// B opens. `entry` is `end_date_bar` or `checkin_bar_link`.
    static func wrapupOpened(dateId: String, entry: EndEntry, checkedIn: Bool, minutesVsStart: Int) -> ChatAnalytics.Event {
        (wrapupOpenedName, [
            "date_id": dateId,
            "entry": entry.rawValue,
            "checked_in": checkedIn,
            "minutes_vs_start": minutesVsStart,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// A B option tapped, before any gate (registry section 43 `outcome`: met or partner_no_show).
    static func wrapupOutcomeSelected(dateId: String, option: EndOption, checkedIn: Bool) -> ChatAnalytics.Event {
        (wrapupOutcomeSelectedName, [
            "date_id": dateId,
            "outcome": option == .met ? "met" : "partner_no_show",
            "checked_in": checkedIn,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// H opens.
    static func problemReportOpened(dateId: String, checkedIn: Bool, minutesVsStart: Int) -> ChatAnalytics.Event {
        (problemReportOpenedName, [
            "date_id": dateId,
            "checked_in": checkedIn,
            "minutes_vs_start": minutesVsStart,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// An H option tapped, before any gate (section 44 `category`: partner_no_show or uncomfortable).
    static func problemCategorySelected(dateId: String, option: EndOption, checkedIn: Bool) -> ChatAnalytics.Event {
        (problemCategorySelectedName, [
            "date_id": dateId,
            "category": option == .uncomfortable ? "uncomfortable" : "partner_no_show",
            "checked_in": checkedIn,
        ].merging(Stamp.of(0)) { a, _ in a })
    }
}

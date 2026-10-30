//
//  CheckInAnalytics.swift
//  ShowUp · the 48hrs Check-In's events, family H (SHOWUP-178 to SHOWUP-183)
//
//  The Swift twin of `checkin/CheckInAnalytics.kt`. Names and payloads exactly as
//  `design_handoff_showup/tracking/events.json` (taxonomy 1.4.16, registry 1.4.25) defines them;
//  `CheckInModelTests` asserts every row of the six tickets' tables, their order, and the events
//  that must NOT fire. Screen views use the app-wide `ProfileAnalytics.screenViewed`, so
//  `screen_viewed` is built in one place.
//
//  NOTHING HERE IS ATTRIBUTE DATA -- `sensitivity_class` 0 on every row -- except
//  `motivation_selected`, Class 1, the one event allowed to carry the motivation. The uplift text
//  and the gender filter never appear in any payload.
//
//  `check_in_created` is the commit event and is SERVER-side: the client never fires it.
//

import Foundation

/// The rule `filtersChanged(_:_:)`, under a name the event of the same name below cannot shadow:
/// inside `CheckInAnalytics` an unqualified `filtersChanged` finds the member, not the rule.
private func filtersChangedValue(_ entry: CheckInFilters, _ now: CheckInFilters) -> String {
    filtersChanged(entry, now)
}

enum CheckInAnalytics {
    static let checkInStarted = "check_in_started"
    static let checkInClosed = "check_in_closed"
    static let checkInGateTapped = "check_in_gate_tapped"
    static let checkInStepAnswered = "check_in_step_answered"
    static let availabilitySlotsSet = "availability_slots_set"
    static let checkInNoHoursTodayResolved = "check_in_no_hours_today_resolved"
    static let motivationSelectedName = "motivation_selected"
    static let vibeSelectedName = "vibe_selected"
    static let checkInFiltersChanged = "check_in_filters_changed"
    static let checkInCommitFailed = "check_in_commit_failed"

    /// The intro's CTA, and only that -- a gate reopen past the intro fires the gate event instead.
    static func started(windowId: String) -> (String, [String: any Sendable]) {
        (checkInStarted, ["window_id": windowId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// Every close, with the screen it was tapped on. No `has_draft` (removed, 13 Oct 2026).
    static func closed(_ step: CheckInStep, windowId: String) -> (String, [String: any Sendable]) {
        (checkInClosed, ["step_id": step.stepId, "window_id": windowId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// §27: what reopened the Check-In from the gate.
    enum GateTarget: String, CaseIterable, Sendable {
        case cta
        case searchTab = "search_tab"
        case instantTab = "instant_tab"
        var wire: String { rawValue }
    }

    /// A reopen from the gate. `intro_passed` is the server flag at tap time: it decides the
    /// destination.
    static func gateTapped(_ target: GateTarget, introPassed: Bool) -> (String, [String: any Sendable]) {
        (checkInGateTapped, [
            "target": target.wire, "intro_passed": introPassed,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// A step that moved on. `answer_value` never carries an answer: `hours` / `none` on
    /// availability, `prefilled` / `picked` on Motivation, Vibe and Filters, `written` / `empty` on
    /// Uplift.
    static func stepAnswered(_ step: CheckInStep, answerValue: String,
                             timeToAnswerMs: Int) -> (String, [String: any Sendable]) {
        (checkInStepAnswered, [
            "step_id": step.stepId, "answer_value": answerValue, "time_to_answer_ms": timeToAnswerMs,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// An accepted Continue on Today or Tomorrow -- 0 / 0 for a confirmed empty Today.
    static func slotsSet(_ day: CheckInStep, preferredHours: Int, availableHours: Int,
                         windowId: String) -> (String, [String: any Sendable]) {
        (availabilitySlotsSet, [
            "day": day.stepId, "preferred_hours": preferredHours, "available_hours": availableHours,
            "window_id": windowId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// §29: how the "No hours today?" confirm was answered.
    enum NoHoursAction: String, CaseIterable, Sendable {
        case continueTomorrow = "continue_tomorrow"
        case pickToday = "pick_today"
        case dismissed
        var wire: String { rawValue }
    }

    static func noHoursResolved(_ action: NoHoursAction, windowId: String) -> (String, [String: any Sendable]) {
        (checkInNoHoursTodayResolved, [
            "action": action.wire, "window_id": windowId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// Class 1 -- the ONE event the motivation may travel on, once per accepted Continue.
    static func motivationSelected(_ motivation: MotivationOption,
                                   windowId: String) -> (String, [String: any Sendable]) {
        (motivationSelectedName, [
            "motivation_id": motivation.id, "window_id": windowId,
        ].merging(Stamp.of(1)) { a, _ in a })
    }

    /// The whole set once per accepted Continue: list order, comma-joined, never one per tap.
    static func vibeSelected(_ vibes: Set<String>, windowId: String) -> (String, [String: any Sendable]) {
        (vibeSelectedName, [
            "vibe_ids": vibeIds(vibes), "vibe_count": vibes.count, "window_id": windowId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// Once per accepted Continue, even when nothing changed. Never the genders (Class 2) -- only
    /// WHETHER they changed, in `filters_changed`. `max_age` is JSON null for 65+, never 65.
    static func filtersChanged(entry: CheckInFilters, now: CheckInFilters,
                               windowId: String, openedFrom: String? = nil) -> (String, [String: any Sendable]) {
        let maxAge: any Sendable
        if let cap = now.maxAgeOrNil { maxAge = cap } else { maxAge = JSONNull() }
        var properties: [String: any Sendable] = [
            "filters_changed": filtersChangedValue(entry, now),
            "min_age": now.minAge,
            "max_age": maxAge,
            "distance_km": now.distanceKm,
            "window_id": windowId,
        ].merging(Stamp.of(0)) { a, _ in a }
        if let openedFrom { properties["opened_from"] = openedFrom }
        return (checkInFiltersChanged, properties)
    }

    /// §32: why a commit did not save.
    enum CommitFailure: String, CaseIterable, Sendable {
        case network, server
        case contactInfo = "contact_info"
        var wire: String { rawValue }
    }

    /// Once per request that did not save; `attempt` is 1 for the first tap on this screen view.
    static func commitFailed(_ reason: CommitFailure, attempt: Int,
                             windowId: String) -> (String, [String: any Sendable]) {
        (checkInCommitFailed, [
            "reason": reason.wire, "attempt": attempt, "window_id": windowId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }
}

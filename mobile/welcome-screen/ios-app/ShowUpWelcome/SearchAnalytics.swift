//
//  SearchAnalytics.swift
//  ShowUp · Date search 01's events, family I (SHOWUP-184)
//
//  The Swift twin of `search/SearchAnalytics.kt`. Names and payloads exactly as
//  `design_handoff_showup/tracking/events.json` (taxonomy 1.4.20, registry 1.4.29) defines them;
//  `SearchModelTests` asserts the ticket's table, its order, and the events that must NOT fire. The
//  screen view uses the app-wide `ProfileAnalytics.screenViewed`.
//
//  `sensitivity_class` 0 on every row. THE MESSAGE TEXT IS IN NO PAYLOAD: `like_message_attached`
//  carries `char_count` and nothing else. `like_sent`, `match_created` and `like_withdrawn` are
//  SERVER events -- the client fires none of them.
//

import Foundation

enum SearchAnalytics {
    static let searchViewedName = "search_viewed"
    static let profileCardViewedName = "profile_card_viewed"
    static let profilePhotoOpenedName = "profile_photo_opened"
    static let voicePromptPlayedName = "voice_prompt_played"
    static let videoPromptPlayedName = "video_prompt_played"
    static let likeTappedName = "like_tapped"
    static let likeConfirmShownName = "like_confirm_shown"
    static let likeConfirmAbandonedName = "like_confirm_abandoned"
    static let likeMessageAttachedName = "like_message_attached"
    static let passSentName = "pass_sent"
    static let searchCoachShownName = "search_coach_shown"
    static let searchCoachDismissedName = "search_coach_dismissed"

    /// §35 `like_surface`: always `profile` on this screen (`grid_card` retired 21 Oct 2026).
    static let surfaceProfile = "profile"

    /// §34 `search_pass_type`. One event, two values: never `not_now_tapped`.
    enum PassType: String, Sendable {
        case notNow = "not_now"
        case notInterested = "not_interested"
        var wire: String { rawValue }
    }

    /// §33 `like_abandon_stage`. `Skip message` is NOT an abandon: it sends.
    enum AbandonStage: String, Sendable {
        case confirm, message
        var wire: String { rawValue }
    }

    /// §23 `dismiss_method`, as the coach uses it. `swipe` is never sent: the coach has no drag, and
    /// iOS has no system back -- its value exists for Android.
    enum CoachDismiss: String, Sendable {
        case close, backdrop
        case systemBack = "system_back"
        var wire: String { rawValue }
    }

    /// The media a play was on.
    enum Prompt: Sendable { case voice, video }

    /// The queue loaded: once per Search visit. `filters_active` names the §10 filters set away from
    /// Check-In 05's defaults, never their values (`meet_gender` is Class 2).
    static func searchViewed(resultsCount: Int, filtersActive: [String]) -> (String, [String: any Sendable]) {
        (searchViewedName, [
            "layout": "one_up", "results_count": resultsCount, "filters_active": filtersActive,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// A profile LEFT the screen -- an action, a tab change or backgrounding -- so its dwell is known.
    /// `position` is 1-based in this visit's queue.
    static func profileCardViewed(position: Int, dwellMs: Int, photosSwiped: Int, sectionsScrolled: Int,
                                  sharedVibeCount: Int, sharedInterestCount: Int) -> (String, [String: any Sendable]) {
        (profileCardViewedName, [
            "position": position, "dwell_ms": dwellMs, "photos_swiped": photosSwiped,
            "sections_scrolled": sectionsScrolled, "shared_vibe_count": sharedVibeCount,
            "shared_interest_count": sharedInterestCount,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// A photo tapped open in the viewer. 1 to 6, in profile order.
    static func photoOpened(photoIndex: Int) -> (String, [String: any Sendable]) {
        (profilePhotoOpenedName, ["photo_index": photoIndex].merging(Stamp.of(0)) { a, _ in a })
    }

    /// One per play, when it STOPS -- end, pause, scrolled out, sheet, profile changed.
    static func promptPlayed(_ prompt: Prompt, position: Int, playedPct: Double) -> (String, [String: any Sendable]) {
        (prompt == .voice ? voicePromptPlayedName : videoPromptPlayedName, [
            "position": position, "played_pct": playedPct,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    static func likeTapped(position: Int) -> (String, [String: any Sendable]) {
        (likeTappedName, ["position": position, "surface": surfaceProfile].merging(Stamp.of(0)) { a, _ in a })
    }

    /// Be sure shown. `is_mutual` is false here: a confirm of a received like is Search 02/03's.
    static func likeConfirmShown(targetAttendedCount: Int) -> (String, [String: any Sendable]) {
        (likeConfirmShownName, [
            "target_attended_count": targetAttendedCount, "is_mutual": false,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    static func likeConfirmAbandoned(_ stage: AbandonStage, targetAttendedCount: Int) -> (String, [String: any Sendable]) {
        (likeConfirmAbandonedName, [
            "target_attended_count": targetAttendedCount, "stage": stage.wire, "is_mutual": false,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// `Send message` with a non-empty trimmed text. The text itself is never sent, in any event.
    static func likeMessageAttached(charCount: Int) -> (String, [String: any Sendable]) {
        (likeMessageAttachedName, ["char_count": charCount].merging(Stamp.of(0)) { a, _ in a })
    }

    /// On the tap: there is no undo on this screen.
    static func passSent(position: Int, passType: PassType) -> (String, [String: any Sendable]) {
        (passSentName, [
            "position": position, "pass_type": passType.wire, "surface": surfaceProfile,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    static func coachShown() -> (String, [String: any Sendable]) { (searchCoachShownName, Stamp.of(0)) }

    /// Once per close. A scrim tap always sends `dont_show_again: false`.
    static func coachDismissed(_ method: CoachDismiss, dontShowAgain: Bool, visibleMs: Int) -> (String, [String: any Sendable]) {
        (searchCoachDismissedName, [
            "dismiss_method": method.wire, "dont_show_again": dontShowAgain, "visible_ms": visibleMs,
        ].merging(Stamp.of(0)) { a, _ in a })
    }
}

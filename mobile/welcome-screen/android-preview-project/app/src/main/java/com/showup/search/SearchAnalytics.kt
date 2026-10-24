/*
 * SearchAnalytics.kt
 * ShowUp · Date search 01's events, family I (SHOWUP-184)
 *
 * Names and payloads exactly as `design_handoff_showup/tracking/events.json` (taxonomy 1.4.20,
 * registry 1.4.29) defines them; `SearchViewModelTest` asserts every row of the ticket's table, its
 * emit order, and the events that must NOT fire. The screen view uses the app-wide
 * [ProfileAnalytics.screenViewed], so `screen_viewed` is built in one place.
 *
 * `sensitivity_class` 0 on every row. THE MESSAGE TEXT IS IN NO PAYLOAD: `like_message_attached`
 * carries `char_count` and nothing else. `like_sent`, `match_created` and `like_withdrawn` are
 * SERVER events -- the client fires none of them.
 */
package com.showup.search

import com.showup.profile.Stamp

object SearchAnalytics {
    const val SEARCH_VIEWED = "search_viewed"
    const val PROFILE_CARD_VIEWED = "profile_card_viewed"
    const val PROFILE_PHOTO_OPENED = "profile_photo_opened"
    const val VOICE_PROMPT_PLAYED = "voice_prompt_played"
    const val VIDEO_PROMPT_PLAYED = "video_prompt_played"
    const val LIKE_TAPPED = "like_tapped"
    const val LIKE_CONFIRM_SHOWN = "like_confirm_shown"
    const val LIKE_CONFIRM_ABANDONED = "like_confirm_abandoned"
    const val LIKE_MESSAGE_ATTACHED = "like_message_attached"
    const val PASS_SENT = "pass_sent"
    const val SEARCH_COACH_SHOWN = "search_coach_shown"
    const val SEARCH_COACH_DISMISSED = "search_coach_dismissed"

    /** §35 `like_surface`: always `profile` on this screen (`grid_card` retired 21 Oct 2026). */
    const val SURFACE_PROFILE = "profile"

    /** §34 `search_pass_type`. One event, two values: never `not_now_tapped`. */
    enum class PassType(val wire: String) { NotNow("not_now"), NotInterested("not_interested") }

    /** §33 `like_abandon_stage`. `Skip message` is NOT an abandon: it sends. */
    enum class AbandonStage(val wire: String) { Confirm("confirm"), Message("message") }

    /** §23 `dismiss_method`, as the coach uses it. `swipe` is never sent: the coach has no drag. */
    enum class CoachDismiss(val wire: String) { Close("close"), Backdrop("backdrop"), SystemBack("system_back") }

    /** The media a play was on. */
    enum class Prompt { Voice, Video }

    /**
     * The queue loaded: once per Search visit. `filters_active` names the §10 filters narrowed from
     * Check-In 05's defaults, never their values (`meet_gender` is Class 2).
     */
    fun searchViewed(resultsCount: Int, filtersActive: List<String>) = SEARCH_VIEWED to buildMap<String, Any> {
        put("layout", "one_up")
        put("results_count", resultsCount)
        put("filters_active", filtersActive)
        putAll(Stamp.of(0))
    }

    /**
     * A profile LEFT the screen -- an action, a tab change or backgrounding -- so its dwell is known.
     * `position` is 1-based in this visit's queue. The counts are the overlap's; nothing else is.
     */
    fun profileCardViewed(
        position: Int,
        dwellMs: Long,
        photosSwiped: Int,
        sectionsScrolled: Int,
        sharedVibeCount: Int,
        sharedInterestCount: Int,
    ) = PROFILE_CARD_VIEWED to buildMap<String, Any> {
        put("position", position)
        put("dwell_ms", dwellMs)
        put("photos_swiped", photosSwiped)
        put("sections_scrolled", sectionsScrolled)
        put("shared_vibe_count", sharedVibeCount)
        put("shared_interest_count", sharedInterestCount)
        putAll(Stamp.of(0))
    }

    /** A photo tapped open in the viewer. 1 to 6, in profile order. */
    fun photoOpened(photoIndex: Int) = PROFILE_PHOTO_OPENED to buildMap<String, Any> {
        put("photo_index", photoIndex)
        putAll(Stamp.of(0))
    }

    /** One per play, when it STOPS -- end, pause, scrolled out, sheet, profile changed. */
    fun promptPlayed(prompt: Prompt, position: Int, playedPct: Double) =
        (if (prompt == Prompt.Voice) VOICE_PROMPT_PLAYED else VIDEO_PROMPT_PLAYED) to buildMap<String, Any> {
            put("position", position)
            put("played_pct", playedPct)
            putAll(Stamp.of(0))
        }

    fun likeTapped(position: Int) = LIKE_TAPPED to buildMap<String, Any> {
        put("position", position)
        put("surface", SURFACE_PROFILE)
        putAll(Stamp.of(0))
    }

    /** Be sure shown. `is_mutual` is false here: a confirm of a received like is Search 02/03's. */
    fun likeConfirmShown(targetAttendedCount: Int) = LIKE_CONFIRM_SHOWN to buildMap<String, Any> {
        put("target_attended_count", targetAttendedCount)
        put("is_mutual", false)
        putAll(Stamp.of(0))
    }

    fun likeConfirmAbandoned(stage: AbandonStage, targetAttendedCount: Int) = LIKE_CONFIRM_ABANDONED to buildMap<String, Any> {
        put("target_attended_count", targetAttendedCount)
        put("stage", stage.wire)
        put("is_mutual", false)
        putAll(Stamp.of(0))
    }

    /** `Send message` with a non-empty trimmed text. The text itself is never sent, in any event. */
    fun likeMessageAttached(charCount: Int) = LIKE_MESSAGE_ATTACHED to buildMap<String, Any> {
        put("char_count", charCount)
        putAll(Stamp.of(0))
    }

    /** On the tap: there is no undo on this screen. */
    fun passSent(position: Int, passType: PassType) = PASS_SENT to buildMap<String, Any> {
        put("position", position)
        put("pass_type", passType.wire)
        put("surface", SURFACE_PROFILE)
        putAll(Stamp.of(0))
    }

    fun coachShown() = SEARCH_COACH_SHOWN to buildMap<String, Any> { putAll(Stamp.of(0)) }

    /** Once per close. A scrim tap or Android back always sends `dont_show_again: false`. */
    fun coachDismissed(method: CoachDismiss, dontShowAgain: Boolean, visibleMs: Long) =
        SEARCH_COACH_DISMISSED to buildMap<String, Any> {
            put("dismiss_method", method.wire)
            put("dont_show_again", dontShowAgain)
            put("visible_ms", visibleMs)
            putAll(Stamp.of(0))
        }
}

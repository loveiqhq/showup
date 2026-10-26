/*
 * SearchAnalytics.kt
 * ShowUp · Date search's events, family I (SHOWUP-184; the grid 185 and the Likes tab 186)
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
    const val PASS_UNDONE = "pass_undone"
    const val LIKE_CONFIRM_FAILED = "like_confirm_failed"
    const val LIKES_RECEIVED_VIEWED = "likes_received_viewed"
    const val SEARCH_COACH_SHOWN = "search_coach_shown"
    const val SEARCH_COACH_DISMISSED = "search_coach_dismissed"

    /** §35 `like_surface`: `profile` for every like (`grid_card` retired 21 Oct 2026). */
    const val SURFACE_PROFILE = "profile"

    /**
     * §35 `like_surface` as `pass_sent` and `pass_undone` use it: `profile`, or `likes_received` for
     * a decline sent from the Likes list's toast ("because the decline is sent from the list's
     * toast", Search 03).
     */
    enum class Surface(val wire: String) { Profile("profile"), LikesReceived("likes_received") }

    /** §01's `search_viewed` `layout`: the Freemium queue or the Premium grid. */
    enum class Layout(val wire: String) { OneUp("one_up"), Grid("grid") }

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
    fun searchViewed(resultsCount: Int, filtersActive: List<String>, layout: Layout = Layout.OneUp) = SEARCH_VIEWED to buildMap<String, Any> {
        put("layout", layout.wire)
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

    /** Be sure shown. `is_mutual` when confirming a like the viewer received (Search 02, 03). */
    fun likeConfirmShown(targetAttendedCount: Int, isMutual: Boolean = false) = LIKE_CONFIRM_SHOWN to buildMap<String, Any> {
        put("target_attended_count", targetAttendedCount)
        put("is_mutual", isMutual)
        putAll(Stamp.of(0))
    }

    /** A mutual flow has no message step, so its only stage is `confirm`. */
    fun likeConfirmAbandoned(stage: AbandonStage, targetAttendedCount: Int, isMutual: Boolean = false) =
        LIKE_CONFIRM_ABANDONED to buildMap<String, Any> {
            put("target_attended_count", targetAttendedCount)
            put("stage", stage.wire)
            put("is_mutual", isMutual)
            putAll(Stamp.of(0))
        }

    /** `Send message` with a non-empty trimmed text. The text itself is never sent, in any event. */
    fun likeMessageAttached(charCount: Int) = LIKE_MESSAGE_ATTACHED to buildMap<String, Any> {
        put("char_count", charCount)
        putAll(Stamp.of(0))
    }

    /**
     * On the tap -- except a declined received like, which is sent when its 6 s Undo window ends
     * (Search 02, 03). `position` is 1-based in the queue, the grid or the list for this visit.
     */
    fun passSent(position: Int, passType: PassType, surface: Surface = Surface.Profile) = PASS_SENT to buildMap<String, Any> {
        put("position", position)
        put("pass_type", passType.wire)
        put("surface", surface.wire)
        putAll(Stamp.of(0))
    }

    /** `Undo` on the decline toast: nothing was sent, and no `pass_sent` follows. */
    fun passUndone(position: Int, surface: Surface) = PASS_UNDONE to buildMap<String, Any> {
        put("position", position)
        put("surface", surface.wire)
        putAll(Stamp.of(0))
    }

    /** A confirm the server refused, once, when the sorry sheet opens. Always `surface: profile`. */
    fun likeConfirmFailed(reason: CloseReason) = LIKE_CONFIRM_FAILED to buildMap<String, Any> {
        put("reason", reason.wire)
        put("surface", SURFACE_PROFILE)
        putAll(Stamp.of(0))
    }

    /** The Likes list loaded, once per visit: `0 / 0` on the empty state. */
    fun likesReceivedViewed(openCount: Int, closedCount: Int) = LIKES_RECEIVED_VIEWED to buildMap<String, Any> {
        put("open_count", openCount)
        put("closed_count", closedCount)
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

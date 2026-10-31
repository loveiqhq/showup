/*
 * CheckInAnalytics.kt
 * ShowUp · the 48hrs Check-In's events, family H (SHOWUP-178 to SHOWUP-183)
 *
 * Names and payloads exactly as `design_handoff_showup/tracking/events.json` (taxonomy 1.4.16,
 * registry 1.4.25) defines them; `CheckInViewModelTest` asserts every row of the six tickets'
 * tables, their order, and the events that must NOT fire. Screen views use the app-wide
 * [ProfileAnalytics.screenViewed], so `screen_viewed` is built in one place.
 *
 * NOTHING HERE IS ATTRIBUTE DATA -- `sensitivity_class` 0 on every row -- except
 * `motivation_selected`, Class 1, the one event allowed to carry the motivation. The uplift text
 * and the gender filter never appear in any payload.
 *
 * `check_in_created` is the commit event and is SERVER-side: the client never fires it.
 */
package com.showup.checkin

import com.showup.analytics.JsonNull
import com.showup.profile.Stamp

object CheckInAnalytics {
    const val CHECK_IN_STARTED = "check_in_started"
    const val CHECK_IN_CLOSED = "check_in_closed"
    const val CHECK_IN_GATE_TAPPED = "check_in_gate_tapped"
    const val CHECK_IN_STEP_ANSWERED = "check_in_step_answered"
    const val AVAILABILITY_SLOTS_SET = "availability_slots_set"
    const val CHECK_IN_NO_HOURS_TODAY_RESOLVED = "check_in_no_hours_today_resolved"
    const val MOTIVATION_SELECTED = "motivation_selected"
    const val VIBE_SELECTED = "vibe_selected"
    const val CHECK_IN_FILTERS_CHANGED = "check_in_filters_changed"
    const val CHECK_IN_COMMIT_FAILED = "check_in_commit_failed"

    /** The intro's CTA, and only that -- a gate reopen past the intro fires the gate event instead. */
    fun started(windowId: String) = CHECK_IN_STARTED to buildMap<String, Any> {
        put("window_id", windowId)
        putAll(Stamp.of(0))
    }

    /** Every ✕, with the screen it was tapped on. No `has_draft` (removed, 13 Oct 2026). */
    fun closed(step: CheckInStep, windowId: String) = CHECK_IN_CLOSED to buildMap<String, Any> {
        put("step_id", step.stepId)
        put("window_id", windowId)
        putAll(Stamp.of(0))
    }

    /** §27: what reopened the Check-In from the gate. */
    enum class GateTarget(val wire: String) { Cta("cta"), SearchTab("search_tab"), InstantTab("instant_tab") }

    /** A reopen from the gate. `intro_passed` is the server flag at tap time: it decides the destination. */
    fun gateTapped(target: GateTarget, introPassed: Boolean) = CHECK_IN_GATE_TAPPED to buildMap<String, Any> {
        put("target", target.wire)
        put("intro_passed", introPassed)
        putAll(Stamp.of(0))
    }

    /**
     * A step that moved on. `answer_value` never carries an answer: `hours` / `none` on availability,
     * `prefilled` / `picked` on Motivation, Vibe and Filters, `written` / `empty` on Uplift.
     */
    fun stepAnswered(step: CheckInStep, answerValue: String, timeToAnswerMs: Long) =
        CHECK_IN_STEP_ANSWERED to buildMap<String, Any> {
            put("step_id", step.stepId)
            put("answer_value", answerValue)
            put("time_to_answer_ms", timeToAnswerMs)
            putAll(Stamp.of(0))
        }

    /** An accepted Continue on Today or Tomorrow -- 0 / 0 for a confirmed empty Today. */
    fun slotsSet(day: CheckInStep, preferredHours: Int, availableHours: Int, windowId: String) =
        AVAILABILITY_SLOTS_SET to buildMap<String, Any> {
            put("day", day.stepId)
            put("preferred_hours", preferredHours)
            put("available_hours", availableHours)
            put("window_id", windowId)
            putAll(Stamp.of(0))
        }

    /** §29: how the "No hours today?" confirm was answered. */
    enum class NoHoursAction(val wire: String) {
        ContinueTomorrow("continue_tomorrow"),
        PickToday("pick_today"),
        Dismissed("dismissed"),
    }

    fun noHoursResolved(action: NoHoursAction, windowId: String) =
        CHECK_IN_NO_HOURS_TODAY_RESOLVED to buildMap<String, Any> {
            put("action", action.wire)
            put("window_id", windowId)
            putAll(Stamp.of(0))
        }

    /** Class 1 -- the ONE event the motivation may travel on, once per accepted Continue. */
    fun motivationSelected(motivation: MotivationOption, windowId: String) =
        MOTIVATION_SELECTED to buildMap<String, Any> {
            put("motivation_id", motivation.id)
            put("window_id", windowId)
            putAll(Stamp.of(1))
        }

    /** The whole set once per accepted Continue: list order, comma-joined, never one per tap. */
    fun vibeSelected(vibes: Set<String>, windowId: String) = VIBE_SELECTED to buildMap<String, Any> {
        put("vibe_ids", vibeIds(vibes))
        put("vibe_count", vibes.size)
        put("window_id", windowId)
        putAll(Stamp.of(0))
    }

    /**
     * Once per accepted Continue, even when nothing changed. Never the genders (Class 2) -- only
     * WHETHER they changed, in `filters_changed`. `max_age` is null for 65+, never 65.
     */
    fun filtersChanged(
        entry: CheckInFilters,
        now: CheckInFilters,
        windowId: String,
        openedFrom: String? = null,
        /** The remote flag `filters_dealbreakers`: off, both booleans are sent as false (05b). */
        dealbreakers: Boolean = false,
    ) =
        CHECK_IN_FILTERS_CHANGED to buildMap<String, Any> {
            put("filters_changed", com.showup.checkin.filtersChanged(entry, now))
            put("min_age", now.minAge)
            put("max_age", now.maxAgeOrNull ?: JsonNull)
            put("distance_km", now.distanceKm)
            // Both on every accepted Continue, changed or not (taxonomy 1.4.23); a tap sends nothing.
            put("age_dealbreaker", dealbreakers && now.ageDealbreaker)
            put("distance_dealbreaker", dealbreakers && now.distanceDealbreaker)
            put("window_id", windowId)
            if (openedFrom != null) put("opened_from", openedFrom)
            putAll(Stamp.of(0))
        }

    /** §32: why a commit did not save. */
    enum class CommitFailure(val wire: String) { Network("network"), Server("server"), ContactInfo("contact_info") }

    /** Once per request that did not save; `attempt` is 1 for the first tap on this screen view. */
    fun commitFailed(reason: CommitFailure, attempt: Int, windowId: String) =
        CHECK_IN_COMMIT_FAILED to buildMap<String, Any> {
            put("reason", reason.wire)
            put("attempt", attempt)
            put("window_id", windowId)
            putAll(Stamp.of(0))
        }
}

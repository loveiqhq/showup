/*
 * DateAnalytics.kt
 * ShowUp · the Date screen's events (SHOWUP-193; family K, registry 1.4.32 / taxonomy 1.4.25)
 *
 * Builders only: each returns the name and the payload, and the view model reports them. Names and
 * values are the registry's, never typed at a call site.
 *
 * NOT FROM HERE: `match_mate_*` (Date 10), `coordinate_viewed` (Date 02), `arrival_*` (Date 03),
 * `date_status_*` (Date 06), `date_cancelled` (Date 07). Their entry points are on this screen;
 * their events belong to their tickets. `coordinate_unlocked` is the server's.
 */
package com.showup.date

import com.showup.profile.Stamp
import java.time.Instant

/**
 * `enums.json` §38 `date_quick_action`, minus `ride`: Order Ride is hidden until a ride-app API
 * exists, so `ride` is reserved and never emitted.
 */
enum class QuickAction(val wire: String) {
    Directions("directions"),
    Share("share"),
    Calendar("calendar"),
    CopyAddress("copy_address"),
}

object DateAnalytics {
    const val DATE_SCREEN_VIEWED = "date_screen_viewed"
    const val DATE_QUICK_ACTION_TAPPED = "date_quick_action_tapped"

    /**
     * Once per visit, beside `screen_viewed` -- never on the in-place unlock. `state` is the
     * Coordinate card's (`locked` / `available`); C is `available` with `has_unread`.
     */
    fun dateScreenViewed(date: ActiveDate, state: DateState, now: Instant) = DATE_SCREEN_VIEWED to buildMap<String, Any> {
        put("date_id", date.id)
        put("state", if (state == DateState.Locked) "locked" else "available")
        put("has_unread", state == DateState.Unread)
        put("checked_in", date.checkedIn)
        put("minutes_before_start", minutesBeforeStart(date.meetAt, now))
        putAll(Stamp.of(0))
    }

    /** On the tap, before the OS hand-off. Whether the OS sheet is completed is not ours. */
    fun quickActionTapped(date: ActiveDate, action: QuickAction, now: Instant) = DATE_QUICK_ACTION_TAPPED to buildMap<String, Any> {
        put("date_id", date.id)
        put("action", action.wire)
        put("minutes_before_start", minutesBeforeStart(date.meetAt, now))
        putAll(Stamp.of(0))
    }
}

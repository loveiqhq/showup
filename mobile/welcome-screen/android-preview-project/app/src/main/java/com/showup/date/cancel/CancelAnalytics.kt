/*
 * CancelAnalytics.kt
 * ShowUp · Date 07's client events (SHOWUP-198; family K, registry 1.4.37)
 *
 * NOT FROM HERE: `date_cancelled` (the server's, on success only), the retired cancel_link_tapped /
 * partner_cancellation_received / rematch_started, `date_ended`, `date_ended_in_chat`, and anything carrying the
 * match's id, a place, a coordinate, free text -- or the removed `initiator` and `reason`.
 */
package com.showup.date.cancel

import com.showup.profile.Stamp

/** Registry section 46 `exit`: how the guard sheet closed without a cancel. */
enum class CancelExit(val wire: String) { Keep("keep"), Dismissed("dismissed"), Message("message") }

object CancelAnalytics {
    const val CANCEL_GUARD_SHOWN = "cancel_guard_shown"
    const val CANCEL_ABANDONED_AT_GUARD = "cancel_abandoned_at_guard"

    /** The sheet opens, every time. There is no separate link-tap event. */
    fun guardShown(dateId: String, chatOpen: Boolean, penaltyPoints: Int, minutesBeforeStart: Int) =
        CANCEL_GUARD_SHOWN to buildMap<String, Any> {
            put("date_id", dateId)
            put("chat_open", chatOpen)
            put("penalty_points", penaltyPoints)
            put("minutes_before_start", minutesBeforeStart)
            putAll(Stamp.of(0))
        }

    /** The sheet closes without a cancel. [changeRequested]: a picker sent a request from this sheet this session. */
    fun abandoned(dateId: String, exit: CancelExit, changeRequested: Boolean) =
        CANCEL_ABANDONED_AT_GUARD to buildMap<String, Any> {
            put("date_id", dateId)
            put("exit", exit.wire)
            put("change_requested", changeRequested)
            putAll(Stamp.of(0))
        }
}

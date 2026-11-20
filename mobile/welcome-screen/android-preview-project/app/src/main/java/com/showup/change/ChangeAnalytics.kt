/*
 * ChangeAnalytics.kt
 * ShowUp · Date 05's client events (SHOWUP-196; registry 1.4.35, family K -- Date Coordination)
 *
 * Builders only: each returns the name and the payload. The picker's model reports them. The server owns
 * `change_suggested`, `change_agreed` and `change_expired`, so a successful send fires nothing here.
 *
 * NEVER IN A PAYLOAD: a place name, an address, a coordinate or a distance. Every payload is ids, the kind
 * and the entry -- and the picker is an overlay on `date_screen` / `date_chat`, so it fires no `screen_viewed`.
 */
package com.showup.change

import com.showup.date.minutesBeforeStart
import com.showup.profile.Stamp
import java.time.Instant

object ChangeAnalytics {
    const val CHANGE_PICKER_OPENED = "change_picker_opened"
    const val CHANGE_PICKER_DISMISSED = "change_picker_dismissed"

    /** The picker is shown: from a chip or tile (after `action_chip_used`), or from Suggest another (alone). */
    fun pickerOpened(dateId: String, kind: ChangeKind, entry: ChangeEntry, meetAt: Instant, now: Instant) =
        CHANGE_PICKER_OPENED to buildMap<String, Any> {
            put("date_id", dateId)
            put("kind", kind.wire)
            put("entry", entry.wire)
            put("minutes_before_start", minutesBeforeStart(meetAt, now))
            putAll(Stamp.of(0))
        }

    /** Cancel or back, with nothing sent. [hadSelection]: a slot or a place was selected when it closed. */
    fun pickerDismissed(dateId: String, kind: ChangeKind, entry: ChangeEntry, hadSelection: Boolean) =
        CHANGE_PICKER_DISMISSED to buildMap<String, Any> {
            put("date_id", dateId)
            put("kind", kind.wire)
            put("entry", entry.wire)
            put("had_selection", hadSelection)
            putAll(Stamp.of(0))
        }
}

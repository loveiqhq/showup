/*
 * DateEndAnalytics.kt
 * ShowUp · Date 06's client events (SHOWUP-197; family N, registry 1.4.39)
 *
 * NOT FROM HERE: `date_ended` and `problem_stay_flagged` (the server's), `screen_viewed` for B to J (they are
 * sheets on `date_screen`), the retired wrapup_prompted / wrapup_confirmed / grace_period_* /
 * no_penalty_exit_granted, and anything carrying a coordinate, a distance, a place or free text.
 */
package com.showup.date.end

import com.showup.date.EndEntry
import com.showup.profile.Stamp

object DateEndAnalytics {
    const val WRAPUP_OPENED = "wrapup_opened"
    const val WRAPUP_OUTCOME_SELECTED = "wrapup_outcome_selected"
    const val PROBLEM_REPORT_OPENED = "problem_report_opened"
    const val PROBLEM_CATEGORY_SELECTED = "problem_category_selected"

    /** B opens. [entry] is `end_date_bar` or `checkin_bar_link`. */
    fun wrapupOpened(dateId: String, entry: EndEntry, checkedIn: Boolean, minutesVsStart: Int) =
        WRAPUP_OPENED to buildMap<String, Any> {
            put("date_id", dateId)
            put("entry", entry.wire)
            put("checked_in", checkedIn)
            put("minutes_vs_start", minutesVsStart)
            putAll(Stamp.of(0))
        }

    /** A B option tapped, before any gate (registry section 43 `outcome`: met or partner_no_show). */
    fun wrapupOutcomeSelected(dateId: String, option: EndOption, checkedIn: Boolean) =
        WRAPUP_OUTCOME_SELECTED to buildMap<String, Any> {
            put("date_id", dateId)
            put("outcome", if (option == EndOption.Met) "met" else "partner_no_show")
            put("checked_in", checkedIn)
            putAll(Stamp.of(0))
        }

    /** H opens. */
    fun problemReportOpened(dateId: String, checkedIn: Boolean, minutesVsStart: Int) =
        PROBLEM_REPORT_OPENED to buildMap<String, Any> {
            put("date_id", dateId)
            put("checked_in", checkedIn)
            put("minutes_vs_start", minutesVsStart)
            putAll(Stamp.of(0))
        }

    /** An H option tapped, before any gate (section 44 `category`: partner_no_show or uncomfortable). */
    fun problemCategorySelected(dateId: String, option: EndOption, checkedIn: Boolean) =
        PROBLEM_CATEGORY_SELECTED to buildMap<String, Any> {
            put("date_id", dateId)
            put("category", if (option == EndOption.Uncomfortable) "uncomfortable" else "partner_no_show")
            put("checked_in", checkedIn)
            putAll(Stamp.of(0))
        }
}

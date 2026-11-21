/*
 * DateEndRules.kt
 * ShowUp · Date 06's plain rules (SHOWUP-197)
 *
 * `minutesVsStart` is the arrival gate's (`arrival/ArrivalRules.kt`): the same whole minutes from the agreed
 * meet time, negative before, so the end events and the gate's agree and the server's `date_ended` matches.
 */
package com.showup.date.end

import com.showup.arrival.ArrivalEntry
import com.showup.date.EndReason
import java.time.Instant

/** Which sheet is up: B (and its steps) from the End Date bar or the link, or H (and its steps) from the Report card. */
enum class EndRoute { Wrap, Report }

/** The step inside the sheet: the menu (B or H), C, the guide (E or F), G, or I. */
enum class EndStep { Menu, Met, Guide, NoShow, Uncomfortable }

/** What a menu row picks. `We met` exists on B only, `I don't feel comfortable` on H only. */
enum class EndOption { Met, NoShow, Uncomfortable }

/** R11: the no-show end unlocks at the AGREED meet time, to the second. */
fun pastMeet(meetAt: Instant, now: Instant): Boolean = !now.isBefore(meetAt)

/** Where the picked option leads: with no check-in needed, or once the gate has stored one (R9). */
fun endStepAfterGate(option: EndOption): EndStep = when (option) {
    EndOption.Met -> EndStep.Met
    EndOption.NoShow -> EndStep.Guide
    EndOption.Uncomfortable -> EndStep.Uncomfortable
}

/** `We met` needs no check-in (both confirming in the review counts); the two problem routes do. */
fun optionNeedsCheckIn(option: EndOption): Boolean = option != EndOption.Met

/** The reason the CTA of [step] ends the date with; null where no CTA ends it. */
fun endReasonFor(step: EndStep): EndReason? = when (step) {
    EndStep.Met -> EndReason.Met
    EndStep.NoShow -> EndReason.PartnerNoShow
    EndStep.Uncomfortable -> EndReason.Uncomfortable
    EndStep.Menu, EndStep.Guide -> null
}

/** The guide's end button: its label, whether it answers a tap, and whether it leads with the clock icon. */
data class GuideEnd(val label: String, val enabled: Boolean, val clock: Boolean)

fun guideEnd(pastMeet: Boolean, meetTime: String): GuideEnd =
    if (pastMeet) {
        GuideEnd(DateEndCopy.END_OPEN, enabled = true, clock = false)
    } else {
        GuideEnd(DateEndCopy.END_LOCKED.format(meetTime), enabled = false, clock = true)
    }

/** `Message {name}` is hidden while the chat is locked; the end button moves up. */
fun messageShown(chatOpen: Boolean): Boolean = chatOpen

/** The entry the gate reports: `wrapup` from B's route, `report` from H's. */
fun gateEntryFor(route: EndRoute): ArrivalEntry = when (route) {
    EndRoute.Wrap -> ArrivalEntry.Wrapup
    EndRoute.Report -> ArrivalEntry.Report
}

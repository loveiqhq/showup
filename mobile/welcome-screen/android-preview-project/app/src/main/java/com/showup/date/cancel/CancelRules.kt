/*
 * CancelRules.kt
 * ShowUp · Date 07's plain rules (SHOWUP-198)
 *
 * The guard sheet follows Date 05's gate and never a copy of it (R12): [changeClosed] is the negation of
 * `changeWindowOpen`, the first half of `changeKindAvailable`, and [cancelTiles] filters through
 * `changeKindAvailable` itself. `minutesBeforeStart` is the Date screen's (`date/DateRules.kt`), so this sheet's
 * events agree with Date 05's and the server's.
 */
package com.showup.date.cancel

import com.showup.change.ChangeKind
import com.showup.change.DateChange
import com.showup.change.changeClock
import com.showup.change.changeKindAvailable
import com.showup.change.changeWindowOpen
import com.showup.chat.headerLine
import com.showup.date.ActiveDate
import com.showup.date.EndedOutcome
import com.showup.date.OutcomeReason
import java.time.Instant
import java.time.ZoneId

/**
 * I: from Date 05's cutoff, or once either person has checked in, a new time or place can no longer be agreed.
 * A server that sends no `change` block (before Date 05) is not "closed": nothing was ever offered, so there are no
 * tiles and no body line, and `Message {name} instead` follows the chat alone -- it must not open a locked chat.
 */
fun changeClosed(change: DateChange?, now: Instant): Boolean = change != null && !changeWindowOpen(change, now)

/** The body line shows only with something under it: a tile Date 05 still offers, or D's confirmation. */
fun solutionBodyShown(tiles: List<ChangeKind>, sent: Boolean, changeClosed: Boolean): Boolean =
    !changeClosed && (tiles.isNotEmpty() || sent)

/** The tiles that still show, in the chips' order: Date 05's own gate for each kind. */
fun cancelTiles(change: DateChange?, now: Instant): List<ChangeKind> =
    ChangeKind.entries.filter { changeKindAvailable(change, it, now) }

/** `Message {name} instead`: whenever the chat is open (C), and always from the cutoff (I) -- it is open by then. */
fun showMessage(chatOpen: Boolean, changeClosed: Boolean): Boolean = chatOpen || changeClosed

/** D's `{value}`: the requested time on the phone's clock, or the requested place's name. Empty when none was chosen. */
fun sentLabel(kind: ChangeKind, slot: Instant?, placeName: String?, zone: ZoneId): String = when (kind) {
    ChangeKind.Time -> slot?.let { changeClock(it, zone) }.orEmpty()
    ChangeKind.Place -> placeName.orEmpty()
}

/**
 * The red link shows while the date is active (a date is only passed here while it is), THIS user has not checked
 * in -- the match's check-in does not hide it -- and the end routes are on (R1: flow 10 is not built, so a cancel
 * with nowhere to land stays hidden in production).
 */
fun cancelLinkShown(date: ActiveDate): Boolean = date.endsEnabled && !date.checkedIn

/** The portrait's pill: the chat header's line, `Today · 19:00 · Cafe Krone` from the binding values (R10). */
fun guardPill(date: ActiveDate, zone: ZoneId): String = headerLine(date, zone)

/** H: the other person called the date off. The canceller's own landing is `landAfterEnd`. */
fun cancelledOnYou(outcome: EndedOutcome): Boolean = outcome.reason == OutcomeReason.Cancelled && !outcome.endedByYou

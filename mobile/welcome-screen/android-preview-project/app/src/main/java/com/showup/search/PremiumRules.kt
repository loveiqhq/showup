/*
 * PremiumRules.kt
 * ShowUp · the Premium grid (SHOWUP-185) and the Likes tab (SHOWUP-186): their rules and copy, no UI
 *
 * The cards, the closed reasons, the sorry sheet, the undo toast and the fly-out's geometry, as plain
 * values and functions tested on their own (`PremiumRulesTest`), the same on iOS
 * (`PremiumRules.swift`). Everything Search 01 already decides -- the distance, the name line, the
 * meet time, the Show-up count -- is read from `SearchRules.kt`, never copied.
 *
 * Copy is verbatim from the tickets (`search/tickets/02-grid.md`, `03-likes-received.md`), quoted
 * back by `audit/verify-premium.py`.
 */
package com.showup.search

import java.time.ZoneId

/** The two tickets' copy, one constant per string, so the verifier can quote each against them. */
object PremiumCopy {
    /** The top bar's label: "its own label, always that word" (drawn in capitals, read as written). */
    const val PREMIUM_TAG = "Premium"
    const val LIKED_YOU = "Liked you"

    // The cards' accessible names. A mark is part of the name, never colour only.
    const val CARD_LABEL = "Open %s's profile"
    const val CARD_LABEL_LIKED = "Open %s's profile, liked"
    const val CARD_LABEL_NOT_NOW = "Open %s's profile, not now"

    /** The marked label: `Not now`; `Not interested` shows only during the 150 ms hold. */
    const val NOT_NOW_LABEL = "Not now"
    const val NOT_INTERESTED_LABEL = "Not interested"

    // The profile, opened from a list.
    const val ALL_PROFILES = "All profiles"
    const val ALL_LIKES = "All likes"
    const val CONFIRM_DATE = "Confirm date"

    /** Be sure in mutual mode: `Confirm, and you and {name} have a confirmed, binding date {day}.` */
    const val BE_SURE_MUTUAL_LEAD = "Confirm, and you and %s have a "

    // The undo toast (Search 02 G, Search 03 D).
    const val UNDO_TOAST = "%s's like declined."
    const val UNDO = "Undo"

    // The sorry sheet (Search 02 18).
    const val SORRY_LINE = "There are still people ready to meet you. Your date could be one of them."
    const val FIND_SOMEONE_ELSE = "Find someone else"
    const val BACK_TO_LIKES = "Back to likes"

    // The Likes tab (Search 03).
    const val LIKES_INTRO_LEAD = "People who want to "
    const val LIKES_INTRO_EMPHASIS = "meet you"
    const val LIKES_INTRO_BODY = "Open a profile, then decide. Confirm and you two have a binding date."
    const val VIEW_PROFILE = "View profile"
    const val EMPTY_LEAD = "No likes "
    const val EMPTY_EMPHASIS = "yet"
    const val EMPTY_BODY = "When someone likes you, they show up here — with their message, if they wrote one."
    const val BACK_TO_SEARCH = "Back to search"

    // ── states the tickets do not draw ──────────────────────────────────────────────────────────
    //
    // PROPOSED, NOT TICKETED: a list that could not be read. The words follow Search 01's own
    // proposal and go to Philipp with it; the verifier lists them as proposed.
    const val LIKES_LOADING_LABEL = "Loading likes"
    const val LIKES_LOAD_FAILED_PROPOSED = "We couldn't load your likes just now. Please try again."
}

/**
 * Why a like closed without a decision (§36 `like_close_reason`), and every line each reason is
 * drawn with: the grid's closed card, the Likes list's closed card, and the sorry sheet.
 */
enum class CloseReason(
    val wire: String,
    /** The pill: on the closed grid card and on the sorry sheet's photo. */
    val pill: String,
    /** The grid card's line, in place of the meet time and motivation. */
    val gridLine: String,
    /** The Likes card's first line -- and, after `{name}. `, the grid card's accessible name. */
    private val firstLine: String,
    /** The Likes card's second line. */
    val likesBody: String,
    private val sorryLead: String,
    /** The sorry headline's italic, washed run. */
    val sorryEmphasis: String,
    private val sorryBodyText: String,
) {
    Matched(
        wire = "matched",
        pill = "Too late",
        gridLine = "Has a date with someone else.",
        firstLine = "Too late — %s has a date with someone else.",
        likesBody = "A like closes as soon as either of you matches. Confirm sooner next time.",
        sorryLead = "We're really sorry, %s confirmed a date ",
        sorryEmphasis = "just before you did.",
        sorryBodyText = "A like closes the moment either of you matches, and this time %s's match came first.",
    ),
    NoOverlap(
        wire = "no_overlap",
        pill = "Out of time",
        gridLine = "No time left to meet.",
        firstLine = "Out of time — you and %s have no time left to meet.",
        likesBody = "A like closes when your Check-Ins no longer overlap. Confirm sooner next time.",
        sorryLead = "Sorry, you and %s ",
        sorryEmphasis = "ran out of time.",
        sorryBodyText = "We're sorry this one didn't work out. Your Check-In and %s's no longer share any time, so the like has closed.",
    ),
    ;

    /** The Likes card's first line. No gendered pronoun, in either reason. */
    fun likesHead(name: String): String = firstLine.format(name)

    /** The closed grid card's accessible name: `{name}. Too late — {name} has a date with someone else.` */
    fun gridLabel(name: String): String = "$name. ${likesHead(name)}"

    /** The sorry headline before its italic run. */
    fun sorryHeadLead(name: String): String = sorryLead.format(name)

    fun sorryBody(name: String): String = sorryBodyText.format(name)

    companion object {
        /** A reason this app does not know (a newer server) is no reason: nothing changes. */
        fun of(wire: String?): CloseReason? = entries.firstOrNull { it.wire == wire }
    }
}

/** What the 150 ms hold before a fly-out shows: the filled heart, or the `Not interested` label. */
enum class Leave { Liked, NotInterested }

/**
 * A grid card's accessible name, mark included (Search 02: "a marked card announces its mark as part
 * of its name; the mark is never colour-only"). The closed card is not a control and has its own.
 *
 * [likedYou]: the `Liked you` badge, read as part of the card ("read as part of its card") -- what a
 * sighted user sees on it, a screen reader hears. Only while the badge is drawn: a marked card has
 * none, and during the fly-out the card's own mark is the news.
 */
fun gridCardLabel(name: String, notNow: Boolean, leaving: Leave?, likedYou: Boolean = false): String = when {
    leaving == Leave.Liked -> PremiumCopy.CARD_LABEL_LIKED.format(name)
    leaving == Leave.NotInterested -> "${PremiumCopy.CARD_LABEL.format(name)}, ${PremiumCopy.NOT_INTERESTED_LABEL.lowercase()}"
    notNow -> PremiumCopy.CARD_LABEL_NOT_NOW.format(name)
    likedYou -> "${PremiumCopy.CARD_LABEL.format(name)}, ${PremiumCopy.LIKED_YOU.lowercase()}"
    else -> PremiumCopy.CARD_LABEL.format(name)
}

/** The Likes card's photo: `Open {name}'s profile`, or `…, not now` once labelled. */
fun likesPhotoLabel(name: String, notNow: Boolean): String =
    (if (notNow) PremiumCopy.CARD_LABEL_NOT_NOW else PremiumCopy.CARD_LABEL).format(name)

/** The grid card's meta line: `{HH:MM} {day} · ~{n} km` -- on one line at 375. */
fun gridMetaLine(profile: SearchProfile, zone: ZoneId): String =
    "${meetLine(profile.meetAt, profile.meetDay, zone)} · ${searchDistance(profile.distanceKm)}"

/** The Likes card's distance, after the name: `· < 1 km` / `· ~{n} km`. */
fun likesDistance(km: Int): String = "· ${searchDistance(km)}"

/** The liker's message, "in straight double quotes, as written". */
fun quotedMessage(message: String): String = "\"$message\""

/** The undo toast's line. */
fun undoLine(name: String): String = PremiumCopy.UNDO_TOAST.format(name)

/**
 * The fly-out's destination, from the card's centre: the visible list's centre x and 72% of its
 * height (Search 02 20). Both boxes in the same coordinates; the result is the translation to apply.
 */
fun flyOutTarget(
    cardLeft: Float, cardTop: Float, cardWidth: Float, cardHeight: Float,
    viewLeft: Float, viewTop: Float, viewWidth: Float, viewHeight: Float,
): Pair<Float, Float> = Pair(
    viewLeft + viewWidth / 2f - (cardLeft + cardWidth / 2f),
    viewTop + viewHeight * FLY_OUT_DROP - (cardTop + cardHeight / 2f),
)

/** Where in the visible list the card lands: 72% of its height. */
const val FLY_OUT_DROP = 0.72f

/** How small the card is when it disappears. */
const val FLY_OUT_SCALE = 0.12f

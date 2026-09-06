/*
 * TutorialAnalytics.kt
 * ShowUp · analytics seam for the tutorial flow
 *
 * A seam, not an integration. Real code rather than a commented-out reminder: a comment claiming
 * something is tracked is indistinguishable from tracking that works, and this project has already
 * been bitten by that more than once.
 *
 * The default does nothing, and will keep doing nothing until there is somewhere to send events —
 * the backend's analytics module has no ingest endpoint yet, and consent capture is not built, so
 * AnalyticsService.track() would discard anything sent today. When both land, pass a real
 * implementation in and no screen needs to change.
 */
package com.showup.tutorial

// The tracker contract lives in com.showup.analytics so the welcome and sign-up
// screens can use it without depending on this package.
import com.showup.analytics.AnalyticsTracker

object TutorialAnalytics {
    const val CARD_VIEWED = "tutorial_card_viewed"
    const val CTA_TAPPED = "tutorial_cta_tapped"
    const val BACK_TAPPED = "tutorial_back_tapped"

    /** Fired once, from the final screen's CTA. Completion of the whole flow, not of one screen. */
    const val COMPLETED = "tutorial_completed"

    private fun props(card: Int, name: String): Map<String, Any> =
        mapOf("card" to card, "card_name" to name)

    /** Card 1. The spec sheet is `00-welcome-spec-sheet`, but the ticket's tracking section names
     *  this screen "Tutorial 1 - Welcome", so it is card 1. */
    val welcome = props(1, "welcome")

    /** Card 2 — SHOWUP-135, "Tutorial 2 - Meet". Screen 1 of the 5-segment tour. */
    val meet = props(2, "meet")

    /** Card 3 — SHOWUP-136, "Tutorial 3 - Match". */
    val match = props(3, "match")

    /** Card 4 — SHOWUP-137, "Tutorial 4 - Match means meet". */
    val binding = props(4, "match_means_meet")

    /** Card 5 — SHOWUP-138, "Tutorial 5 - 30min". */
    val thirty = props(5, "thirty_minutes")

    /** Card 6 — SHOWUP-139, "Tutorial 6 - ShowUpRate". The terminal screen. */
    val showUpRate = props(6, "show_up_rate")
}

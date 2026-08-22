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

interface AnalyticsTracker {
    fun track(event: String, properties: Map<String, Any>)
}

object NoOpAnalytics : AnalyticsTracker {
    override fun track(event: String, properties: Map<String, Any>) = Unit
}

/**
 * Names and properties for the tutorial flow.
 *
 * "Tutorial", deliberately not "onboarding" — onboarding is the separate flow where someone fills
 * in their profile. Conflating them makes the funnel unreadable later.
 *
 * Names follow the backend taxonomy: snake_case, `<noun>_<verb-ed>`, snake_case properties, no free
 * text and no personal data. The card number is a property rather than part of the event name, so
 * all six screens share these two events instead of inventing twelve — and "where do people drop
 * out of the tutorial?" stays one query grouped by `card`.
 */
object TutorialAnalytics {
    const val CARD_VIEWED = "tutorial_card_viewed"
    const val CTA_TAPPED = "tutorial_cta_tapped"

    private fun props(card: Int, name: String): Map<String, Any> =
        mapOf("card" to card, "card_name" to name)

    /** Card 1. The spec sheet is `00-welcome-spec-sheet`, but the ticket's tracking section names
     *  this screen "Tutorial 1 - Welcome", so it is card 1. */
    val welcome = props(1, "welcome")

    /** Card 2 — SHOWUP-135, "Tutorial 2 - Meet". Screen 1 of the 5-segment tour. */
    val meet = props(2, "meet")
}

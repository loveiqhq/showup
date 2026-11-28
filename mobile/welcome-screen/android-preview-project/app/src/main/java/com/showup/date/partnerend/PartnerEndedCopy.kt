/*
 * PartnerEndedCopy.kt
 * ShowUp · Date 08's strings, exactly the ticket's `## Copy` (SHOWUP-199)
 *
 * `%s` is A's (the match's) first name. The apostrophes are the ticket's own, straight, and are not normalised.
 * The runs the reference draws as `<strong>` / `<em>` are split into [Run]s so a screen styles them without
 * re-typing a word; `audit/verify-partner-end.py` compares the plain strings with the ticket.
 */
package com.showup.date.partnerend

import com.showup.date.cancel.Run
import com.showup.date.cancel.RunStyle

private fun p(text: String) = Run(text)
private fun b(text: String) = Run(text, RunStyle.Bold)

object PartnerEndedCopy {
    const val CTA = "Proceed to review"

    // A · You didn't show up
    const val A_HEADLINE_LEAD = "%s ended the date, stating you "
    const val A_HEADLINE_ITALIC = "didn't show up."

    /** Two lines: the reference's `<br/>` falls after "waiting for you.". */
    fun aSub(name: String): List<Run> = listOf(p("$name "), b("ended the date"), p(" after waiting for you.\nIt's now closed."))

    val A_ROWS: List<List<Run>> = listOf(
        listOf(
            b("Not showing up"), p(" to a date — or being "), b("very late"),
            p(" without reaching out to your date — shows "), b("no respect for each other's time."),
        ),
        listOf(p("To clear this up, "), b("proceed to the review"), p(" and "), b("give us your side.")),
        listOf(
            p("Once you've completed the review, we run an "), b("automated check"),
            p(": did you both check in, and did you message each other? Your "), b("Show-up Rate only drops"),
            p(" if the no-show is "), b("proven."),
        ),
    )

    // B · Uncomfortable
    const val B_HEADLINE_LEAD = "%s ended the date, saying they "
    const val B_HEADLINE_ITALIC = "didn't feel comfortable"
    const val B_HEADLINE_REST = " in the situation."
    const val B_SUB = "The date is now closed."
    val B_ROWS: List<List<Run>> = listOf(
        listOf(
            b("Respect is non-negotiable"), p(" at Show Up. Behaviour that leaves someone feeling "), b("uncomfortable"),
            p(" is not accepted here."),
        ),
        listOf(
            p("So far we only have "), b("one side of the story."), p(" In the next step, tell us "),
            b("what happened from your point of view."),
        ),
        listOf(p("We "), b("review every report"), p(" and "), b("act on it where it's needed.")),
    )

    // C · We met
    const val C_HEADLINE_LEAD = "%s ended the date, stating you both have "
    const val C_HEADLINE_ITALIC = "met."
    const val C_SUB = "Thank you for being real and showing up to your date."
    val C_ROWS: List<List<Run>> = listOf(
        listOf(p("We hope it was a good one. "), b("Head into the review"), p(" and tell us how it went — it takes a minute.")),
        listOf(p("Once the review is done, you're free to "), b("search for your next date.")),
    )

    // D · Your date has ended
    const val D_HEADLINE_LEAD = "Your date has "
    const val D_HEADLINE_ITALIC = "ended."

    /** `time` is the meet time already formatted for the device's clock (see [meetClock]). */
    fun dSub(name: String, time: String): String = "We closed your date with $name automatically, 2 hours after the meet time ($time)."

    /** An older server sends no meet time: the ticket's parenthesis goes with it (`%s` is the name). */
    const val D_SUB_NO_TIME = "We closed your date with %s automatically, 2 hours after the meet time."

    val D_ROWS: List<List<Run>> = listOf(
        listOf(p("If you met, we hope you had a great time. "), b("Either way, tell us how it went in the review.")),
        listOf(p("Once the review is done, you're free to "), b("search for your next date.")),
    )
}

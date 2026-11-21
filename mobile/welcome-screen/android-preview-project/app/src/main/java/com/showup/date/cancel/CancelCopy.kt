/*
 * CancelCopy.kt
 * ShowUp · Date 07's strings, exactly the ticket's (SHOWUP-198)
 *
 * `%s` is the match's first name, `%d` the rate config's drop (`ActiveDate.cancelPenaltyPoints`, 10 in MVP): the
 * cost box names it from the value it is given and never carries a literal. The runs the reference draws as
 * `<strong>` / `<em>` are split into [Run]s here so a screen styles them without re-typing a word; [plain] is the
 * string with the marks removed, which the tests compare with the ticket. "her" stays (16 Nov).
 */
package com.showup.date.cancel

/** How a run of copy is drawn. [Danger] is the cost box's red `no-show`. */
enum class RunStyle { Plain, Bold, Italic, Danger }

/** One styled stretch of a string. */
data class Run(val text: String, val style: RunStyle = RunStyle.Plain)

private fun p(text: String) = Run(text)
private fun b(text: String) = Run(text, RunStyle.Bold)
private fun i(text: String) = Run(text, RunStyle.Italic)

/** The string a list of runs reads as. */
fun plain(runs: List<Run>): String = runs.joinToString("") { it.text }

object CancelCopy {
    // A
    const val LINK = "Cancel this date"

    // B: the portrait and the gut-punch
    const val COUNTING = "%s is counting on you."
    const val HEADLINE_LEAD = "Are you really ready to "
    const val HEADLINE_ITALIC = "disappoint her?"
    val BODY = listOf(
        p("This isn't a button — it's a real person who cleared her evening, picked something to wear, and is quietly looking forward to meeting "),
        i("you"),
        p(". Cancelling lands on her side of the screen."),
    )

    /** The three reasons, in order. */
    val REASONS: List<List<Run>> = listOf(
        listOf(p("Almost every date leaves you "), b("glad you went"), p(". The hard part is only the front door.")),
        listOf(p("She chose "), i("you"), p(" out of everyone. That's rare — showing up honours it.")),
        listOf(p("You'll go to sleep "), b("proud you stayed real"), p(" and kept your word.")),
    )

    // The solution box
    const val SOLUTION_TITLE = "Sometimes life happens — find a solution with %s!"
    const val SOLUTION_BODY = "Would a later time or another place work? Request it — if %s agrees, your date goes ahead."
    const val CHANGE_TIME = "Change meet time"
    const val CHANGE_PLACE = "Change location"
    const val MESSAGE = "Message %s instead"

    /** D: `{value}` bold, then the line. */
    const val SENT_TAIL = " requested. Waiting for %s to agree — until then your date stays as planned."

    // The cost box
    fun cost(drop: Int): List<Run> = listOf(
        p("Cancelling this date counts as a "),
        Run("no-show", RunStyle.Danger),
        p(" and lowers your "),
        b("Show-up Rate by $drop"),
        p(" — a lower rate means fewer likes and matches. Let it stay low and you'll eventually lose access to the app entirely."),
    )

    // The choices
    const val KEEP = "Don't cancel my date"
    const val CONFIRM = "Cancel anyway and let her down"
    const val WARNING = "This can't be undone — your date is off and you'll never match %s again."

    // E
    const val CANCEL_FAILED = "Couldn't cancel the date. Check your connection and try again."

    // H: what the match sees
    const val H_HEADLINE_LEAD = "%s had to "
    const val H_HEADLINE_ITALIC = "cancel"
    const val H_HEADLINE_REST = " — we're truly sorry."
    val H_BODY = listOf(
        p("You made room in your day and you were ready to show up. "),
        b("Don't be discouraged - someone true as you is just a like away from meeting you."),
    )
    fun hRows(name: String): List<List<Run>> = listOf(
        listOf(p("Your "), b("Show-up Rate stays untouched."), p(" A date someone else cancels will "), b("never"), p(" count against you. You did everything right.")),
        listOf(p("Show Up is built on "), b("reliability"), p(" and "), b("treasuring each other's time"), p(" — real, warm, in-person connection. "), b("This isn't how our community behaves.")),
        listOf(p("People who "), b("repeatedly cancel or don't show up"), p(" become less visible, and eventually "), b("lose access."), p(" $name carries that — not you.")),
    )
    const val H_CTA = "Find someone who shows up"
}

/*
 * DateEndCopy.kt
 * ShowUp · Date 06's strings, exactly the ticket's (SHOWUP-197)
 *
 * `%s` is the match's first name, or the binding meet time where the name says so. The apostrophes are the
 * ticket's own, straight in some strings and typographic in others; they are not normalised.
 * `audit/verify-end.py` compares these with the ticket. The bold runs the reference draws (`<b>`,
 * `<strong>`) are split into pieces here so a screen can style them without re-typing a word.
 */
package com.showup.date.end

object DateEndCopy {
    // A: the End Date bar
    const val BAR_TITLE = "Your date with %s"
    const val BAR_LINE = "Tap End Date - when your date is finished."
    const val BAR_BUTTON = "End Date"

    // B: How did it go?
    const val WRAP_TITLE = "How did it go with %s?"
    const val MET_TITLE = "We met — date is finished"
    const val MET_SUB = "Only once you’ve actually met in person."
    const val NO_SHOW_TITLE = "My date isn't showing up"
    const val NO_SHOW_SUB = "They're not here yet — here's what to do and how the wait works."
    const val CANCEL = "Cancel"

    // C: We met
    const val C_TITLE = "Thanks for showing up"
    const val C_BODY_1 = "You are wonderful for showing up to %s."
    const val C_BODY_2 = "We hope you had a great time together."
    const val C_STEP_1 = "Please review your date now."
    const val C_STEP_2 = "Search for new dates resumes after the review."
    const val C_NOTE_1 = "This is only for dates that happened. Need to call it off "
    const val C_NOTE_BOLD_1 = "before"
    const val C_NOTE_2 = " meeting? Use "
    const val C_NOTE_BOLD_2 = "Cancel date"
    const val C_NOTE_3 = " in the chat menu. Ending a date you didn’t attend counts as a no-show and lowers your Show-up Rate."
    const val C_CTA = "End Date → Review"

    // What happens next
    const val WHAT_NEXT = "What happens next"

    // E / F: Not here yet?
    const val BACK = "Back"
    const val GUIDE_TITLE = "Not here yet?"
    const val GUIDE_1_BOLD = "Message %s"
    const val GUIDE_1 = " to ask where they are. A quick “on my way?” clears up most late arrivals."
    const val GUIDE_2_A = "Please give it at least "
    const val GUIDE_2_BOLD = "10 minutes"
    const val GUIDE_2_B = ". Something can always come up."
    const val GUIDE_3 = "Please check you are at the right location."
    const val GUIDE_4_A = "If you get no reply and you’re "
    const val GUIDE_4_BOLD = "10 minutes"
    const val GUIDE_4_B = " past your meet time — you’re free to end the date and leave."
    const val MESSAGE = "Message %s"
    const val END_LOCKED = "You can end the date from %s"
    const val END_OPEN = "It’s been 10+ minutes — end date"

    // G: Thanks for waiting
    const val G_TITLE = "Thanks for waiting"
    const val G_BODY_A = "You gave %s time. "
    const val G_BODY_BOLD = "Leaving now won’t lower your Show-up Rate."
    const val G_STEP_1 = "Please give us a little more information in the review now - helps us to determine the consequences for your date partner."
    const val G_STEP_2 = "No penalty to you — your Show-up Rate stays where it is."
    const val G_STEP_3 = "Searching for a new date will resume."
    const val G_CTA = "End date and review"

    // H: Report a problem
    const val REPORT_CARD_TITLE = "Report a problem"
    const val REPORT_CARD_LINE = "If you feel unsafe or uncomfortable, let us know."
    const val H_TITLE = "Report a problem"
    const val H_LINE = "Everything here is confidential. We've got you."
    const val UNCOMFORTABLE_TITLE = "I don't feel comfortable"
    const val UNCOMFORTABLE_SUB = "If you don't feel comfortable in the situation - head out now. That's ok!"

    // I: I don't feel comfortable
    const val I_TITLE = "We're sorry to hear that."
    const val I_BODY_A = "You don't have to explain a thing — you showed up, so "
    const val I_BODY_BOLD_1 = "you're free to head out now,"
    const val I_BODY_B = " with "
    const val I_BODY_BOLD_2 = "no penalty to your Show-up Rate."
    const val I_STEP_1 = "No penalty to you — you showed up, and that's what counts."
    const val I_STEP_2 = "Head out whenever you're ready — no need to say goodbye."
    const val I_STEP_3 = "Review your date next. What you tell us stays confidential."
    const val HEAD_OUT = "Head out now"
    const val STAY = "Stay for now"

    // J: the end failed
    const val END_FAILED = "Couldn't end the date. Check your connection and try again."
}

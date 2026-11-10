/*
 * ChatCopy.kt
 * ShowUp · every string the coordinate chat draws (SHOWUP-194)
 *
 * From the ticket's "Copy — final strings", verbatim; `audit/verify-chat.py` quotes the ticket at run
 * time against this file and its Swift twin. The chips' labels and drafts are the vocabulary's
 * ([ChatReason]), not repeated here. `%s` / `%d` are filled by [ChatRules] or the screen. A headline
 * whose word is italic is split into its runs; a paragraph with bold runs lists them beside it.
 */
package com.showup.chat

object ChatCopy {
    // ── A · Tutorial ────────────────────────────────────────────────────────
    const val TUTORIAL_HEADER = "Coordinate with %s"
    const val TUTORIAL_HEADLINE_ITALIC = "Must read"
    const val TUTORIAL_HEADLINE_REST = " — how messaging works"
    const val TUTORIAL_INTRO =
        "How you use the chat is a major part of how we can determine if one of you later says the other " +
            "never showed while they insist they did."
    val TUTORIAL_INTRO_BOLD = listOf(
        "How you use the chat",
        "determine if one of you later says the other never showed while they insist they did.",
    )
    const val STEP_1_TITLE = "Use our action chips"
    const val STEP_1_BODY =
        "Make use of our action chips whenever possible. It allows us to analyze the situation of your date " +
            "and understand who played real and who didn't."
    val STEP_1_BOLD = listOf("Make use", "action chips", "understand who played real and who didn't.")
    const val EXAMPLES = "Examples:"
    const val EXAMPLE_ARRIVED = "I arrived"
    const val EXAMPLE_LATE = "Running late"
    const val STEP_2_TITLE = "We never read your words"
    const val STEP_2_BODY =
        "What you type stays private between you and your date. All we can see is which signal you picked " +
            "and whether a message was sent and replied to — never the message itself."
    val STEP_2_BOLD = listOf(
        "What you type stays private between",
        "which signal you picked",
        "whether a message was sent and replied to",
    )
    const val OPEN_THE_CHAT = "Open the chat"
    const val TUTORIAL_PRIVATE = "Your messages are private. Only the signals are logged."

    // ── Chat header ─────────────────────────────────────────────────────────
    const val DATE_ON = "DATE ON"
    const val TODAY = "Today"
    const val TOMORROW = "Tomorrow"

    /** The header's countdown: `in {h}h {m}m`, `in 0h 45m` under an hour. */
    const val COUNTDOWN = "in %dh %dm"

    // ── B · Start ───────────────────────────────────────────────────────────
    const val START_HEADLINE_LEAD = "Start with a "
    const val START_HEADLINE_ITALIC = "reason"
    const val START_BODY_1 = "Tap one that fits best to start the chat."
    const val START_BODY_2 =
        "Make use of our action chips as much as possible to allow us to understand who played real and who didn't."
    const val START_PRIVACY = "We log which reason you pick — never what you type."

    // ── C · Field ───────────────────────────────────────────────────────────
    const val PLACEHOLDER = "Type a message, or tap a chip below"

    // ── D · Cancel guide ────────────────────────────────────────────────────
    const val GUIDE_TITLE = "Life happens — handle it with respect"
    const val GUIDE_1 =
        "Show Up is for reliable, committed people who want to meet in real life, quickly. But sometimes life " +
            "gets in the way. If there's really no way you can make it, explain why and say sorry. %s has freed " +
            "up time for you and is looking forward to meeting you."
    val GUIDE_1_BOLD = listOf("explain why and say sorry")
    const val GUIDE_2 =
        "Better still: move it to later today or to another place instead of cancelling. A different day can't " +
            "be set in the app — agree on one here, then one of you ends the date so you can both search again."
    val GUIDE_2_BOLD = listOf("move it to later today or to another place")
    const val GOT_IT = "Got it"
    const val PLACEHOLDER_LOCKED = "Read the note above, then tap Got it"
    const val GUIDE_ACKED = "Explain why, say sorry — in your own words."
    const val PLACEHOLDER_SORRY = "Explain why and say sorry to %s…"

    // ── E · Card ────────────────────────────────────────────────────────────
    const val CARD_LEAD = "Not meeting today?"
    const val CARD_BODY = "Once you've talked it through, one of you ends the date so you can both search again."
    const val END_DATE = "End date"

    // ── F · Sheet ───────────────────────────────────────────────────────────
    const val SHEET_TITLE = "End the date with %s?"
    const val SHEET_BODY = "The date won't happen, so there's no review. You can both search again straight away."
    const val KEEP_THE_DATE = "Keep the date"

    // ── G · You ended it ────────────────────────────────────────────────────
    const val G_HEADLINE_LEAD = "You "
    const val G_HEADLINE_ITALIC = "ended"
    const val G_HEADLINE_REST = " the date"
    const val G_BODY = "We hope you and %s found another day to meet. Search is open again — go and find your next date."
    const val G_ROW = "You told %s yourself, so your Show-up Rate stays untouched. There's no review, as the date didn't happen."
    const val FIND_NEXT = "Find your next date"

    // ── H · The match ───────────────────────────────────────────────────────
    const val H_HEADLINE_LEAD = "%s had to "
    const val H_HEADLINE_ITALIC = "cancel"
    const val H_HEADLINE_REST = " the date"
    const val H_BODY = "We hope you two found another day to meet. Search is open again — go and find your next date."
    const val H_ROW = "Your Show-up Rate stays untouched. There's no review, as the date didn't happen."
    val OUTCOME_BOLD = listOf("Search is open again", "Show-up Rate stays untouched.")

    // ── the reference's, not the ticket's copy section ──────────────────────
    //
    // The read receipt under my bubbles (`ChatBubble`: "Read" / "Delivered").
    const val READ = "Read"
    const val DELIVERED = "Delivered"

    // ── spoken only: what TalkBack names the two icon buttons ───────────────
    const val BACK = "Back"
    const val SEND = "Send"

    // ── the three failures, I, J and K (9 Nov 2026) ─────────────────────────
    //
    // Search's `Try again` beside the first.
    const val LOAD_FAILED = "Couldn't load your chat. Check your connection and try again."
    const val SEND_FAILED = "Couldn't send. Check your connection and try again."
    const val END_FAILED = "Couldn't end the date. Check your connection and try again."
}

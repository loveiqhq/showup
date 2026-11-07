// ChatCopy.swift
// ShowUp · every string the coordinate chat draws (SHOWUP-194)
//
// From the ticket's "Copy — final strings", verbatim; `audit/verify-chat.py` quotes the ticket at run time
// against this file and its Kotlin twin (`ChatCopy.kt`). The chips' labels and drafts are the
// vocabulary's (`ChatReason`), not repeated here. A headline whose word is italic is split into its runs;
// a paragraph with bold runs lists them beside it.

import Foundation

enum ChatCopy {
    // MARK: A · Tutorial
    static func tutorialHeader(_ name: String) -> String { "Coordinate with \(name)" }
    static let tutorialHeadlineItalic = "Must read"
    static let tutorialHeadlineRest = " — how messaging works"
    static let tutorialIntro =
        "How you use the chat is a major part of how we can determine if one of you later says the other " +
        "never showed while they insist they did."
    static let tutorialIntroBold = [
        "How you use the chat",
        "determine if one of you later says the other never showed while they insist they did.",
    ]
    static let step1Title = "Use our action chips"
    static let step1Body =
        "Make use of our action chips whenever possible. It allows us to analyze the situation of your date " +
        "and understand who played real and who didn't."
    static let step1Bold = ["Make use", "action chips", "understand who played real and who didn't."]
    static let examples = "Examples:"
    static let exampleArrived = "I arrived"
    static let exampleLate = "Running late"
    static let step2Title = "We never read your words"
    static let step2Body =
        "What you type stays private between you and your date. All we can see is which signal you picked " +
        "and whether a message was sent and replied to — never the message itself."
    static let step2Bold = [
        "What you type stays private between",
        "which signal you picked",
        "whether a message was sent and replied to",
    ]
    static let openTheChat = "Open the chat"
    static let tutorialPrivate = "Your messages are private. Only the signals are logged."

    // MARK: Chat header
    static let dateOn = "DATE ON"
    static let today = "Today"
    static let tomorrow = "Tomorrow"
    /// The header's countdown: `in {h}h {m}m`, `in 0h 45m` under an hour.
    static func countdown(hours: Int, minutes: Int) -> String { "in \(hours)h \(minutes)m" }

    // MARK: B · Start
    static let startHeadlineLead = "Start with a "
    static let startHeadlineItalic = "reason"
    static let startBody1 = "Tap one that fits best to start the chat."
    static let startBody2 =
        "Make use of our action chips as much as possible to allow us to understand who played real and who didn't."
    static let startPrivacy = "We log which reason you pick — never what you type."

    // MARK: C · Field
    static let placeholder = "Type a message, or tap a chip below"

    // MARK: D · Cancel guide
    static let guideTitle = "Life happens — handle it with respect"
    static func guide1(_ name: String) -> String {
        "Show Up is for reliable, committed people who want to meet in real life, quickly. But sometimes life " +
        "gets in the way. If there's really no way you can make it, explain why and say sorry. \(name) has freed " +
        "up time for you and is looking forward to meeting you."
    }
    static let guide1Bold = ["explain why and say sorry"]
    static let guide2 =
        "Better still: move it to later today or to another place instead of cancelling. A different day can't " +
        "be set in the app — agree on one here, then one of you ends the date so you can both search again."
    static let guide2Bold = ["move it to later today or to another place"]
    static let gotIt = "Got it"
    static let placeholderLocked = "Read the note above, then tap Got it"
    static let guideAcked = "Explain why, say sorry — in your own words."
    static func placeholderSorry(_ name: String) -> String { "Explain why and say sorry to \(name)…" }

    // MARK: E · Card
    static let cardLead = "Not meeting today?"
    static let cardBody = "Once you've talked it through, one of you ends the date so you can both search again."
    static let endDate = "End date"

    // MARK: F · Sheet
    static func sheetTitle(_ name: String) -> String { "End the date with \(name)?" }
    static let sheetBody = "The date won't happen, so there's no review. You can both search again straight away."
    static let keepTheDate = "Keep the date"

    // MARK: G · You ended it
    static let gHeadlineLead = "You "
    static let gHeadlineItalic = "ended"
    static let gHeadlineRest = " the date"
    static func gBody(_ name: String) -> String {
        "We hope you and \(name) found another day to meet. Search is open again — go and find your next date."
    }
    static func gRow(_ name: String) -> String {
        "You told \(name) yourself, so your Show-up Rate stays untouched. There's no review, as the date didn't happen."
    }
    static let findNext = "Find your next date"

    // MARK: H · The match
    static func hHeadlineLead(_ name: String) -> String { "\(name) had to " }
    static let hHeadlineItalic = "cancel"
    static let hHeadlineRest = " the date"
    static let hBody = "We hope you two found another day to meet. Search is open again — go and find your next date."
    static let hRow = "Your Show-up Rate stays untouched. There's no review, as the date didn't happen."
    static let outcomeBold = ["Search is open again", "Show-up Rate stays untouched."]

    // MARK: the reference's, not the ticket's copy section
    //
    // The read receipt under my bubbles (`ChatBubble`: "Read" / "Delivered").
    static let read = "Read"
    static let delivered = "Delivered"

    // MARK: spoken only: what VoiceOver names the two icon buttons
    static let back = "Back"
    static let send = "Send"

    // MARK: not in the ticket: PROPOSED, for Philipp
    //
    // Every screen has a failed state (CLAUDE.md) and the ticket draws none. Search's approved sentence, said
    // of the chat, as the Date screen says it of the date; Search's `Try again` beside the first.
    static let loadFailed = "Couldn't load your chat. Check your connection and try again."
    static let sendFailed = "Couldn't send. Check your connection and try again."
    static let endFailed = "Couldn't end the date. Check your connection and try again."
}

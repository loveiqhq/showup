// DateEndCopy.swift
// ShowUp · Date 06's strings, exactly the ticket's (SHOWUP-197). Android's `DateEndCopy.kt`.
//
// Templated strings are functions of the match's first name (or the binding meet time where the name says so). The
// apostrophes are the ticket's own, straight in some strings and typographic in others; they are not normalised. The
// bold runs the reference draws (`<b>`, `<strong>`) are split into pieces so a screen can style them without
// re-typing a word.

import Foundation

enum DateEndCopy {

    // A: the End Date bar
    static func barTitle(_ name: String) -> String { "Your date with \(name)" }
    static let barLine = "Tap End Date - when your date is finished."
    static let barButton = "End Date"

    // B: How did it go?
    static func wrapTitle(_ name: String) -> String { "How did it go with \(name)?" }
    static let metTitle = "We met — date is finished"
    static let metSub = "Only once you’ve actually met in person."
    static let noShowTitle = "My date isn't showing up"
    static let noShowSub = "They're not here yet — here's what to do and how the wait works."
    static let cancel = "Cancel"

    // C: We met
    static let cTitle = "Thanks for showing up"
    static func cBody1(_ name: String) -> String { "You are wonderful for showing up to \(name)." }
    static let cBody2 = "We hope you had a great time together."
    static let cStep1 = "Please review your date now."
    static let cStep2 = "Search for new dates resumes after the review."
    static let cNote1 = "This is only for dates that happened. Need to call it off "
    static let cNoteBold1 = "before"
    static let cNote2 = " meeting? Use "
    static let cNoteBold2 = "Cancel date"
    static let cNote3 = " in the chat menu. Ending a date you didn’t attend counts as a no-show and lowers your Show-up Rate."
    static let cCta = "End Date → Review"

    // What happens next
    static let whatNext = "What happens next"

    // E / F: Not here yet?
    static let back = "Back"
    static let guideTitle = "Not here yet?"
    static func guide1Bold(_ name: String) -> String { "Message \(name)" }
    static let guide1 = " to ask where they are. A quick “on my way?” clears up most late arrivals."
    static let guide2A = "Please give it at least "
    static let guide2Bold = "10 minutes"
    static let guide2B = ". Something can always come up."
    static let guide3 = "Please check you are at the right location."
    static let guide4A = "If you get no reply and you’re "
    static let guide4Bold = "10 minutes"
    static let guide4B = " past your meet time — you’re free to end the date and leave."
    static func message(_ name: String) -> String { "Message \(name)" }
    static func endLocked(_ time: String) -> String { "You can end the date from \(time)" }
    static let endOpen = "It’s been 10+ minutes — end date"

    // G: Thanks for waiting
    static let gTitle = "Thanks for waiting"
    static func gBodyA(_ name: String) -> String { "You gave \(name) time. " }
    static let gBodyBold = "Leaving now won’t lower your Show-up Rate."
    static let gStep1 = "Please give us a little more information in the review now - helps us to determine the consequences for your date partner."
    static let gStep2 = "No penalty to you — your Show-up Rate stays where it is."
    static let gStep3 = "Searching for a new date will resume."
    static let gCta = "End date and review"

    // H: Report a problem
    static let reportCardTitle = "Report a problem"
    static let reportCardLine = "If you feel unsafe or uncomfortable, let us know."
    static let hTitle = "Report a problem"
    static let hLine = "Everything here is confidential. We've got you."
    static let uncomfortableTitle = "I don't feel comfortable"
    static let uncomfortableSub = "If you don't feel comfortable in the situation - head out now. That's ok!"

    // I: I don't feel comfortable
    static let iTitle = "We're sorry to hear that."
    static let iBodyA = "You don't have to explain a thing — you showed up, so "
    static let iBodyBold1 = "you're free to head out now,"
    static let iBodyB = " with "
    static let iBodyBold2 = "no penalty to your Show-up Rate."
    static let iStep1 = "No penalty to you — you showed up, and that's what counts."
    static let iStep2 = "Head out whenever you're ready — no need to say goodbye."
    static let iStep3 = "Review your date next. What you tell us stays confidential."
    static let headOut = "Head out now"
    static let stay = "Stay for now"

    // J: the end failed
    static let endFailed = "Couldn't end the date. Check your connection and try again."
}

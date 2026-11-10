// DateCopy.swift
// ShowUp · every string the Date screen draws (SHOWUP-193)
//
// From the ticket's copy section, verbatim; `audit/verify-date.py` quotes the ticket at run time
// against this file and its Kotlin twin, and checks the two hold the same strings. A value is filled
// by a function here, never assembled at a call site.
//
// No footer bar strings: the bars are Date 03's and Date 06's, which write them (9 Nov 2026).

import Foundation

enum DateCopy {
    static let hero = "It's a date."

    // MARK: time + place
    static func meetingLead(_ name: String) -> String { "You and \(name) are meeting " }
    static let meetingTime = "Meeting time:"
    static let meetingPlace = "Meeting place:"
    static let copy = "Copy"
    static let copied = "Copied"

    // MARK: Coordinate
    static func coordTitle(_ name: String) -> String { "Coordinate with \(name)" }
    static let coordLockedSub = "Sort last-minute details before you meet"
    static func coordLockedLine(_ time: String) -> String { "Messaging available from \(time)" }
    static func coordLockedLineTomorrow(_ time: String) -> String { "Messaging available tomorrow from \(time)" }
    /// The locked card's change line (5 Nov 2026), over the two tiles; shown with them, once Date 05 ships.
    static let coordChangeLine = "Request to change the location or meet time."
    static func coordOpenSub(_ name: String) -> String { "Running late / Can't find your date at the spot? Tell \(name) here!" }
    static let openChat = "Open chat"
    static let unreadOne = "New message"
    static func unreadMany(_ n: Int) -> String { "\(n) new" }
    static func needsResponse(_ time: String) -> String { "needs a response · \(time)" }
    static let now = "now"
    static let readAndReply = "Read & reply"

    // MARK: the rest of the column
    static let reportTitle = "Report a problem"
    static let reportBody = "If you feel unsafe or uncomfortable, let us know."
    static let mutualIntent = "Mutual intent"
    static func theirShowUp(_ name: String) -> String { "\(name)'s Show-up" }
    static let youBothWant = "You both want"
    static let directions = "Directions"
    static let shareDate = "Share Date"
    static let calendar = "Calendar"
    static func nameAge(_ name: String, _ age: Int) -> String { "\(name), \(age)" }
    static func seeProfile(_ name: String) -> String { "See \(name)'s full profile" }
    static let inCommon = "What you have in common"
    static let cancelDate = "Cancel this date"
    static let tabHint = "Available again once your date is finished."

    // MARK: How the date works
    static let worksTitle = "How the date works"
    static func worksMeet(_ place: String, _ time: String) -> String { "Meet at \(place) at \(time)" }
    static func worksMeetNoPlace(_ time: String) -> String { "Meet at \(time)" }
    static let worksMeetSub = "A public place — a café, bar or restaurant."
    static let worksCheckIn = "Check in when you arrive"
    static func worksCheckInSub(_ name: String) -> String { "Tap Check-in at the spot — we let \(name) know you're there." }
    static let worksThirty = "30 minutes, no pressure"
    static let worksThirtySub = "Stay if you're vibing, leave with a smile."
    static let worksEnd = "End the date in the app"
    static let worksEndSub = "Tap End date when you’re done — then a short review."

    // MARK: the OS hand-offs
    static func shareText(_ name: String, _ day: String, _ time: String, _ place: String) -> String {
        "I'm meeting \(name) \(day) at \(time) at \(place)."
    }
    static func shareTextNoPlace(_ name: String, _ day: String, _ time: String) -> String {
        "I'm meeting \(name) \(day) at \(time)."
    }
    static func calendarTitle(_ name: String) -> String { "Date with \(name)" }

    // MARK: 9 Nov 2026
    /// The first read failed, with nothing to show; beside Search's `Try again`.
    static let loadFailed = "Couldn't load your date. Check your connection and try again."

    /// The read-only profile's back pill.
    static let profileBack = "Your date"
}

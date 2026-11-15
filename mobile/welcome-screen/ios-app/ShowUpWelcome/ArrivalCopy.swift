// ArrivalCopy.swift
// ShowUp · the arrival check-in's strings, exactly the ticket's (SHOWUP-195). Android's `ArrivalCopy.kt`.
//
// A name is filled by a function, never assembled at a call site. `audit/verify-arrival.py` compares these with
// the ticket.

import Foundation

enum ArrivalCopy {
    // A: the Check-in bar
    static func barTitle(_ name: String) -> String { "Your date with \(name)" }
    static let barLine = "Confirm you arrived at the meeting location, we will let your date know."
    static let barButton = "Check-in"
    static let endDate = "End date"

    // B: the confirmation
    static func confirmTitle(_ name: String) -> String {
        "Have you arrived at the meeting location for your date with \(name)?"
    }
    static let notYet = "Not yet"
    static let yes = "Yes, I've arrived"

    // C: the location could not be read
    static let locError = "We couldn't find your location. Check your signal, then try again."
    static let tryAgain = "Try again"

    // D: stored
    static let doneTitle = "You are amazing for showing up!"
    static func doneBody(_ name: String) -> String { "We will let \(name) know that you have arrived. Enjoy your date." }
    static let close = "Close"
    static let undo = "Undo check-in"

    // H: the match has arrived
    static let arrivedTag = "Arrived"
    static func arrivedTime(_ time: String) -> String { "checked in · \(time)" }
    static func arrivedBody(_ name: String) -> String { "\(name) has arrived at the meeting location." }
    static let openChat = "Open chat"

    /// Built for Date 06's report flow, shown by no screen yet.
    static let reportNote = "Check in first, so we can confirm you were there."
}

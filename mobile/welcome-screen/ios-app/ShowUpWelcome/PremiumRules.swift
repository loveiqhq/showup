//
//  PremiumRules.swift
//  ShowUp · the Premium grid (SHOWUP-185) and the Likes tab (SHOWUP-186): their rules and copy, no UI
//
//  The Swift twin of `search/PremiumRules.kt`. The cards, the closed reasons, the sorry sheet, the undo
//  toast and the fly-out's geometry, as plain values and functions tested on their own
//  (`PremiumRulesTests`). Everything Search 01 already decides -- the distance, the name line, the
//  meet time, the Show-up count -- is read from `SearchRules.swift`, never copied.
//
//  Copy is verbatim from the tickets (`search/tickets/02-grid.md`, `03-likes-received.md`), quoted
//  back by `audit/verify-premium.py`.
//

import CoreGraphics
import Foundation

/// The two tickets' copy, one constant per string, so the verifier can quote each against them.
enum PremiumCopy {
    /// The top bar's label: "its own label, always that word" (drawn in capitals, read as written).
    static let premiumTag = "Premium"
    static let likedYou = "Liked you"

    // The cards' accessible names. A mark is part of the name, never colour only.
    static func cardLabel(_ name: String) -> String { "Open \(name)'s profile" }
    static func cardLabelLiked(_ name: String) -> String { "Open \(name)'s profile, liked" }
    static func cardLabelNotNow(_ name: String) -> String { "Open \(name)'s profile, not now" }

    /// The marked label: `Not now`; `Not interested` shows only during the 150 ms hold.
    static let notNowLabel = "Not now"
    static let notInterestedLabel = "Not interested"

    // The profile, opened from a list.
    static let allProfiles = "All profiles"
    static let allLikes = "All likes"
    static let confirmDate = "Confirm date"

    /// Be sure in mutual mode: `Confirm, and you and {name} have a confirmed, binding date {day}.`
    static func beSureMutualLead(_ name: String) -> String { "Confirm, and you and \(name) have a " }

    // The undo toast (Search 02 G, Search 03 D).
    static func undoToast(_ name: String) -> String { "\(name)'s like declined." }
    static let undo = "Undo"

    // The sorry sheet (Search 02 18).
    static let sorryLine = "There are still people ready to meet you. Your date could be one of them."
    static let findSomeoneElse = "Find someone else"
    static let backToLikes = "Back to likes"

    // The Likes tab (Search 03).
    static let likesIntroLead = "People who want to "
    static let likesIntroEmphasis = "meet you"
    static let likesIntroBody = "Open a profile, then decide. Confirm and you two have a binding date."
    static let viewProfile = "View profile"
    static let emptyLead = "No likes "
    static let emptyEmphasis = "yet"
    static let emptyBody = "When someone likes you, they show up here — with their message, if they wrote one."
    static let backToSearch = "Back to search"

    // States the tickets do not draw.
    //
    // PROPOSED, NOT TICKETED: a list that could not be read. The words follow Search 01's own proposal
    // and go to Philipp with it; the verifier lists them as proposed.
    static let likesLoadingLabel = "Loading likes"
    static let likesLoadFailedProposed = "We couldn't load your likes just now. Please try again."
}

/// Why a like closed without a decision (§36 `like_close_reason`), and every line each reason is drawn
/// with: the grid's closed card, the Likes list's closed card, and the sorry sheet.
enum CloseReason: String, CaseIterable, Equatable, Sendable, Codable {
    case matched
    case noOverlap = "no_overlap"

    var wire: String { rawValue }

    /// The pill: on the closed grid card and on the sorry sheet's photo.
    var pill: String {
        switch self {
        case .matched: return "Too late"
        case .noOverlap: return "Out of time"
        }
    }

    /// The grid card's line, in place of the meet time and motivation.
    var gridLine: String {
        switch self {
        case .matched: return "Has a date with someone else."
        case .noOverlap: return "No time left to meet."
        }
    }

    /// The Likes card's first line. No gendered pronoun, in either reason.
    func likesHead(_ name: String) -> String {
        switch self {
        case .matched: return "Too late — \(name) has a date with someone else."
        case .noOverlap: return "Out of time — you and \(name) have no time left to meet."
        }
    }

    /// The Likes card's second line.
    var likesBody: String {
        switch self {
        case .matched: return "A like closes as soon as either of you matches. Confirm sooner next time."
        case .noOverlap: return "A like closes when your Check-Ins no longer overlap. Confirm sooner next time."
        }
    }

    /// The closed grid card's accessible name: `{name}. Too late — {name} has a date with someone else.`
    func gridLabel(_ name: String) -> String { "\(name). \(likesHead(name))" }

    /// The sorry headline before its italic run.
    func sorryHeadLead(_ name: String) -> String {
        switch self {
        case .matched: return "We're really sorry, \(name) confirmed a date "
        case .noOverlap: return "Sorry, you and \(name) "
        }
    }

    /// The sorry headline's italic, washed run.
    var sorryEmphasis: String {
        switch self {
        case .matched: return "just before you did."
        case .noOverlap: return "ran out of time."
        }
    }

    func sorryBody(_ name: String) -> String {
        switch self {
        case .matched:
            return "A like closes the moment either of you matches, and this time \(name)'s match came first."
        case .noOverlap:
            return "We're sorry this one didn't work out. Your Check-In and \(name)'s no longer share any time, so the like has closed."
        }
    }

    /// A reason this app does not know (a newer server) is no reason: nothing changes.
    static func of(_ wire: String?) -> CloseReason? {
        guard let wire else { return nil }
        return CloseReason(rawValue: wire)
    }
}

/// What the 150 ms hold before a fly-out shows: the filled heart, or the `Not interested` label.
enum Leave: Equatable, Sendable { case liked, notInterested }

/// A grid card's accessible name, mark included (Search 02: "a marked card announces its mark as part
/// of its name; the mark is never colour-only"). The closed card is not a control and has its own.
func gridCardLabel(_ name: String, notNow: Bool, leaving: Leave?) -> String {
    switch leaving {
    case .liked: return PremiumCopy.cardLabelLiked(name)
    case .notInterested: return "\(PremiumCopy.cardLabel(name)), \(PremiumCopy.notInterestedLabel.lowercased())"
    case nil: return notNow ? PremiumCopy.cardLabelNotNow(name) : PremiumCopy.cardLabel(name)
    }
}

/// The Likes card's photo: `Open {name}'s profile`, or `…, not now` once labelled.
func likesPhotoLabel(_ name: String, notNow: Bool) -> String {
    notNow ? PremiumCopy.cardLabelNotNow(name) : PremiumCopy.cardLabel(name)
}

/// The grid card's meta line: `{HH:MM} {day} · ~{n} km` -- on one line at 375.
func gridMetaLine(_ profile: SearchProfile, zone: TimeZone) -> String {
    "\(meetLine(profile.meetAt, day: profile.meetDay, zone: zone)) · \(searchDistance(profile.distanceKm))"
}

/// The Likes card's distance, after the name: `· < 1 km` / `· ~{n} km`.
func likesDistance(_ km: Int) -> String { "· \(searchDistance(km))" }

/// The liker's message, "in straight double quotes, as written".
func quotedMessage(_ message: String) -> String { "\"\(message)\"" }

/// The undo toast's line.
func undoLine(_ name: String) -> String { PremiumCopy.undoToast(name) }

/// Where in the visible list the card lands: 72% of its height.
let flyOutDrop: CGFloat = 0.72

/// How small the card is when it disappears.
let flyOutScale: CGFloat = 0.12

/// The fly-out's destination, from the card's centre: the visible list's centre x and 72% of its height
/// (Search 02 20). Both boxes in the same coordinates; the result is the translation to apply.
func flyOutTarget(card: CGRect, view: CGRect) -> CGSize {
    CGSize(width: view.minX + view.width / 2 - card.midX,
           height: view.minY + view.height * flyOutDrop - card.midY)
}

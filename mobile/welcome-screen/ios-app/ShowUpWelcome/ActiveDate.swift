// ActiveDate.swift
// ShowUp · the Date screen's data, in the app's own types (SHOWUP-193)
//
// Read from `GET /me/date` by `DateRepository`. The screen takes these and returns pixels; nothing
// here knows about HTTP or the generated client. Android's `ActiveDate.kt`, field for field.

import Foundation

/// The agreed meeting place. `address` is nil for a venue stored without one.
struct DatePlace: Equatable, Sendable {
    var name: String
    var address: String?
}

/// The person the viewer is meeting. A hidden age, an unknown district or distance and a missing photo
/// are nil: no value, no text on the screen.
struct DateMatch: Equatable, Sendable {
    var userId: String
    var name: String
    var age: Int?
    var district: String?
    /// Whole km rounded by the server; 0 is "< 1 km".
    var distanceKm: Int?
    /// A loadable URL.
    var photoUrl: URL?
    var attendedCount: Int
}

/// The newest unread message from the match. `reason` is its chip, nil for a message sent with none.
struct DateUnread: Equatable, Sendable {
    var count: Int
    var reason: ChatReason?
    var body: String?
    var sentAt: Date
}

/// Everything the Date screen renders. Time and place are the latest AGREED values.
struct ActiveDate: Equatable, Sendable {
    var id: String
    var meetAt: Date
    /// `today` or `tomorrow`, by the Check-In's 7am day, from the server -- as Search's.
    var meetDay: String
    /// meetAt − 1:30: when Coordinate opens.
    var unlockAt: Date
    var coordinateOpen: Bool
    var place: DatePlace?
    var match: DateMatch
    var motivationYou: String?
    var motivationThem: String?
    /// §31 vibes both chose, in the match's order.
    var sharedVibes: [String]
    var sharedInterests: [String]
    var unread: DateUnread?
    /// The viewer's own arrival check-in. Always false until Date 03.
    var checkedIn: Bool
    /// The viewer has opened the chat past its tutorial on this date (Date 02); false shows the tutorial.
    var chatTutorialSeen: Bool
}

/// A date ended in the chat after "I can't make it today" (Date 02) that the viewer has not seen yet: the
/// other person's first name and whether the viewer ended it. Shown once; after it is marked seen the read
/// is `.none`.
struct EndedInChat: Hashable, Sendable {
    var dateId: String
    var name: String
    var endedByYou: Bool
}

/// What `GET /me/date` found: a date, an ended-in-chat outcome to show, or neither. A failed call is the
/// `Result`'s.
enum DateRead: Equatable, Sendable {
    case active(ActiveDate)
    case ended(EndedInChat)
    case none
}

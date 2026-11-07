// DateSamples.swift
// ShowUp · the spec sheet's date, as values (SHOWUP-193)
//
// Leonie at Cafe Krone, 19:00 Berlin, on 3 Nov 2026 -- what every frame of the sheet shows. For the
// previews, the evidence and the developer menu; the tests keep their own, so a sample edited for a
// picture never changes what a test asserts. Android's `DateSamples.kt`.

import Foundation

enum DateSamples {
    static let zone = TimeZone(identifier: "Europe/Berlin") ?? .current
    static let meet = Date(timeIntervalSince1970: 1_793_728_800) // 2026-11-03T18:00:00Z
    static let unlock = meet.addingTimeInterval(-90 * 60)

    /// The sheet's clocks: 15:55 in A, 17:42 in B and C.
    static let aNow = meet.addingTimeInterval(-(3 * 3600 + 5 * 60))
    static let bNow = meet.addingTimeInterval(-78 * 60)

    static let date = ActiveDate(
        id: "sample-date", meetAt: meet, meetDay: "today", unlockAt: unlock, coordinateOpen: false,
        place: DatePlace(name: "Cafe Krone", address: "Oranienstrasse 42, Kreuzberg"),
        match: DateMatch(userId: "sample-leonie", name: "Leonie", age: 28, district: "Neukölln",
                         distanceKm: 1, photoUrl: nil, attendedCount: 4),
        motivationYou: "long_term", motivationThem: "long_term",
        sharedVibes: ["coffee_chat", "nature_walk", "playful", "deep_talk"],
        sharedInterests: ["reading", "brunch", "hiking"],
        unread: nil, checkedIn: false, chatTutorialSeen: false)

    /// C: the sheet's running-late message, sent at 17:42.
    static let unread = DateUnread(count: 1, reason: .runningLate,
                                   body: "Running about 10 min late — sorry! Almost there.", sentAt: bNow)
}

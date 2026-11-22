// PartnerEndedRules.swift
// ShowUp · Date 08: which payload each `PartnerScreen` draws, every number that differs between them, the words each
// one reads, and where the screen sits in the app's stack (SHOWUP-199). Android's `PartnerEndedRules.kt`, with the
// routing helpers of its `ChatRouting.kt` that are Date 08's alone.
//
// The three frames share one layout; only the payload's values change (spec §4). The tests pin them so a payload cannot
// drift into another's layout. The headline and the sub are built here, not in the view, because a SwiftUI test cannot
// read text back out of a hosted view: what the tests compare with the ticket is what the screen draws.

import CoreGraphics
import Foundation

enum HeroTone: Equatable, Sendable { case blush, lilac }

/// The row accent: orange for the two accountability frames, violet for the thank-you.
enum RowAccent: Equatable, Sendable { case orange, violet }

/// What stays equal across the three payloads.
enum PartnerEndedLayout {
    static let heroRadius: CGFloat = 28
    static let avatarSize: CGFloat = 76
    static let badgeSize: CGFloat = 30
    static let tileSize: CGFloat = 36
    static let tileRadius: CGFloat = 12
    static let rowGap: CGFloat = 10
}

struct PartnerEndedPayload: Equatable {
    let tone: HeroTone
    let heroPadH: CGFloat
    let heroPadV: CGFloat
    let avatarGap: CGFloat
    /// A's photo, or (C has none) the white disc with the violet heart.
    let usesPhoto: Bool
    let badgeIcon: BrandIcon
    let badgeIconSize: CGFloat
    let badgeStroke: CGFloat
    /// The `liqSuccessFg` badge when true, orange when false.
    let badgeSuccess: Bool
    let rowsTop: CGFloat
    let rowIcons: [BrandIcon]
    let rows: [[Run]]
    let accent: RowAccent
}

func partnerEndedPayload(_ screen: PartnerScreen) -> PartnerEndedPayload {
    switch screen {
    case .youNoShowed:
        return PartnerEndedPayload(
            tone: .blush, heroPadH: 24, heroPadV: 30, avatarGap: 18, usesPhoto: true,
            badgeIcon: .clock, badgeIconSize: 16, badgeStroke: 2.6, badgeSuccess: false,
            rowsTop: 18, rowIcons: [.userX, .messageCircle, .shieldCheck],
            rows: PartnerEndedCopy.aRows, accent: .orange)
    case .dateEndedDiscomfort:
        return PartnerEndedPayload(
            tone: .blush, heroPadH: 22, heroPadV: 28, avatarGap: 16, usesPhoto: true,
            badgeIcon: .hand, badgeIconSize: 15, badgeStroke: 2.6, badgeSuccess: false,
            rowsTop: 16, rowIcons: [.hand, .messageCircle, .shieldCheck],
            rows: PartnerEndedCopy.bRows, accent: .orange)
    case .dateClosedPositive:
        return PartnerEndedPayload(
            tone: .lilac, heroPadH: 24, heroPadV: 30, avatarGap: 18, usesPhoto: false,
            badgeIcon: .check, badgeIconSize: 15, badgeStroke: 3, badgeSuccess: true,
            rowsTop: 18, rowIcons: [.heart, .search],
            rows: PartnerEndedCopy.cRows, accent: .violet)
    }
}

/// The headline's runs, `(text, isTheWashedRun)`, for `WashHeadline`.
func partnerEndedHeadline(_ screen: PartnerScreen, name: String) -> [(String, Bool)] {
    switch screen {
    case .youNoShowed:
        return [(PartnerEndedCopy.aHeadlineLead(name), false), (PartnerEndedCopy.aHeadlineItalic, true)]
    case .dateEndedDiscomfort:
        return [(PartnerEndedCopy.bHeadlineLead(name), false), (PartnerEndedCopy.bHeadlineItalic, true),
                (PartnerEndedCopy.bHeadlineRest, false)]
    case .dateClosedPositive:
        return [(PartnerEndedCopy.cHeadlineLead(name), false), (PartnerEndedCopy.cHeadlineItalic, true)]
    }
}

/// The line under the headline, its `<strong>` runs bold. Only A's carries one.
func partnerEndedSub(_ screen: PartnerScreen, name: String) -> [Run] {
    switch screen {
    case .youNoShowed: return PartnerEndedCopy.aSub(name)
    case .dateEndedDiscomfort: return [Run(text: PartnerEndedCopy.bSub)]
    case .dateClosedPositive: return [Run(text: PartnerEndedCopy.cSub)]
    }
}

/// The §11 screen each payload reports its `screen_viewed` as.
func partnerEndedScreenID(_ screen: PartnerScreen) -> ProfileScreen {
    switch screen {
    case .youNoShowed: return .datePartnerEndedNoShow
    case .dateEndedDiscomfort: return .datePartnerEndedUncomfortable
    case .dateClosedPositive: return .datePartnerEndedMet
    }
}

/// Date 08, for the match of a Date 06 end: everything it draws rides in the route, so a relaunch draws the same screen.
/// The outcome's own photo first, as H's: at the next launch no date is held.
func partnerEndedRoute(_ outcome: EndedOutcome, screen: PartnerScreen, photoURL: URL?) -> AppRoute {
    .partnerEnded(dateId: outcome.dateId, name: outcome.name, screen: screen, photoURL: outcome.photoUrl ?? photoURL)
}

/// The stack once Date 08 is up: Date 08 alone over the flow. The chat, a Date 05 picker, G or H leave it, so no back
/// can ever reopen them (Android pops to the flow the same way).
func pathAfterPartnerEnd(_ path: [AppRoute], showing route: AppRoute) -> [AppRoute] { [route] }

/// Whether a back -- the edge swipe, or the system shortening the path -- may leave `top`. Never Date 08 (R6): the date
/// is over and the person owes a review, so there is nothing to go back to.
func canGoBack(from top: AppRoute?) -> Bool {
    if case .partnerEnded? = top { return false }
    return true
}

/// Where the person was when Date 08 opened, for `referrer_screen_id` (R7). `from` is the chat when the chat found the
/// end. Otherwise it was a read of the Date screen: `heldDate` is what that screen was showing, and a read that finds an
/// end leaves it as it was. None held means this read is the launch's first and nothing was on screen yet. Android's
/// `partnerEndedReferrer`.
func partnerEndedReferrer(heldDate: ActiveDate?, from: ProfileScreen?) -> ProfileScreen? {
    if let from { return from }
    return heldDate == nil ? nil : ProfileScreen.dateScreen
}

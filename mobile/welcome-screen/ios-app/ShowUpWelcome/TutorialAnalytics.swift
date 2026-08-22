//  TutorialAnalytics.swift
//  ShowUp · analytics seam for the tutorial flow
//
//  A seam, not an integration. Real code rather than a commented-out reminder: a comment claiming
//  something is tracked is indistinguishable from tracking that works, and this project has already
//  been bitten by that more than once.
//
//  The default does nothing, and will keep doing nothing until there is somewhere to send events —
//  the backend's analytics module has no ingest endpoint yet, and consent capture is not built, so
//  AnalyticsService.track() would discard anything sent today. When both land, inject a real
//  implementation and no screen needs to change.

import Foundation

public protocol AnalyticsTracking {
    func track(_ event: String, properties: [String: Any])
}

public struct NoOpAnalytics: AnalyticsTracking {
    public init() {}
    public func track(_ event: String, properties: [String: Any]) {}
}

/// Names and properties for the tutorial flow.
///
/// "Tutorial", deliberately not "onboarding" — onboarding is the separate flow where someone fills
/// in their profile. Conflating them makes the funnel unreadable later.
///
/// Names follow the backend taxonomy: snake_case, `<noun>_<verb-ed>`, snake_case properties, no free
/// text and no personal data. The card number is a property rather than part of the event name, so
/// all six screens share these two events instead of inventing twelve — and "where do people drop
/// out of the tutorial?" stays one query grouped by `card`.
public enum TutorialAnalytics {
    public static let cardViewed = "tutorial_card_viewed"
    public static let ctaTapped  = "tutorial_cta_tapped"

    private static func props(_ card: Int, _ name: String) -> [String: Any] {
        ["card": card, "card_name": name]
    }

    /// Card 1. The spec sheet is `00-welcome-spec-sheet`, but the ticket's tracking section names
    /// this screen "Tutorial 1 - Welcome", so it is card 1.
    public static let welcome = props(1, "welcome")

    /// Card 2 — SHOWUP-135, "Tutorial 2 - Meet". Screen 1 of the 5-segment tour.
    public static let meet = props(2, "meet")
}

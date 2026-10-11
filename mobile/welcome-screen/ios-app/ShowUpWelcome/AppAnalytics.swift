//  AppAnalytics.swift
//  ShowUp · the one tracker every screen reports through
//
//  Android twin: `analytics/AppAnalytics.kt`.

import Foundation
import Observation

/// THE app's tracker: every screen and model reports through this one value.
///
/// WHY ONE, AND WHY NOW
///
/// Every screen already builds its events, and the unit tests check them against each ticket's
/// table. But until 8 October 2026 no screen was handed a tracker — each defaulted to reporting
/// nowhere — so choosing an analytics tool would have meant wiring every call site by hand, and
/// nothing anyone could look at showed whether a single event fired. Now the host hands this to
/// every one of them, and two things follow:
///
/// - connecting a tool is ONE line: `destination`.
/// - in a debug build every event also lands in `EventLog`, which the developer menu shows live —
///   the "analytics debug log for one full pass" SHOWUP-163 asks for as evidence.
struct AppAnalytics: AnalyticsTracking, Sendable {
    static let shared = AppAnalytics()

    /// Where events really go. Nowhere yet: no analytics tool has been chosen (Epic 11), and paid
    /// providers are on hold. Connecting one is replacing this line, and nothing else.
    private let destination = NoOpAnalytics()

    func track(_ event: String, properties: [String: any Sendable]) {
        #if DEBUG
        // Every reporter today is on the main actor; the hop covers any that one day is not,
        // without an unchecked conformance.
        if Thread.isMainThread {
            MainActor.assumeIsolated { EventLog.shared.record(event, properties: properties) }
        } else {
            Task { @MainActor in EventLog.shared.record(event, properties: properties) }
        }
        #endif
        destination.track(event, properties: properties)
    }
}

#if DEBUG
/// The live event log the developer menu reads. Debug builds only: it does not exist in a release
/// build at all.
///
/// In memory only, newest first, capped at `capacity` so a long session cannot grow it without
/// bound. Nothing is printed or written to disk: the events carry no personal data by design
/// (events.json), and the menu still withholds any value whose key is on the prohibited list.
@MainActor
@Observable
final class EventLog {
    static let shared = EventLog()

    struct Entry: Identifiable {
        /// Increasing per log, so a list can key its rows on it.
        let id: Int
        let at: Date
        let event: String
        let properties: [String: any Sendable]
    }

    /// Newest first.
    private(set) var entries: [Entry] = []
    private var nextId = 0
    private let capacity: Int
    private let clock: () -> Date

    init(capacity: Int = 300, clock: @escaping () -> Date = Date.init) {
        self.capacity = capacity
        self.clock = clock
    }

    func record(_ event: String, properties: [String: any Sendable]) {
        nextId += 1
        entries.insert(Entry(id: nextId, at: clock(), event: event, properties: properties), at: 0)
        if entries.count > capacity { entries.removeLast(entries.count - capacity) }
    }

    func clear() {
        entries = []
    }
}
#endif

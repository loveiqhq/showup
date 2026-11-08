// ReceivedLedger.swift
// ShowUp · which of the match's messages `message_received` has reported, kept on the phone (SHOWUP-194)
//
// The registry's rule is once per message. Held in memory alone, every launch reported the whole thread
// again, and a date's "answered" count grew with each app open (the SHOWUP-194 review). Per date, the ids
// already reported: a later launch reports only the rest. Ids, not the newest time: two messages in one
// millisecond would have hidden the second. A date's chat is tens of messages, so the set stays small.
// Android's `ReceivedLedger.kt`.

import Foundation

@MainActor
protocol ReceivedLedger {
    /// The match's messages already reported for `dateId`, by id.
    func reported(_ dateId: String) -> Set<String>
    /// `ids` have been reported for `dateId`, beside those already kept.
    func record(_ dateId: String, _ ids: [String])
}

/// The phone's ledger: a set of ids per date, so the standard defaults are enough.
struct UserDefaultsReceivedLedger: ReceivedLedger {
    private static let prefix = "showup.chatReceived."

    func reported(_ dateId: String) -> Set<String> {
        Set(UserDefaults.standard.stringArray(forKey: Self.prefix + dateId) ?? [])
    }

    func record(_ dateId: String, _ ids: [String]) {
        guard !ids.isEmpty else { return }
        UserDefaults.standard.set(Array(reported(dateId).union(ids)), forKey: Self.prefix + dateId)
    }
}

/// A ledger in memory. Previews and tests.
@MainActor
final class InMemoryReceivedLedger: ReceivedLedger {
    private var ids: [String: Set<String>] = [:]

    func reported(_ dateId: String) -> Set<String> { ids[dateId] ?? [] }
    func record(_ dateId: String, _ newIds: [String]) { ids[dateId, default: []].formUnion(newIds) }
}

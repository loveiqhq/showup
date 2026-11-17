// ChangeAnalytics.swift
// ShowUp · Date 05's client events (SHOWUP-196; registry 1.4.35, family K -- Date Coordination)
//
// Builders only: each returns the name and the payload. The picker's model reports them. The server owns
// `change_suggested`, `change_agreed` and `change_expired`, so a successful send fires nothing here. Android's
// `ChangeAnalytics.kt`.
//
// NEVER IN A PAYLOAD: a place name, an address, a coordinate or a distance. Every payload is ids, the kind and the
// entry -- and the picker is an overlay on `date_screen` / `date_chat`, so it fires no `screen_viewed`.

import Foundation

enum ChangeAnalytics {
    static let pickerOpenedName = "change_picker_opened"
    static let pickerDismissedName = "change_picker_dismissed"

    /// The picker is shown: from a chip or tile (after `action_chip_used`), or from Suggest another (alone).
    static func pickerOpened(dateId: String, kind: ChangeKind, entry: ChangeEntry, meetAt: Date, now: Date) -> ChatAnalytics.Event {
        (pickerOpenedName, [
            "date_id": dateId,
            "kind": kind.rawValue,
            "entry": entry.rawValue,
            "minutes_before_start": minutesBeforeStart(meetAt: meetAt, now: now),
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// Cancel or back, with nothing sent. `hadSelection`: a slot or a place was selected when it closed.
    static func pickerDismissed(dateId: String, kind: ChangeKind, entry: ChangeEntry, hadSelection: Bool) -> ChatAnalytics.Event {
        (pickerDismissedName, [
            "date_id": dateId,
            "kind": kind.rawValue,
            "entry": entry.rawValue,
            "had_selection": hadSelection,
        ].merging(Stamp.of(0)) { a, _ in a })
    }
}

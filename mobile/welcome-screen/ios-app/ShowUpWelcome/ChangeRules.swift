// ChangeRules.swift
// ShowUp · Date 05's rules, as plain functions (SHOWUP-196)
//
// When the change tiles and chips show, the deadline, the distance line, the labels. No view, so each is tested
// without one (`ChangeRulesTests`). Android's `ChangeRules.kt`, name for name.

import Foundation

/// How long before the BINDING meet time a suggestion can no longer be answered (3 Nov 2026).
private let deadlineMinutes: TimeInterval = 30

/// The answer deadline: the binding meet time less 0:30, counted from the binding time, never the suggested one.
func changeDeadline(binding: Date) -> Date { binding.addingTimeInterval(-deadlineMinutes * 60) }

/// The last slot the server sent, `{latest}`; nil when it sent none.
func latestSlot(_ slots: [Date]) -> Date? { slots.max() }

/// `HH:mm` on the phone's clock: every `{time}`, `{deadline}`, `{latest}` and slot (R19), as `meetTime` reads it.
func changeClock(_ at: Date, zone: TimeZone) -> String { meetTime(at, zone: zone) }

/// Whether the entry for `kind` shows, for the locked card's tiles and the chat's two chips alike (R7, one check).
/// Hidden for both people when the server says no (a committed check-in), from the cutoff on, and -- for the time
/// only -- while there is no later slot. Evaluated with the screens' clock tick, so they hide in place. A server
/// before Date 05 sends no block: neither shows.
func changeKindAvailable(_ change: DateChange?, kind: ChangeKind, now: Date) -> Bool {
    guard let change, change.allowed, now < change.cutoffAt else { return false }
    return kind != .time || !change.slots.isEmpty
}

/// The locked card's tiles, in the chips' order.
func changeTilesFor(_ change: DateChange?, now: Date) -> [ChatReason] {
    var tiles: [ChatReason] = []
    if changeKindAvailable(change, kind: .time, now: now) { tiles.append(.changeTime) }
    if changeKindAvailable(change, kind: .place, now: now) { tiles.append(.changePlace) }
    return tiles
}

/// The chips the start grid and the dock show: the shipped ones, the two changes only while `changeTilesFor` has them.
func visibleReasons(_ change: DateChange?, now: Date) -> [ChatReason] {
    let changes = changeTilesFor(change, now: now)
    return shownReasons.filter { ($0 != .changeTime && $0 != .changePlace) || changes.contains($0) }
}

/// The kind a change chip or tile opens; nil for any other chip.
func changeKindOf(_ reason: ChatReason) -> ChangeKind? {
    switch reason {
    case .changeTime: return .time
    case .changePlace: return .place
    default: return nil
    }
}

/// The picker a chip or tile opens: its kind, and the §42 entry -- `coordinate_tile` for the locked card's tiles,
/// `chat_chip` for the grid and the dock. Nil for a chip that opens no picker.
func changePickerRoute(_ reason: ChatReason, surface: ChipSurface) -> AppRoute? {
    guard let kind = changeKindOf(reason) else { return nil }
    return .changePicker(kind: kind, entry: surface == .coordinateCard ? .coordinateTile : .chatChip)
}

/// `Suggest another` on a card in the thread: the picker of the same kind, entry `counter_thread` (§42).
func counterThreadRoute(_ kind: ChangeKind) -> AppRoute {
    .changePicker(kind: kind, entry: .counterThread)
}

/// `350 m` under a kilometre, rounded to the nearest 50; `1.1 km` from it, one decimal (R3). Integer maths so a half
/// rounds up on every phone: 349 is 350 m, 999 reads `1.0 km` (50 m steps reach a full kilometre), 1049 is `1.0 km`,
/// 1050 is `1.1 km`. Never in analytics.
func formatDistance(_ meters: Int) -> String {
    let m = max(meters, 0)
    let stepped = (m + 25) / 50 * 50
    if m < 1000 && stepped < 1000 { return "\(stepped) m" }
    let tenths = (m + 50) / 100
    return "\(tenths / 10).\(tenths % 10) km"
}

/// `Café`, `Bar`, `Restaurant`: the card and request card read `{Type} · {address}`.
func placeTypeLabel(_ type: PlaceType) -> String {
    switch type {
    case .cafe: return "Café"
    case .bar: return "Bar"
    case .restaurant: return "Restaurant"
    }
}

/// The row's tag, uppercased as the kit draws it.
func placeTypeTag(_ type: PlaceType) -> String { placeTypeLabel(type).uppercased() }

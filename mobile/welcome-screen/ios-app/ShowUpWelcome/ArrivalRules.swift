// ArrivalRules.swift
// ShowUp · the arrival check-in's plain rules (SHOWUP-195). Android's `ArrivalRules.kt`.

import Foundation

/// `enums.json` section 41 `arrival_entry`: where the person tapped. `report` is reserved for Date 06.
enum ArrivalEntry: String, CaseIterable, Sendable {
    case dateScreen = "date_screen"
    case chatChip = "chat_chip"
    case report = "report"

    var wire: String { rawValue }
}

/// How long one location read may take before the person is told, in ms.
let locationFixTimeoutMs = 10_000

/// Whole minutes from the latest agreed meet time to `now`: negative before, truncated toward zero -- the
/// server's `minutes_vs_start`, so both sides' events agree.
func minutesVsStart(_ meetAt: Date, _ now: Date) -> Int {
    let seconds = Int(now.timeIntervalSince1970.rounded(.down)) - Int(meetAt.timeIntervalSince1970.rounded(.down))
    return seconds / 60
}

/// The wait before retry number `attempt` (0-based) of an undo or commit: 2 s, doubling, held at 30 s.
func retryDelayMs(_ attempt: Int) -> Int {
    let doubled = 2_000 << min(max(attempt, 0), 10)
    return min(doubled, 30_000)
}

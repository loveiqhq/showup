// ArrivalRouting.swift
// ShowUp · what the host decides around the arrival gate (SHOWUP-195). Android's `ArrivalRouting.kt`.
//
// Plain functions, so the rules are tested without a screen.

import SwiftUI

/// The `I arrived at the spot` chip before check-in opens the gate instead of selecting a draft. The host calls
/// `chat.pick(reason, surface, gated)` first (`action_chip_used`), then `gate.open(.chatChip)` when this is true
/// (`arrival_confirm_shown`).
@MainActor
func arrivalChipGated(_ reason: ChatReason, _ gate: ArrivalGateModel) -> Bool {
    reason == .arrived && !gate.checkedIn()
}

/// Where an undo leaves the person.
enum UndoLanding: Equatable, Sendable {
    /// Nothing was undone.
    case none
    /// From the chat: leave it for the Date screen, then consume.
    case leaveChat
    /// Already on the Date screen (or elsewhere): just consume.
    case consumeOnly
}

func undoLanding(undone: Bool, chatOnTop: Bool) -> UndoLanding {
    if !undone { return .none }
    return chatOnTop ? .leaveChat : .consumeOnly
}

/// The chat's chip tap, as the host runs it: `pick` first (`action_chip_used`), then the gate's sheet
/// (`arrival_confirm_shown`) when the chip is gated.
/// `beforeGate` runs just before the sheet opens, when the chip is gated: the host puts the keyboard away.
@MainActor
func onChatChip(_ reason: ChatReason, _ surface: ChipSurface, _ gate: ArrivalGateModel,
                pick: (ChatReason, ChipSurface, Bool) -> Void, beforeGate: () -> Void = {}) {
    let gated = arrivalChipGated(reason, gate)
    pick(reason, surface, gated)
    if gated {
        beforeGate()
        gate.open(.chatChip)
    }
}

/// An undo, as the host acts on it: `leaveChat` first when the chat is on top, then `consume`, exactly once.
func routeUndo(undone: Bool, chatOnTop: Bool, leaveChat: () -> Void, consume: () -> Void) {
    switch undoLanding(undone: undone, chatOnTop: chatOnTop) {
    case .none: break
    case .leaveChat:
        leaveChat()
        consume()
    case .consumeOnly: consume()
    }
}

/// The scene changed phase: only `.background` is the person leaving (the done sheet commits, and a read still
/// busy commits when its store lands); `.active` is back, and a store that lands now shows the done sheet as normal.
/// `.inactive` is Control Center, a call banner or a Face ID sheet passing over the app; iOS has no rebuild on
/// rotation or a font change, so Android's `changingConfigurations` has no twin here.
@MainActor
func onScenePhase(_ phase: ScenePhase, gate: ArrivalGateModel) {
    switch phase {
    case .background: gate.leftApp()
    case .active: gate.foregrounded()
    case .inactive: break
    @unknown default: break
    }
}

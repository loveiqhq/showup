/*
 * ArrivalRouting.kt
 * ShowUp · what the host decides around the arrival gate (SHOWUP-195)
 *
 * Pure, so the rules are tested without a screen.
 */
package com.showup.arrival

import com.showup.chat.ChatReason

/**
 * The `I arrived at the spot` chip before check-in opens the gate instead of selecting a draft. The host
 * calls `chat.pick(reason, surface, gated)` first (`action_chip_used`), then `gate.open(ChatChip)` when this
 * is true (`arrival_confirm_shown`).
 */
fun arrivalChipGated(reason: ChatReason, gate: ArrivalGateViewModel): Boolean =
    reason == ChatReason.Arrived && !gate.checkedIn()

/** Where an undo leaves the person. */
enum class UndoLanding {
    /** Nothing was undone. */
    None,

    /** From the chat: leave it for the Date screen, then consume. */
    LeaveChat,

    /** Already on the Date screen (or elsewhere): just consume. */
    ConsumeOnly,
}

fun undoLanding(undone: Boolean, chatOnTop: Boolean): UndoLanding = when {
    !undone -> UndoLanding.None
    chatOnTop -> UndoLanding.LeaveChat
    else -> UndoLanding.ConsumeOnly
}

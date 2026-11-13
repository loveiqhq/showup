/*
 * ArrivalRouting.kt
 * ShowUp · what the host decides around the arrival gate (SHOWUP-195)
 *
 * Pure, so the rules are tested without a screen.
 */
package com.showup.arrival

import com.showup.chat.ChatReason
import com.showup.chat.ChipSurface

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

/**
 * The chat's chip tap, as the host runs it: [pick] first (`action_chip_used`), then the gate's sheet
 * (`arrival_confirm_shown`) when the chip is gated.
 */
fun onChatChip(reason: ChatReason, surface: ChipSurface, gate: ArrivalGateViewModel, pick: (ChatReason, ChipSurface, Boolean) -> Unit) {
    val gated = arrivalChipGated(reason, gate)
    pick(reason, surface, gated)
    if (gated) gate.open(ArrivalEntry.ChatChip)
}

/** An undo, as the host acts on it: [leaveChat] first when the chat is on top, then [consume], exactly once. */
fun routeUndo(undone: Boolean, chatOnTop: Boolean, leaveChat: () -> Unit, consume: () -> Unit) {
    when (undoLanding(undone, chatOnTop)) {
        UndoLanding.None -> Unit
        UndoLanding.LeaveChat -> {
            leaveChat()
            consume()
        }
        UndoLanding.ConsumeOnly -> consume()
    }
}

/** The Activity stopped: the person left D unless the Activity is only being rebuilt (rotation, theme, font). */
fun onAppStopped(gate: ArrivalGateViewModel, changingConfigurations: Boolean) {
    if (!changingConfigurations) gate.leftApp()
}

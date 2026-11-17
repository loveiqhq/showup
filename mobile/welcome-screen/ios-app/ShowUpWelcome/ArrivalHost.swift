// ArrivalHost.swift
// ShowUp · the arrival gate's sheets over a screen, the screen behind them blurred (SHOWUP-195). Android's
// `ArrivalHost.kt`.
//
// The one place the Date screen and the chat share, so both blur exactly as `ChatScreen`'s End date sheet does:
// `ModalShell`'s backdrop, arriving with the sheet.

import SwiftUI

struct ArrivalOver<Content: View>: View {
    let ui: GateUi
    let name: String
    let onYes: () -> Void
    let onNotYet: () -> Void
    let onClose: () -> Void
    let onUndo: () -> Void
    @ViewBuilder let content: () -> Content

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let up = ui.phase != .idle
        ZStack {
            content()
                .blur(radius: up ? modalShellBlur : 0)
                .animation(reduceMotion ? nil : .easeInOut(duration: Motion.sheet), value: up)
            ArrivalSheetHost(ui: ui, name: name, onYes: onYes, onNotYet: onNotYet, onClose: onClose, onUndo: onUndo)
        }
    }
}

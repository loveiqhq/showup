// ChatScreen.swift
// ShowUp · the coordinate chat (SHOWUP-194): B, the start grid, and C to E, the thread with its dock; F, the
// End the date sheet, over it. One screen, one screen id, from `screen-chat-reference.jsx`. Android's
// `ChatScreen.kt`.
//
// ONE COLUMN, ONE FLEXIBLE ELEMENT. Header, then the thread (the only scroll), then the dock (the field,
// then the chips), then the keyboard -- the system's, never a height in code: SwiftUI's keyboard avoidance
// lifts the dock and the thread absorbs the difference. No absolute positions.
//
// The screen takes `ChatUi` and returns pixels; every decision is the model's or `ChatRules`'.

import SwiftUI

struct ChatScreen: View {
    let ui: ChatUi
    var zone: TimeZone = .current
    var onBack: () -> Void = {}
    var onPick: (ChatReason, ChipSurface) -> Void = { _, _ in }
    var onEdit: (String) -> Void = { _ in }
    var onAckGuide: () -> Void = {}
    var onSend: () -> Void = {}
    var onEndCard: () -> Void = {}
    var onKeep: () -> Void = {}
    var onEnd: () -> Void = {}
    var onRetry: () -> Void = {}

    @FocusState private var fieldFocused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Color.liqCream.ignoresSafeArea()
            if let date = ui.date {
                VStack(spacing: 0) {
                    ChatHeader(date: date, now: ui.now, zone: zone, onBack: onBack)
                    if ui.loading {
                        Color.liqElevated.frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if ui.failed != nil {
                        ZStack {
                            Color.liqElevated
                            CentredStatement(text: ChatCopy.loadFailed, action: SearchCopy.tryAgain, onAction: onRetry)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if ui.start {
                        startGrid
                    } else {
                        thread(date)
                        dock(date)
                    }
                }
                // `ModalShell`'s backdrop blur, arriving with the sheet.
                .blur(radius: ui.endSheet ? modalShellBlur : 0)
                .animation(reduceMotion ? nil : .easeInOut(duration: Motion.sheet), value: ui.endSheet)
                if ui.endSheet {
                    EndDateSheet(name: date.match.name, ending: ui.ending, failed: ui.endFailed != nil,
                                 onKeep: onKeep, onEnd: onEnd)
                }
            }
        }
        // After `Got it` the field takes the focus.
        .onChange(of: ui.guide) { old, new in
            if old == .locked && new == .acked { fieldFocused = true }
        }
    }

    // MARK: B · start

    /// B: one scroll, white, pad 20 16 20 -- the guide (the headline Lora 700 25 with `reason` washed, then
    /// two lines 14 / 1.5 muted, at most 290, 20 below), the five tiles in two columns, and the privacy line
    /// 18 below. No field and no dock: a tile opens the thread with that chip.
    private var startGrid: some View {
        ScrollView {
            VStack(spacing: 0) {
                WashHeadline(parts: [(ChatCopy.startHeadlineLead, false), (ChatCopy.startHeadlineItalic, true)],
                             fontSize: 25, lineHeightMultiple: 1.14, trackingEm: -0.02, balance: true, centered: true)
                    .fixedSize(horizontal: false, vertical: true)
                PrettyText(ChatCopy.startBody1 + "\n" + ChatCopy.startBody2, font: chatFont(PS.manropeRegular, 14, .regular),
                           lineHeight: 14 * 1.5, color: .liqMuted, centered: true)
                    .frame(maxWidth: 290)
                    .padding(.top, 8)
                ChipGrid(reasons: shownReasons, selected: nil, compact: false) { onPick($0, .entry) }
                    .padding(.top, 20)
                LockLine(text: ChatCopy.startPrivacy)
                    .padding(.top, 18)
            }
            .padding(EdgeInsets(top: 20, leading: 16, bottom: 20, trailing: 16))
        }
        .background(Color.liqElevated)
    }

    // MARK: C to E · the thread

    /// The thread, pad 14 14 8 (6 14 6 while the guide waits, so it has the room on a short phone), gap 12:
    /// the messages oldest first, the avatar beside the last of each of the match's runs. While "I can't make
    /// it today" is selected the guide (or its collapsed line) sits at the foot; otherwise, once such a
    /// message is in, the End date card follows the messages as the thread's last item.
    private func thread(_ date: ActiveDate) -> some View {
        let guide = ui.selected == .cantMakeIt
        let waiting = guide && ui.guide == .locked
        let footKey: String? = guide ? (ui.guide == .acked ? "guide-acked" : "guide-locked") : (ui.endCardShown ? "end-card" : nil)
        return ThreadScroll(
            padding: waiting ? EdgeInsets(top: 6, leading: 14, bottom: 6, trailing: 14)
                             : EdgeInsets(top: 14, leading: 14, bottom: 8, trailing: 14),
            messageCount: ui.messages.count,
            footKey: footKey,
            messages: { width in
                ForEach(Array(ui.messages.enumerated()), id: \.element.id) { i, message in
                    let lastOfRun = !message.fromYou && (i == ui.messages.count - 1 || ui.messages[i + 1].fromYou)
                    ChatBubble(message: message, name: date.match.name, photoURL: date.match.photoUrl,
                               showAvatar: lastOfRun, zone: zone, width: width)
                }
                if !guide && ui.endCardShown { EndDateCard(onEnd: onEndCard) }
            },
            foot: {
                if guide { CancelGuide(name: date.match.name, acked: ui.guide == .acked, onAck: onAckGuide) }
            })
            .background(Color.liqElevated)
            .fitAnchor("chat:thread")
    }

    // MARK: the dock

    /// The field (pad 11 14 9, gap 9) and the compact chips (pad 2 14 10, gap 6, no heading) on cream 96%
    /// glass under a soft rule. A failed send says so between the two and keeps the draft and the chip.
    private func dock(_ date: ActiveDate) -> some View {
        VStack(spacing: 0) {
            composer(date)
            if ui.sendFailed {
                Text(ChatCopy.sendFailed).font(F.manrope(12, .medium)).foregroundColor(.liqDangerFg)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(EdgeInsets(top: 0, leading: 18, bottom: 6, trailing: 18))
            }
            ChipGrid(reasons: shownReasons, selected: ui.selected, compact: true) { onPick($0, .dock) }
                .padding(EdgeInsets(top: 2, leading: 14, bottom: 10, trailing: 14))
        }
        .modifier(PinnedGlass())
        .fitPinnedBar()
        .fitAnchor("chat:dock")
    }

    /// The field and Send. The field: radius 20, pad 9 14, a 1.5 border in the ink while a chip is selected
    /// (otherwise the border token), filled soft and disabled while the guide waits; it grows to about 90 and
    /// then scrolls. Send: 46 round, the sunset with the violet glow when there is text to send, the border
    /// token when not -- both crossing over `Motion.tint`.
    private func composer(_ date: ActiveDate) -> some View {
        let placeholder = ui.locked ? ChatCopy.placeholderLocked
            : (ui.selected == .cantMakeIt ? ChatCopy.placeholderSorry(date.match.name) : ChatCopy.placeholder)
        let shape = RoundedRectangle(cornerRadius: 20)
        let strong = ui.selected != nil && !ui.locked
        return HStack(alignment: .bottom, spacing: 9) {
            TextField("", text: Binding(get: { ui.draft }, set: { onEdit($0) }),
                      prompt: Text(placeholder).foregroundColor(.liqSubtle), axis: .vertical)
                .font(F.manrope(14.5, .medium))
                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeMedium, size: 14.5, multiple: 1.4))
                .foregroundColor(.liqFg)
                .lineLimit(1...4)
                .disabled(ui.locked)
                .focused($fieldFocused)
                .fitTapArea()
                // Pad 9 14, plus the 1.5 border CSS counts, plus the 2 every browser pads a textarea with.
                .padding(EdgeInsets(top: 12.5, leading: 17.5, bottom: 12.5, trailing: 17.5))
                .background(shape.fill(ui.locked ? Color.liqBorderSoft : Color.liqElevated))
                .overlay(shape.strokeBorder(strong ? Color.liqFg : Color.liqBorder, lineWidth: 1.5))
                .animation(reduceMotion ? nil : .easeInOut(duration: Motion.tint), value: strong)
                .animation(reduceMotion ? nil : .easeInOut(duration: Motion.tint), value: ui.locked)
            sendButton
        }
        .padding(EdgeInsets(top: 11, leading: 14, bottom: 9, trailing: 14))
    }

    private var sendButton: some View {
        let on = ui.sendEnabled
        return Button(action: onSend) {
            ZStack {
                Circle().fill(Color.liqBorder)
                ShadowedShape(shape: Circle(), fill: Gradients.sunset(), shadows: shadowViolet).opacity(on ? 1 : 0)
                // `margin-left: -1, margin-top: 1`: the paper plane sits optically centred in the disc.
                BrandIconView(icon: .send, size: 19, stroke: 2.1, tint: .white).offset(x: -1, y: 1)
            }
            .frame(width: 46, height: 46)
            .animation(reduceMotion ? nil : .easeInOut(duration: Motion.tint), value: on)
        }
        .buttonStyle(PressScale())
        .disabled(!on)
        .overflowTapTarget()
        .accessibilityLabel(ChatCopy.send)
    }
}

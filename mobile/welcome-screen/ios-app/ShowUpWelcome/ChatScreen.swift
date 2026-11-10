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
import UIKit

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
    /// J: the line under a failed message, by its `clientId`.
    var onResend: (String) -> Void = { _ in }

    @FocusState private var fieldFocused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Color.liqCream.ignoresSafeArea()
            if let date = ui.date {
                VStack(spacing: 0) {
                    ChatHeader(date: date, now: ui.now, zone: zone, onBack: onBack)
                    if ui.failed != nil {
                        // I: the thread's place, the header kept, no dock and so no keyboard. It stays while
                        // Try again reloads, the button waiting.
                        ZStack {
                            Color.liqElevated
                            LoadFailed(text: ChatCopy.loadFailed, action: SearchCopy.tryAgain, onAction: onRetry, busy: ui.loading)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if ui.loading {
                        Color.liqElevated.frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if ui.start {
                        startGrid
                    } else {
                        ThreadAndDock {
                            thread(date)
                            dock(date)
                        }
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
                    if message.failed {
                        UnsentMessage(message: message, date: date, zone: zone, width: width,
                                      resending: message.clientId.map { ui.resending.contains($0) } ?? false, onResend: onResend)
                    } else {
                        let lastOfRun = !message.fromYou && (i == ui.messages.count - 1 || ui.messages[i + 1].fromYou)
                        ChatBubble(message: message, name: date.match.name, photoURL: date.match.photoUrl,
                                   showAvatar: lastOfRun, zone: zone, width: width)
                    }
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
    /// glass under a soft rule. A failed send stays in the thread (J), not here.
    /// The field always shows; where the chips do not fit under it they scroll -- only on a split screen or
    /// at the largest type on a small phone, where `ThreadAndDock` caps the dock.
    private func dock(_ date: ActiveDate) -> some View {
        VStack(spacing: 0) {
            composer(date)
            ViewThatFits(in: .vertical) {
                dockChips
                ScrollView { dockChips }.scrollBounceBehavior(.basedOnSize)
            }
        }
        .modifier(PinnedGlass())
        .fitPinnedBar()
        .fitAnchor("chat:dock")
    }

    /// Pad 2 14 10, plus the 1 the field's row gives up (its 9 is 8 here): the top row's chips answer 3 above
    /// themselves, and where the chips scroll the scroll clips at its top. Android's `DockChips`.
    private var dockChips: some View {
        ChipGrid(reasons: shownReasons, selected: ui.selected, compact: true) { onPick($0, .dock) }
            .padding(EdgeInsets(top: 3, leading: 14, bottom: 10, trailing: 14))
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
                      // Never one word alone on the prompt's last line: on a narrow phone the locked prompt
                      // split the button's name, `... tap Got` / `it` (the 8 Nov 2026 device sweep).
                      prompt: Text(lastWordsTogether(placeholder)).foregroundColor(.liqSubtle), axis: .vertical)
                .font(F.manrope(14.5, .medium))
                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeMedium, size: 14.5, multiple: 1.4))
                .foregroundColor(.liqFg)
                .lineLimit(1...4)
                .disabled(ui.locked)
                .focused($fieldFocused)
                // Pad 9 14, plus the 1.5 border CSS counts, plus the 2 every browser pads a textarea with.
                .padding(EdgeInsets(top: 12.5, leading: 17.5, bottom: 12.5, trailing: 17.5))
                // At least the 44 touch floor, as Android's field keeps its 48: at the smallest type sizes the box
                // was 43 (the 9 Nov 2026 CI sweep, once the dock's blanket exemption went). At the default it is 45.
                .frame(minHeight: 44)
                .background(shape.fill(ui.locked ? Color.liqBorderSoft : Color.liqElevated))
                .overlay(shape.strokeBorder(strong ? Color.liqFg : Color.liqBorder, lineWidth: 1.5))
                // The whole box takes the touch, not only the line of text inside it: a tap on its padding
                // focused nothing -- a touch area 21 tall in a field 46 tall (the SHOWUP-194 fit sweep).
                // Android's field is its decoration box, so it never had this.
                .contentShape(shape)
                .simultaneousGesture(TapGesture().onEnded { if !ui.locked { fieldFocused = true } })
                .fitTapArea()
                .animation(reduceMotion ? nil : .easeInOut(duration: Motion.tint), value: strong)
                .animation(reduceMotion ? nil : .easeInOut(duration: Motion.tint), value: ui.locked)
            sendButton
        }
        // Pad 11 14 9, the 9 split: 8 here and 1 above the chips (see `dockChips`).
        .padding(EdgeInsets(top: 11, leading: 14, bottom: 8, trailing: 14))
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

/// J (9 Nov 2026): a message whose send failed -- the bubble at opacity .6 with no receipt, and 4 under it,
/// right-aligned, the line that resends it: `alert-circle` 14 / 2.4, gap 6, the sentence 12 / 600 / 1.35 in the
/// danger ink at most 280, the line at least 44 high. It announces itself, as `role="alert"` does. While the
/// resend is on its way the line does nothing. Android's `UnsentMessage`.
private struct UnsentMessage: View {
    let message: ChatMessage
    let date: ActiveDate
    let zone: TimeZone
    let width: CGFloat
    let resending: Bool
    let onResend: (String) -> Void

    var body: some View {
        VStack(alignment: .trailing, spacing: 4) {
            ChatBubble(message: message, name: date.match.name, photoURL: date.match.photoUrl,
                       showAvatar: false, zone: zone, width: width)
                .opacity(0.6)
            Button(action: { if !resending, let id = message.clientId { onResend(id) } }) {
                HStack(spacing: 6) {
                    BrandIconView(icon: .alertCircle, size: 14, stroke: 2.4, tint: .liqDangerFg)
                    // Right-aligned as the reference's `text-align: right`, which PrettyText does not draw; the
                    // last two words kept together for its `text-wrap: pretty`.
                    Text(lastWordsTogether(ChatCopy.sendFailed))
                        .font(F.manrope(12, .semibold))
                        .lineSpacing(TypeMetrics.lineSpacing(PS.manropeSemi, size: 12, multiple: 1.35))
                        .foregroundColor(.liqDangerFg)
                        .multilineTextAlignment(.trailing)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 2)
                .frame(maxWidth: 280, minHeight: 44, alignment: .trailing)
                .contentShape(Rectangle())
            }
            .buttonStyle(PressScale())
            .fitTapArea()
            .accessibilityLabel(ChatCopy.sendFailed)
            .onAppear { UIAccessibility.post(notification: .announcement, argument: ChatCopy.sendFailed) }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
    }
}

/// The thread over the dock: the thread keeps 72 -- a short message and its time (pad 14, bubble 40, time
/// 18) -- and the dock its own height in the rest. On a split screen or at the largest type the dock wanted
/// more than the window and squeezed the thread out (the SHOWUP-194 fit sweep); on a phone, even with the
/// keys up and a draft of two lines, nothing changes. The field is never cut for it: where the field alone
/// is taller than that -- its placeholder in three lines at the largest type with the keys up -- the thread
/// gives way (the SHOWUP-194 keyboard sweep). Android's `ThreadLeast` and `Dock` in `ChatScreen`.
private struct ThreadAndDock: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        proposal.replacingUnspecifiedDimensions()
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let most = Swift.max(bounds.height - 72, 0)
        // The dock's own answer at `most`: the field whole, and the chips (scrolling) in what is left.
        let dock = Swift.min(subviews[1].sizeThatFits(ProposedViewSize(width: bounds.width, height: most)).height,
                             bounds.height)
        subviews[0].place(at: bounds.origin, anchor: .topLeading,
                          proposal: ProposedViewSize(width: bounds.width, height: bounds.height - dock))
        subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.maxY - dock), anchor: .topLeading,
                          proposal: ProposedViewSize(width: bounds.width, height: dock))
    }
}

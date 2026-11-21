// CancelDateConfirmSheet.swift
// ShowUp · B to E and I, the penalty guard (SHOWUP-198), from `CancelDateConfirmSheet` in `screen-cancel-reference.jsx`.
// Android's `CancelDateConfirmSheet.kt`.
//
// ONE MODAL SHELL. Chat locked (B), chat open (C), a request sent (D), a failed cancel (E) and Date 05's cutoff (I) are
// one sheet whose parts are replaced in place -- never a second sheet. The shell scrolls inside, so the choices are
// reached by scrolling on every phone.
//
// The screen takes values and closures (`CancelUi`, `CancelActions`); it never receives a navigator, so every frame
// renders in every state at every size with no device.
//
// Numbers are the reference's, with CSS's border-in-the-box counted into the padding where SwiftUI only draws a border
// inside (a row's 13 + its 1 pt), and the line boxes as `PrettyText`'s.

import SwiftUI

/// The ids the fit tests read, one place.
extension FitAnchorID {
    static let cancelSheet = "cancel-sheet"
    static let cancelPortrait = "cancel-portrait"
    static let cancelPill = "cancel-pill"
    static let cancelHeadline = "cancel-headline"
    static let cancelReason = "cancel-reason"
    static let cancelSolution = "cancel-solution"
    static let cancelSolutionBody = "cancel-solution-body"
    static let cancelTileTime = "cancel-tile-time"
    static let cancelTilePlace = "cancel-tile-place"
    static let cancelSent = "cancel-sent"
    static let cancelMessage = "cancel-message"
    static let cancelCost = "cancel-cost"
    static let cancelKeep = "cancel-keep"
    static let cancelConfirm = "cancel-confirm"
    static let cancelWarning = "cancel-warning"
}

/// The reference's own red (`#dc2626`) for the cost box, the link and the warning line. It is not `liqDanger`
/// (`#FB323B`): the sheet draws a deeper one on purpose, and no token holds it.
private let cancelRed = Color(hex: 0xDC2626)

/// Every tap the sheet can send. The host binds them to the model; a test records them.
struct CancelActions {
    var onKeep: () -> Void = {}
    var onDismiss: () -> Void = {}
    var onMessage: () -> Void = {}
    var onTile: (ChangeKind) -> Void = { _ in }
    var onCancel: () -> Void = {}
}

// MARK: text

/// One paragraph as the reference sets it: Manrope, a CSS line box, pretty-wrapped. The marks are the runs': `<strong>`
/// is 700 in the ink, `<em>` the upright skewed in the paragraph's own ink, `no-show` 800 in the red.
private func cancelPara(_ runs: [Run], size: CGFloat, lineHeight: CGFloat, color: Color, centered: Bool = false,
                        pretty: Bool = true) -> PrettyText {
    var text = PrettyText(runs: runs.map { ($0.text, $0.style != .plain) },
                          font: chatFont(PS.manropeRegular, size, .regular), lineHeight: size * lineHeight, color: color,
                          emphasisFont: chatFont(PS.manropeBold, size, .bold), emphasisColor: .liqFg,
                          centered: centered, pretty: pretty)
    for (index, run) in runs.enumerated() {
        switch run.style {
        case .italic:
            text.runFaces[index] = (TypeMetrics.oblique(PS.manropeRegular, size,
                                                        fallback: TypeMetrics.italicSystem(size, weight: .regular)), color)
        case .danger:
            text.runFaces[index] = (chatFont(PS.manropeXBold, size, .heavy), cancelRed)
        case .plain, .bold:
            break
        }
    }
    return text
}

private func cancelPara(_ text: String, size: CGFloat, lineHeight: CGFloat, color: Color, centered: Bool = false,
                        pretty: Bool = true) -> PrettyText {
    cancelPara([Run(text: text)], size: size, lineHeight: lineHeight, color: color, centered: centered, pretty: pretty)
}

// MARK: the shell

/// B to E and I while the model's sheet is open, nothing otherwise. The scrim and a swipe close it (the model's
/// `dismiss`); nothing is stored.
struct CancelDateSheetHost: View {
    let ui: CancelUi
    var zone: TimeZone = .current
    var actions = CancelActions()

    var body: some View {
        if ui.open, let date = ui.date {
            ModalShell(label: CancelCopy.link, onDismiss: { _ in actions.onDismiss() }, contentPadding: EdgeInsets()) {
                CancelDateConfirmSheet(ui: ui, name: date.match.name, pill: guardPill(date, zone: zone),
                                       photoUrl: date.match.photoUrl, actions: actions)
            }
            .fitModal()
        }
    }
}

/// The sheet's content, padding `10 0 26`, in the reference's order: the portrait, the gut-punch, the three reasons,
/// the solution box, the cost box and the choices.
struct CancelDateConfirmSheet: View {
    let ui: CancelUi
    let name: String
    let pill: String
    let photoUrl: URL?
    var actions = CancelActions()

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            CancelPortrait(name: name, pill: pill, photoUrl: photoUrl)
            CancelGutPunch()
            CancelReasons()
            CancelSolution(ui: ui, name: name, actions: actions)
            CancelCostBox(drop: ui.drop)
            CancelChoices(ui: ui, name: name, actions: actions)
        }
        .padding(EdgeInsets(top: 10, leading: 0, bottom: 26, trailing: 0))
        .frame(maxWidth: .infinity)
        .fitAnchor(FitAnchorID.cancelSheet)
    }
}

// MARK: the portrait

/// Her, made real: the match's photo 244 tall, radius 24, `center 22%`, the ink gradient, the pill and the line.
private struct CancelPortrait: View {
    let name: String
    let pill: String
    let photoUrl: URL?

    var body: some View {
        // rgba(29,17,41,0) at 38% to rgba(29,17,41,.78): the ink at both ends, so the ramp never passes through grey.
        let ramp = LinearGradient(stops: [.init(color: Color.liqFg.opacity(0), location: 0.38),
                                          .init(color: Color.liqFg.opacity(0.78), location: 1)],
                                  startPoint: .top, endPoint: .bottom)
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: 244)
            .overlay { PhotoBackdrop(url: photoUrl, index: 0, focusTop22: true) }
            .overlay { ramp }
            .overlay(alignment: .bottomLeading) { caption }
            .clipShape(RoundedRectangle(cornerRadius: 24))
            // The anchor is the portrait itself, inside its 6 / 20 margin.
            .fitAnchor(FitAnchorID.cancelPortrait)
            .padding(EdgeInsets(top: 6, leading: 20, bottom: 0, trailing: 20))
    }

    private var caption: some View {
        VStack(alignment: .leading, spacing: 0) {
            // The reference's `backdrop-filter: blur(6px)` is dropped, as on Android: over the gradient the 16 % white
            // tint reads the same without it.
            HStack(spacing: 6) {
                BrandIconView(icon: .calendar, size: 13, stroke: 2.4, tint: .white).accessibilityHidden(true)
                Text(pill).font(F.manrope(11.5, .bold)).tracking(-0.01 * 11.5).foregroundColor(.white)
                    .fixedSize(horizontal: false, vertical: true)
            }
            // Pad 5 11, plus the 1 pt border, which CSS counts in the box.
            .padding(EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 12))
            .background(Capsule().fill(Color.white.opacity(0.16)))
            .overlay(Capsule().strokeBorder(Color.white.opacity(0.28), lineWidth: 1))
            .fitAnchor(FitAnchorID.cancelPill)
            Color.clear.frame(height: 8)
            counting
        }
        .padding(EdgeInsets(top: 0, leading: 18, bottom: 16, trailing: 18))
    }

    private var counting: PrettyText {
        var text = PrettyText(CancelCopy.counting(name), font: chatFont(PS.loraBold, 24, .bold), lineHeight: 24,
                              color: .white)
        text.tracking = -0.01 * 24
        text.pretty = false
        return text
    }
}

// MARK: the gut-punch

private struct CancelGutPunch: View {
    var body: some View {
        VStack(spacing: 0) {
            headline
                .frame(maxWidth: .infinity)
                .accessibilityAddTraits(.isHeader)
                .fitAnchor(FitAnchorID.cancelHeadline)
            Color.clear.frame(height: 10)
            cancelPara(CancelCopy.body, size: 15, lineHeight: 1.55, color: .liqMuted, centered: true)
                .frame(maxWidth: 330)
        }
        .padding(EdgeInsets(top: 20, leading: 24, bottom: 0, trailing: 24))
        .frame(maxWidth: .infinity)
    }

    private var headline: PrettyText {
        var text = PrettyText(runs: [(CancelCopy.headlineLead, false), (CancelCopy.headlineItalic, true)],
                              font: chatFont(PS.loraBold, 27, .bold), lineHeight: 27 * 1.14, color: .liqFg,
                              emphasisFont: chatFont(PS.loraBoldItalic, 27, .bold), emphasisColor: .liqFg, centered: true)
        text.tracking = -0.01 * 27
        return text
    }
}

// MARK: the reasons

private struct CancelReasons: View {
    private let icons: [BrandIcon] = [.smile, .heart, .star]

    var body: some View {
        VStack(spacing: 10) {
            ForEach(CancelCopy.reasons.indices, id: \.self) { index in
                let shape = RoundedRectangle(cornerRadius: 16)
                HStack(alignment: .top, spacing: 13) {
                    BrandIconView(icon: icons[index], size: 18, stroke: 2.2, tint: .liqPurple)
                        .frame(width: 34, height: 34)
                        .background(RoundedRectangle(cornerRadius: 11).fill(Color.liqPurple.opacity(0.10)))
                        .padding(.top, 1)
                        .accessibilityHidden(true)
                    cancelPara(CancelCopy.reasons[index], size: 13.5, lineHeight: 1.5, color: .liqMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                // Pad 13 15, plus the 1 pt border, which CSS counts in the box.
                .padding(EdgeInsets(top: 14, leading: 16, bottom: 14, trailing: 16))
                .frame(maxWidth: .infinity)
                .background(shape.fill(Gradients.lilac()))
                .overlay(shape.strokeBorder(Color.liqBorderSoft, lineWidth: 1))
                .fitAnchor("\(FitAnchorID.cancelReason)-\(index)")
            }
        }
        .padding(EdgeInsets(top: 20, leading: 24, bottom: 0, trailing: 24))
    }
}

// MARK: the solution box

/// `Sometimes life happens`, with what is left of Date 05's offer: the body line and the two tiles while a new time or
/// place can still be agreed, or D in their place once one was sent, and `Message {name} instead` whenever the chat is
/// open. From the cutoff (I) only the title and the message button remain; with no tile to offer there is no body line.
private struct CancelSolution: View {
    let ui: CancelUi
    let name: String
    let actions: CancelActions

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 18)
        let bodyShown = ui.bodyShown
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 9) {
                BrandIconView(icon: .messageCircle, size: 17, stroke: 2.3, tint: .liqOrange).accessibilityHidden(true)
                title.frame(maxWidth: .infinity, alignment: .leading)
            }
            if bodyShown {
                Color.clear.frame(height: 6)
                cancelPara(CancelCopy.solutionBody(name), size: 13, lineHeight: 1.5, color: .liqMuted)
                    .fitAnchor(FitAnchorID.cancelSolutionBody)
                Color.clear.frame(height: 13)
                if let sent = ui.sent {
                    CancelSentLine(sent: sent, name: name)
                } else {
                    CancelTiles(kinds: ui.tiles, cancelling: ui.cancelling, onTile: actions.onTile)
                }
            }
            if ui.showMessage {
                Color.clear.frame(height: bodyShown ? 8 : 10)
                CancelMessageButton(name: name, inert: ui.cancelling, action: actions.onMessage)
            }
        }
        // Pad 16 16 15, plus the 1 pt border, which CSS counts in the box.
        .padding(EdgeInsets(top: 17, leading: 17, bottom: 16, trailing: 17))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(shape.fill(LinearGradient(colors: Color.suGradBlush, startPoint: .top, endPoint: .bottom)))
        .overlay(shape.strokeBorder(Color.liqOrange.opacity(0.18), lineWidth: 1))
        .fitAnchor(FitAnchorID.cancelSolution)
        .padding(EdgeInsets(top: 18, leading: 24, bottom: 0, trailing: 24))
    }

    private var title: PrettyText {
        var text = PrettyText(CancelCopy.solutionTitle(name), font: chatFont(PS.manropeXBold, 13.5, .heavy),
                              lineHeight: 13.5 * 1.366, color: .liqFg)
        text.tracking = -0.01 * 13.5
        text.pretty = false
        return text
    }
}

/// D: the check, and the request's value in bold, then the line. Pad 12 13 inside its 1 pt border.
private struct CancelSentLine: View {
    let sent: SentRequest
    let name: String

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14)
        HStack(alignment: .top, spacing: 9) {
            BrandIconView(icon: .check, size: 16, stroke: 2.6, tint: .liqSuccessFg)
                .padding(.top, 1)
                .accessibilityHidden(true)
            cancelPara([Run(text: sent.label, style: .bold), Run(text: CancelCopy.sentTail(name))],
                       size: 13, lineHeight: 1.45, color: .liqFg)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(EdgeInsets(top: 13, leading: 14, bottom: 13, trailing: 14))
        .frame(maxWidth: .infinity)
        .background(shape.fill(Color.liqElevated))
        .overlay(shape.strokeBorder(Color.liqBorderSoft, lineWidth: 1))
        .fitAnchor(FitAnchorID.cancelSent)
    }
}

/// The two tiles in two columns, gap 8: the same tile Date 05's locked card draws, each following its own gate. Both
/// are inert while the cancel is in flight.
private struct CancelTiles: View {
    let kinds: [ChangeKind]
    let cancelling: Bool
    let onTile: (ChangeKind) -> Void

    var body: some View {
        if !kinds.isEmpty {
            HStack(spacing: 8) {
                ForEach(kinds, id: \.self) { kind in tile(kind) }
                // A lone tile keeps its column, as the grid's `1fr 1fr` does.
                if kinds.count == 1 { Color.clear.frame(maxWidth: .infinity) }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func tile(_ kind: ChangeKind) -> some View {
        let shape = RoundedRectangle(cornerRadius: 16)
        let time = kind == .time
        return Button(action: { onTile(kind) }) {
            VStack(spacing: 6) {
                BrandIconView(icon: time ? .clock : .mapPin, size: 18, stroke: 2, tint: .liqPurple)
                    .accessibilityHidden(true)
                Text(time ? CancelCopy.changeTime : CancelCopy.changePlace)
                    .font(F.manrope(13, .semibold)).foregroundColor(.liqFg)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            // Pad 14 8, plus the 1 pt border, which CSS counts in the box.
            .padding(EdgeInsets(top: 15, leading: 9, bottom: 15, trailing: 9))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(shape.fill(Color.liqElevated))
            .overlay(shape.strokeBorder(Color.liqBorderSoft, lineWidth: 1))
        }
        .buttonStyle(PressScale())
        .fitTapArea()
        .fitAnchor(time ? FitAnchorID.cancelTileTime : FitAnchorID.cancelTilePlace)
        .disabled(cancelling)
    }
}

/// `Message {name} instead`: white, h 48, radius 14, the orange hairline, `message-circle` 17 / 2.4, 15 / 700.
private struct CancelMessageButton: View {
    let name: String
    let inert: Bool
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14)
        Button(action: action) {
            HStack(spacing: 8) {
                BrandIconView(icon: .messageCircle, size: 17, stroke: 2.4, tint: .liqOrange).accessibilityHidden(true)
                Text(CancelCopy.message(name)).font(F.manrope(15, .bold)).foregroundColor(.liqOrange)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(shape.fill(Color.liqElevated))
            .overlay(shape.strokeBorder(Color.liqOrange.opacity(0.35), lineWidth: 1))
            .opacity(inert ? 0.6 : 1)
        }
        .buttonStyle(PressScale())
        .fitTapArea()
        .fitAnchor(FitAnchorID.cancelMessage)
        .disabled(inert)
    }
}

// MARK: the cost, kept honest but quiet

private struct CancelCostBox: View {
    let drop: Int

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14)
        HStack(alignment: .top, spacing: 11) {
            BrandIconView(icon: .shieldAlert, size: 17, stroke: 2.3, tint: cancelRed)
                .padding(.top, 1)
                .accessibilityHidden(true)
            cancelPara(CancelCopy.cost(drop), size: 12.5, lineHeight: 1.5, color: .liqFg)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        // Pad 14 16, plus the 1 pt border, which CSS counts in the box.
        .padding(EdgeInsets(top: 15, leading: 17, bottom: 15, trailing: 17))
        .frame(maxWidth: .infinity)
        .background(shape.fill(cancelRed.opacity(0.10)))
        .overlay(shape.strokeBorder(cancelRed.opacity(0.32), lineWidth: 1))
        .fitAnchor(FitAnchorID.cancelCost)
        .padding(EdgeInsets(top: 16, leading: 24, bottom: 0, trailing: 24))
    }
}

// MARK: the choices

/// Padding `20 24 0`, gap 10: E's box (when the cancel failed) between the cost box and the choices, then
/// `Don't cancel my date` (sunset, h 54, radius 16) and the quiet red link with its warning (gap 14).
private struct CancelChoices: View {
    let ui: CancelUi
    let name: String
    let actions: CancelActions

    var body: some View {
        VStack(spacing: 10) {
            // The box carries its own 14 below (`margin 0 0 14`) beside the column's 10, as the reference does.
            if ui.failed { EndErrorBox(text: CancelCopy.cancelFailed) }
            keep
            VStack(spacing: 14) {
                confirm
                warningText
                    .padding(.horizontal, 8)
                    .fitAnchor(FitAnchorID.cancelWarning)
            }
        }
        .padding(EdgeInsets(top: 20, leading: 24, bottom: 0, trailing: 24))
    }

    /// 12 / 700 / 1.4, -0.01em, centred, in the red.
    private var warningText: PrettyText {
        var text = PrettyText(CancelCopy.warning(name), font: chatFont(PS.manropeBold, 12, .bold), lineHeight: 12 * 1.4,
                              color: cancelRed, centered: true)
        text.tracking = -0.01 * 12
        return text
    }

    /// `Don't cancel my date`: the sunset button, h 54 (a minimum, so the largest type grows it), radius 16, the violet glow.
    private var keep: some View {
        Button(action: actions.onKeep) {
            Text(CancelCopy.keep).font(F.manrope(16, .bold)).foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity, minHeight: 54)
                .background(SunsetFill(shape: RoundedRectangle(cornerRadius: 16), shadows: shadowViolet))
                .opacity(ui.cancelling ? 0.6 : 1)
        }
        .buttonStyle(PressScale())
        .fitTapArea()
        .fitAnchor(FitAnchorID.cancelKeep)
        .disabled(ui.cancelling)
    }

    /// `Cancel anyway and let her down`: drawn 30 tall, 14 / 600 in the red, underlined; it answers at 44. The kit's
    /// underline offset of 3 is not reproduced: SwiftUI's `underline` takes no offset.
    private var confirm: some View {
        Button(action: actions.onCancel) {
            Text(CancelCopy.confirm).font(F.manrope(14, .semibold)).foregroundColor(cancelRed)
                .underline(true, color: cancelRed)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 30)
                .opacity(ui.cancelling ? 0.6 : 1)
                .overflowTapTarget()
        }
        .buttonStyle(PressScale())
        .fitAnchor(FitAnchorID.cancelConfirm)
        .disabled(ui.cancelling)
    }
}

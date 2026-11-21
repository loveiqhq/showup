// DateEndSheets.swift
// ShowUp · B to J, the end-date and report sheets (SHOWUP-197), from `screen-wrap-report-reference.jsx`. Android's
// `DateEndSheets.kt`.
//
// ONE MODAL SHELL. `DateEndSheetHost` keeps a single `ModalShell` up while the model's sheet is visible and swaps the
// step inside it (the content crossfades and the height animates, as the arrival gate's does): the reference draws
// one shell per step, which here would be a sheet dropping while another rises.
//
// SHARED STEPS, ONE COPY EACH. `NoShowGuide`, `NoShowConfirm`, `UncomfortableStep` and `EndErrorBox` are rendered by
// BOTH `DateWrapSheet` and `ReportProblemSheet` (through one private dispatcher). A second copy is a bug.
//
// The screens take values and closures (`DateEndUi`, `EndActions`); they never receive a navigator, so every frame
// renders in every state at every size with no device.
//
// Numbers are the reference's, with CSS's border-in-the-box counted into the padding where SwiftUI only draws a
// border inside (a row's 14 + its 1 or 1.5 border), and the line boxes as `PrettyText`'s.

import SwiftUI

/// The ids the fit tests read, one place.
extension FitAnchorID {
    static let endBar = "end-bar"
    static let endBarTile = "end-bar-tile"
    static let endBarButton = "end-bar-button"
    static let endSheet = "end-sheet"
    static let endPickMet = "end-pick-met"
    static let endPickNoShow = "end-pick-no-show"
    static let endPickUncomfortable = "end-pick-uncomfortable"
    static let endCancel = "end-cancel"
    static let endBack = "end-back"
    static let endCta = "end-cta"
    static let endSecondary = "end-secondary"
    static let endMessage = "end-message"
    static let endGuideEnd = "end-guide-end"
    static let endError = "end-error"
    static let endStay = "end-stay"
    static let endNote = "end-note"
    static let endCircle = "end-circle"
}

/// Every tap the sheets can send. The host binds them to the model; a test records them.
struct EndActions {
    var onPick: (EndOption) -> Void = { _ in }
    var onBack: () -> Void = {}
    var onClose: () -> Void = {}
    var onConfirmWait: () -> Void = {}
    var onMessage: () -> Void = {}
    var onEnd: () -> Void = {}
    var onStay: () -> Void = {}
}

// The kit's amber for the caution row, as the change sheets and the Date screen's change card draw it.
private let amber = Color(hex: 0xF59E0B)
private let amberBorder = Color(hex: 0xD97706)
private let amberIcon = Color(hex: 0xB45309)
private let amberTitle = Color(hex: 0x92400E)

// MARK: text

/// One paragraph as the reference sets it: Manrope, a CSS line box, pretty-wrapped. `runs` carry (text, bold).
private func endPara(_ runs: [(String, Bool)], size: CGFloat, lineHeight: CGFloat, color: Color, ink: Color = .liqFg,
                     quiet: Set<Int> = [], centered: Bool = false) -> PrettyText {
    var text = PrettyText(runs: runs, font: chatFont(PS.manropeRegular, size, .regular), lineHeight: size * lineHeight,
                          color: color, emphasisFont: chatFont(PS.manropeBold, size, .bold), emphasisColor: ink,
                          centered: centered)
    text.quietEmphasis = quiet
    return text
}

private func endPara(_ text: String, size: CGFloat, lineHeight: CGFloat, color: Color, centered: Bool = false) -> PrettyText {
    endPara([(text, false)], size: size, lineHeight: lineHeight, color: color, centered: centered)
}

/// Lora 700 26 / 1.1, -0.01em.
private func endTitle(_ text: String, centered: Bool) -> PrettyText {
    var title = PrettyText(text, font: chatFont(PS.loraBold, 26, .bold), lineHeight: 26 * 1.1, color: .liqFg, centered: centered)
    title.tracking = -0.01 * 26
    return title
}

// MARK: the shell

/// B to J while the model's sheet is visible, nothing otherwise. The scrim, back and swipe are the step's own back:
/// one step up, and on a menu or C a close (nothing is stored), as the reference's `onCancel` has it.
struct DateEndSheetHost: View {
    let ui: DateEndUi
    let name: String
    var zone: TimeZone = .current
    var actions = EndActions()

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        if ui.visible, let route = ui.route {
            ModalShell(
                label: sheetLabel(route, ui.step, name),
                onDismiss: { _ in
                    if scrimClosesSheet(ui.step) { actions.onClose() } else { actions.onBack() }
                },
                contentPadding: EdgeInsets()
            ) {
                ZStack {
                    Group {
                        switch route {
                        case .wrap: DateWrapSheet(ui: ui, step: ui.step, name: name, zone: zone, actions: actions)
                        case .report: ReportProblemSheet(ui: ui, step: ui.step, name: name, zone: zone, actions: actions)
                        }
                    }
                    .id("\(route.rawValue)-\(ui.step.rawValue)")
                    .transition(.opacity)
                }
                .frame(maxWidth: .infinity)
                .fitAnchor(FitAnchorID.endSheet)
            }
            .fitModal()
            // On the shell, not inside it: the sheet is as tall as its content, so a swap moves its cream edge too.
            .animation(reduceMotion ? nil : Motion.brand(Motion.progress), value: "\(route.rawValue)-\(ui.step.rawValue)")
        }
    }

    private func sheetLabel(_ route: EndRoute, _ step: EndStep, _ name: String) -> String {
        switch step {
        case .menu: return route == .wrap ? DateEndCopy.wrapTitle(name) : DateEndCopy.hTitle
        case .met: return DateEndCopy.cTitle
        case .guide: return DateEndCopy.guideTitle
        case .noShow: return DateEndCopy.gTitle
        case .uncomfortable: return DateEndCopy.iTitle
        }
    }
}

/// The screen behind the sheets, blurred as `ModalShell`'s backdrop does, arriving with the sheet.
struct DateEndOver<Content: View>: View {
    let visible: Bool
    let name: String
    var zone: TimeZone = .current
    let ui: DateEndUi
    let actions: EndActions
    @ViewBuilder let content: () -> Content

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            content()
                .blur(radius: visible ? modalShellBlur : 0)
                .animation(reduceMotion ? nil : .easeInOut(duration: Motion.sheet), value: visible)
            DateEndSheetHost(ui: ui, name: name, zone: zone, actions: actions)
        }
    }
}

// MARK: the two sheets

/// B, C and the steps they share with H.
struct DateWrapSheet: View {
    let ui: DateEndUi
    let step: EndStep
    let name: String
    let zone: TimeZone
    let actions: EndActions

    var body: some View {
        switch step {
        case .menu:
            WrapMenu(name: name, actions: actions)
        case .met:
            WrapConfirm(
                icon: .sparkles, iconTint: Color.liqSuccessFg.opacity(0.14), iconColor: .liqSuccessFg,
                title: DateEndCopy.cTitle,
                bodyText: [(DateEndCopy.cBody1(name) + "\n" + DateEndCopy.cBody2, false)],
                steps: [(.star, DateEndCopy.cStep1), (.search, DateEndCopy.cStep2)],
                cta: DateEndCopy.cCta, secondary: DateEndCopy.cancel,
                ui: ui, onCta: actions.onEnd, onBack: actions.onBack,
                note: [(DateEndCopy.cNote1, false), (DateEndCopy.cNoteBold1, true), (DateEndCopy.cNote2, false),
                       (DateEndCopy.cNoteBold2, true), (DateEndCopy.cNote3, false)])
        default:
            DateEndSharedStep(ui: ui, step: step, name: name, zone: zone, actions: actions)
        }
    }
}

/// H, and the steps it shares with B. Two options: *I'm in danger* and *Something else* are parked.
struct ReportProblemSheet: View {
    let ui: DateEndUi
    let step: EndStep
    let name: String
    let zone: TimeZone
    let actions: EndActions

    var body: some View {
        switch step {
        case .menu: ReportMenu(actions: actions)
        default: DateEndSharedStep(ui: ui, step: step, name: name, zone: zone, actions: actions)
        }
    }
}

/// The steps both sheets render: one copy each.
private struct DateEndSharedStep: View {
    let ui: DateEndUi
    let step: EndStep
    let name: String
    let zone: TimeZone
    let actions: EndActions

    var body: some View {
        switch step {
        case .guide:
            NoShowGuide(name: name, pastMeet: ui.pastMeet,
                        meetTime: ui.date.map { changeClock($0.meetAt, zone: zone) } ?? "",
                        chatOpen: ui.chatOpen, onBack: actions.onBack, onMessage: actions.onMessage,
                        onEnd: actions.onConfirmWait)
        case .noShow:
            NoShowConfirm(name: name, ui: ui, onCta: actions.onEnd, onBack: actions.onBack)
        case .uncomfortable:
            UncomfortableStep(ui: ui, onBack: actions.onBack, onHeadOut: actions.onEnd, onStay: actions.onStay)
        case .menu, .met:
            EmptyView()
        }
    }
}

// MARK: menus

/// B: padding 14 20 22; the title (4 above, 18 below), two rows gap 10, `Cancel` 14 under them.
private struct WrapMenu: View {
    let name: String
    let actions: EndActions

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Color.clear.frame(height: 4)
            SheetHeading(text: DateEndCopy.wrapTitle(name))
            Color.clear.frame(height: 18)
            VStack(spacing: 10) {
                MenuRow(anchor: FitAnchorID.endPickMet, icon: .sparkles, stroke: 2.4,
                        tile: Color.liqSuccessFg.opacity(0.12), tint: .liqSuccessFg,
                        borderColor: .liqPurple, borderWidth: 1.5,
                        title: DateEndCopy.metTitle, sub: DateEndCopy.metSub,
                        onClick: { actions.onPick(.met) })
                MenuRow(anchor: FitAnchorID.endPickNoShow, icon: .hourglass, stroke: 2.1,
                        tile: Color.liqOrange.opacity(0.12), tint: .liqOrange,
                        title: DateEndCopy.noShowTitle, sub: DateEndCopy.noShowSub,
                        onClick: { actions.onPick(.noShow) })
            }
            Color.clear.frame(height: 14)
            SheetSecondaryButton(label: DateEndCopy.cancel, anchor: FitAnchorID.endCancel, minHeight: 52, fontSize: 15,
                                 action: actions.onClose)
        }
        .padding(EdgeInsets(top: 14, leading: 20, bottom: 22, trailing: 20))
    }
}

/// H: the title (4 above, 4 below), the line (18 below), two rows gap 10, `Cancel` 14 under them.
private struct ReportMenu: View {
    let actions: EndActions

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Color.clear.frame(height: 4)
            SheetHeading(text: DateEndCopy.hTitle)
            Color.clear.frame(height: 4)
            endPara(DateEndCopy.hLine, size: 14, lineHeight: 1.45, color: .liqMuted)
            Color.clear.frame(height: 18)
            VStack(spacing: 10) {
                MenuRow(anchor: FitAnchorID.endPickNoShow, icon: .hourglass, stroke: 2.1,
                        tile: .liqRaised, tint: .liqPurple,
                        title: DateEndCopy.noShowTitle, sub: DateEndCopy.noShowSub,
                        onClick: { actions.onPick(.noShow) })
                MenuRow(anchor: FitAnchorID.endPickUncomfortable, icon: .hand, stroke: 2.1,
                        tile: amber.opacity(0.16), tint: amberIcon, titleColor: amberTitle,
                        fill: amber.opacity(0.07), borderColor: amberBorder.opacity(0.32),
                        title: DateEndCopy.uncomfortableTitle, sub: DateEndCopy.uncomfortableSub,
                        onClick: { actions.onPick(.uncomfortable) })
            }
            Color.clear.frame(height: 14)
            SheetSecondaryButton(label: DateEndCopy.cancel, anchor: FitAnchorID.endCancel, minHeight: 52, fontSize: 15,
                                 textColor: .liqFg, action: actions.onClose)
        }
        .padding(EdgeInsets(top: 14, leading: 20, bottom: 22, trailing: 20))
    }
}

private struct SheetHeading: View {
    let text: String

    var body: some View {
        endTitle(text, centered: false).accessibilityAddTraits(.isHeader)
    }
}

/// A menu row: padding 14 (plus its 1 or 1.5 border), radius 18, gap 14; the tile 46 (radius 13), the title 15.5 / 700
/// / 1.25 over the sub 12.5 / 1.4 (3 below), and `chevron-right` 18 / 2.2.
private struct MenuRow: View {
    let anchor: String
    let icon: BrandIcon
    let stroke: CGFloat
    let tile: Color
    let tint: Color
    var titleColor: Color = .liqFg
    var fill: Color = .liqElevated
    var borderColor: Color = .liqBorderSoft
    var borderWidth: CGFloat = 1
    let title: String
    let sub: String
    let onClick: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 18)
        Button(action: onClick) {
            HStack(spacing: 14) {
                BrandIconView(icon: icon, size: 22, stroke: stroke, tint: tint)
                    .frame(width: 46, height: 46)
                    .background(RoundedRectangle(cornerRadius: 13).fill(tile))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 0) {
                    endPara([(title, false)], size: 15.5, lineHeight: 1.25, color: titleColor)
                    Color.clear.frame(height: 3)
                    endPara(sub, size: 12.5, lineHeight: 1.4, color: .liqMuted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                BrandIconView(icon: .chevronRight, size: 18, stroke: 2.2, tint: .liqSubtle)
                    .accessibilityHidden(true)
            }
            .padding(14 + borderWidth)
            .frame(maxWidth: .infinity)
            .background(shape.fill(fill))
            .overlay(shape.strokeBorder(borderColor, lineWidth: borderWidth))
            .contentShape(shape)
        }
        .buttonStyle(PressScale())
        .fitTapArea()
        .fitAnchor(anchor)
    }
}

// MARK: shared pieces

/// The error box with its 14 below (`margin 0 0 14`). Date 07 passes its own `text`.
struct EndErrorBox: View {
    var text: String = DateEndCopy.endFailed

    var body: some View {
        VStack(spacing: 0) {
            SheetErrorBox(message: text)
            Color.clear.frame(height: 14)
        }
        .fitAnchor(FitAnchorID.endError)
    }
}

/// The small back link: `chevron-left` 16 / 2.4 and `Back` 13.5 / 700 in the subtle ink, drawn small, answering at 44.
struct WrapBack: View {
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                HStack(spacing: 5) {
                    BrandIconView(icon: .chevronLeft, size: 16, stroke: 2.4, tint: .liqSubtle)
                        .accessibilityHidden(true)
                    Text(DateEndCopy.back).font(F.manrope(13.5, .bold)).foregroundColor(.liqSubtle)
                }
                .padding(.horizontal, 2)
                .padding(.vertical, 4)
                .overflowTapTarget(wide: true)
            }
            .buttonStyle(PressScale())
            .fitAnchor(FitAnchorID.endBack)
            Spacer(minLength: 0)
        }
        .padding(.bottom, 8)
    }
}

/// The 72 hero circle, 4 above, with its icon.
private struct HeroCircle: View {
    let icon: BrandIcon
    let tint: Color
    let color: Color
    let iconSize: CGFloat
    let stroke: CGFloat

    var body: some View {
        BrandIconView(icon: icon, size: iconSize, stroke: stroke, tint: color)
            .frame(width: 72, height: 72)
            .background(Circle().fill(tint))
            .fitAnchor(FitAnchorID.endCircle)
            .padding(.top, 4)
            .accessibilityHidden(true)
    }
}

/// The centred title: Lora 700 26 / 1.1, 14 above and 8 below.
private struct CenteredTitle: View {
    let text: String

    var body: some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: 14)
            endTitle(text, centered: true).accessibilityAddTraits(.isHeader)
            Color.clear.frame(height: 8)
        }
    }
}

/// The centred body: 14 / 1.5 muted, at most 300, 18 below.
private struct CenteredBody: View {
    let runs: [(String, Bool)]
    var quiet: Set<Int> = []

    var body: some View {
        VStack(spacing: 0) {
            endPara(runs, size: 14, lineHeight: 1.5, color: .liqMuted, quiet: quiet, centered: true)
                .frame(maxWidth: 300)
            Color.clear.frame(height: 18)
        }
    }
}

/// The "What happens next" card: padding 14 15 (plus the 1 pt border), radius 16, raised, the label (11.5 / 800, +0.08,
/// upper-case, 10 below) over rows of `icon` 17 / 2.1 (violet, 1 down) and text 13.5 / 1.4, 11 apart; 18 below.
private struct NextCard: View {
    let steps: [(BrandIcon, String)]

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16)
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text(DateEndCopy.whatNext.uppercased())
                    .font(F.manrope(11.5, .heavy)).tracking(0.08).foregroundColor(.liqSubtle)
                Color.clear.frame(height: 10)
                ForEach(steps.indices, id: \.self) { i in
                    if i > 0 { Color.clear.frame(height: 11) }
                    HStack(alignment: .top, spacing: 11) {
                        BrandIconView(icon: steps[i].0, size: 17, stroke: 2.1, tint: .liqPurple)
                            .padding(.top, 1)
                            .accessibilityHidden(true)
                        endPara(steps[i].1, size: 13.5, lineHeight: 1.4, color: .liqFg)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(EdgeInsets(top: 15, leading: 16, bottom: 15, trailing: 16))
            .background(shape.fill(Color.liqRaised))
            .overlay(shape.strokeBorder(Color.liqBorderSoft, lineWidth: 1))
            Color.clear.frame(height: 18)
        }
    }
}

/// The shared confirmation beat: hero icon, title, body, "What happens next", an optional note, the error box above
/// the CTA, one sunset CTA (h 56) and the secondary under it (10 below). Both buttons are inert while the end is in
/// flight; the CTA then sits at alpha .6.
struct WrapConfirm: View {
    let icon: BrandIcon
    let iconTint: Color
    let iconColor: Color
    let title: String
    let bodyText: [(String, Bool)]
    let steps: [(BrandIcon, String)]
    let cta: String
    let secondary: String
    let ui: DateEndUi
    let onCta: () -> Void
    let onBack: () -> Void
    var note: [(String, Bool)]? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(spacing: 0) {
                HeroCircle(icon: icon, tint: iconTint, color: iconColor, iconSize: 30, stroke: 2.2)
                CenteredTitle(text: title)
                CenteredBody(runs: bodyText)
            }
            .frame(maxWidth: .infinity)
            NextCard(steps: steps)
            if let note {
                let shape = RoundedRectangle(cornerRadius: 16)
                HStack(alignment: .top, spacing: 9) {
                    BrandIconView(icon: .shieldAlert, size: 15, stroke: 2.2, tint: .liqOrange)
                        .padding(.top, 1)
                        .accessibilityHidden(true)
                    endPara(note, size: 12, lineHeight: 1.45, color: .liqMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(14)
                .frame(maxWidth: .infinity)
                .background(shape.fill(Color.liqOrange.opacity(0.07)))
                .overlay(shape.strokeBorder(Color.liqOrange.opacity(0.28), lineWidth: 1))
                .fitAnchor(FitAnchorID.endNote)
                Color.clear.frame(height: 14)
            }
            if ui.failed { EndErrorBox() }
            SheetPrimaryButton(label: cta, waiting: ui.ending, anchor: FitAnchorID.endCta, action: onCta)
            Color.clear.frame(height: 10)
            SheetSecondaryButton(label: secondary, inert: ui.ending, anchor: FitAnchorID.endSecondary, action: onBack)
        }
        .padding(EdgeInsets(top: 14, leading: 20, bottom: 24, trailing: 20))
    }

}

/// E and F, "Not here yet?": the back link, the orange hourglass circle, the title, four numbered cards (gap 10, 18
/// below) and the buttons. `Message {name}` is hidden while the chat is locked and the end button moves up. The end
/// button is disabled before the BINDING meet time and says when; from it, it opens G.
struct NoShowGuide: View {
    let name: String
    let pastMeet: Bool
    let meetTime: String
    let chatOpen: Bool
    let onBack: () -> Void
    let onMessage: () -> Void
    let onEnd: () -> Void

    private var cards: [[(String, Bool)]] {
        [
            [(DateEndCopy.guide1Bold(name), true), (DateEndCopy.guide1, false)],
            [(DateEndCopy.guide2A, false), (DateEndCopy.guide2Bold, true), (DateEndCopy.guide2B, false)],
            [(DateEndCopy.guide3, false)],
            [(DateEndCopy.guide4A, false), (DateEndCopy.guide4Bold, true), (DateEndCopy.guide4B, false)],
        ]
    }

    var body: some View {
        let end = guideEnd(pastMeet: pastMeet, meetTime: meetTime)
        let shape = RoundedRectangle(cornerRadius: 14)
        VStack(alignment: .leading, spacing: 0) {
            WrapBack(onBack: onBack)
            VStack(spacing: 0) {
                HeroCircle(icon: .hourglass, tint: Color.liqOrange.opacity(0.12), color: .liqOrange, iconSize: 30, stroke: 2.2)
                CenteredTitle(text: DateEndCopy.guideTitle)
            }
            .frame(maxWidth: .infinity)
            VStack(spacing: 10) {
                ForEach(cards.indices, id: \.self) { i in
                    HStack(spacing: 12) {
                        // A numeral in a fixed 30 badge keeps its size at every type size (clipped at AX otherwise).
                        Text("\(i + 1)")
                            .font(.custom(PS.loraBold, fixedSize: 15)).foregroundColor(.liqOrange)
                            .frame(width: 30, height: 30)
                            .background(Circle().fill(Color.liqElevated))
                            .overlay(Circle().strokeBorder(Color.liqBorderSoft, lineWidth: 1))
                            .accessibilityHidden(true)
                        endPara(cards[i], size: 13.5, lineHeight: 1.45, color: .liqFg)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(EdgeInsets(top: 14, leading: 15, bottom: 14, trailing: 15))
                    .frame(maxWidth: .infinity)
                    .background(shape.fill(Color.liqRaised))
                    .overlay(shape.strokeBorder(Color.liqBorderSoft, lineWidth: 1))
                }
            }
            Color.clear.frame(height: 18)
            if messageShown(chatOpen: chatOpen) {
                SheetPrimaryButton(label: DateEndCopy.message(name), anchor: FitAnchorID.endMessage,
                                   leadingIcon: .messageCircle, action: onMessage)
                Color.clear.frame(height: 10)
            }
            SheetSecondaryButton(label: end.label, anchor: FitAnchorID.endGuideEnd, locked: !end.enabled,
                                 leadingIcon: end.clock ? .clock : nil, action: onEnd)
        }
        .padding(EdgeInsets(top: 14, leading: 20, bottom: 24, trailing: 20))
    }
}

/// G, "Thanks for waiting": no verdict on the match, only the ender's own rate is promised. `Back` returns to the guide.
struct NoShowConfirm: View {
    let name: String
    let ui: DateEndUi
    let onCta: () -> Void
    let onBack: () -> Void

    var body: some View {
        WrapConfirm(
            icon: .hourglass, iconTint: Color.liqOrange.opacity(0.12), iconColor: .liqOrange,
            title: DateEndCopy.gTitle,
            bodyText: [(DateEndCopy.gBodyA(name), false), (DateEndCopy.gBodyBold, true)],
            steps: [(.star, DateEndCopy.gStep1), (.shieldCheck, DateEndCopy.gStep2), (.search, DateEndCopy.gStep3)],
            cta: DateEndCopy.gCta, secondary: DateEndCopy.back,
            ui: ui, onCta: onCta, onBack: onBack)
    }
}

/// I, "I don't feel comfortable": `Head out now` ends the date, `Stay for now` stores a flag and closes.
struct UncomfortableStep: View {
    let ui: DateEndUi
    let onBack: () -> Void
    let onHeadOut: () -> Void
    let onStay: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            WrapBack(onBack: onBack)
            VStack(spacing: 0) {
                HeroCircle(icon: .heart, tint: Color.liqPurple.opacity(0.10), color: .liqPurple, iconSize: 32, stroke: 2.1)
                CenteredTitle(text: DateEndCopy.iTitle)
                // The reference's `<b>` stays in the body's colour; only the `<strong>` takes the ink.
                CenteredBody(runs: [(DateEndCopy.iBodyA, false), (DateEndCopy.iBodyBold1, true), (DateEndCopy.iBodyB, false),
                                    (DateEndCopy.iBodyBold2, true)], quiet: [1])
            }
            .frame(maxWidth: .infinity)
            NextCard(steps: [(.shieldCheck, DateEndCopy.iStep1), (.home, DateEndCopy.iStep2), (.star, DateEndCopy.iStep3)])
            if ui.failed { EndErrorBox() }
            SheetPrimaryButton(label: DateEndCopy.headOut, waiting: ui.ending, anchor: FitAnchorID.endCta, action: onHeadOut)
            Color.clear.frame(height: 10)
            SheetSecondaryButton(label: DateEndCopy.stay, inert: ui.ending, anchor: FitAnchorID.endStay, textColor: .liqFg,
                                 action: onStay)
        }
        .padding(EdgeInsets(top: 14, leading: 20, bottom: 24, trailing: 20))
    }
}

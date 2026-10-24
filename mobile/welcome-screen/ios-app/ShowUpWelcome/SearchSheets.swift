//
//  SearchSheets.swift
//  ShowUp · the like flow's two sheets: Be sure (callout 22) and the optional message (callout 23)
//
//  The Swift twin of `search/SearchSheets.kt`. Behaviour is `SheetScaffold`'s, the one bottom sheet in
//  the app: the 42% scrim, a scrim tap as `backdrop`, the swipe down, and a bottom inset that clears
//  the keyboard. The SURFACE is Search's own `ModalShell` -- 28 corners, at most 88% of the screen, the
//  canvas colour, a 36 grabber in `--liq-fg-faint`, an upward violet shadow -- because the design draws
//  two sheet surfaces and both stay (the profile flow's is 32 / 660 / 40).
//
//  Dismissing either sheet sends nothing: every way out but `Continue`, `Skip message` and
//  `Send message` is the caller's cancel.
//

import SwiftUI
import UIKit

/// `ModalShell`'s `backdrop-filter: blur(8px)` -- NOT `blurBehindSheet`'s 1.5, the profile flow's
/// "softening". Borrowing that one put this sheet over a screen barely softened (the 184 evidence
/// review, 24 Oct 2026). iOS passes the CSS value as the radius, as the Check-In dialog's 3 does.
let searchSheetBlur: CGFloat = 8

/// CSS `line-height: normal` for Lora: its hhea ascent, descent and gap, 1006 + 274 + 0 per 1000. The
/// sheets' titles set no line height, so it is this -- not WashHeadline's default 1.05.
private let loraNormalLine: CGFloat = 1.28

/// The kit's `Button size="md"`: 48 tall, 15 type.
private let sheetButtonHeight: CGFloat = 48

/// The id at the very end of a sheet's surface, for `endOnKeyboard`. File scope because a generic
/// type cannot hold a static stored property; a `let` String, so nothing for concurrency to question.
private let searchSheetEnd = "search.sheet.end"

/// Search's `ModalShell` surface inside the app's one sheet behaviour.
struct SearchSheet<Content: View>: View {
    let label: String
    let onDismiss: (SheetDismissMethod) -> Void
    /// When the keyboard comes up, scroll to the sheet's end -- Android's `endOnKeyboard`. On a short
    /// phone the message sheet outgrows the space above the keys, and its two buttons would sit
    /// behind them until the user thought to scroll; at the end, the field, the counter and both
    /// buttons show together.
    var endOnKeyboard = false
    @ViewBuilder let content: () -> Content

    var body: some View {
        SheetScaffold(onDismiss: onDismiss) {
            GeometryReader { g in
                // `max-height: 88%` of the frame the sheet is docked in; past it the sheet scrolls.
                let cap = g.size.height * 0.88
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    ViewThatFits(in: .vertical) {
                        surface
                        ScrollViewReader { proxy in
                            ScrollView { surface }
                                .scrollBounceBehavior(.basedOnSize)
                                .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidShowNotification)) { _ in
                                    if endOnKeyboard { proxy.scrollTo(searchSheetEnd, anchor: .bottom) }
                                }
                        }
                    }
                    .frame(maxHeight: cap)
                    .background(
                        UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28)
                            .fill(Color.liqCream)
                            // `0 -8px 32px rgba(46,1,71,.18)`, cast upward over the screen.
                            .shadow(color: Color.liqShadowInk.opacity(0.18), radius: 16, y: -8)
                            // Docked to the edge: its colour runs down into the home indicator's inset.
                            .ignoresSafeArea(edges: .bottom)
                    )
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityLabel(Text(label))
        }
    }

    private var surface: some View {
        VStack(spacing: 0) {
            Capsule().fill(Color.liqFaint).frame(width: 36, height: 4)
                .padding(.top, 8)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) { content() }
                .padding(24)
            Color.clear.frame(height: 0).id(searchSheetEnd)
        }
        .frame(maxWidth: .infinity)
    }
}

/// The send mark on the sheets' primary buttons: `send` 16 / 2.2.
private struct SendMark: View {
    var body: some View { BrandIconView(icon: .send, size: 16, stroke: 2.2, tint: .white) }
}

/// Be sure (callout 22): what a match commits to, before anything is sent. The line names `today` or
/// `tomorrow` exactly as the meet card does. `Continue`, never "Send like": the like is sent from the
/// message sheet.
struct BeSureSheet: View {
    let name: String
    let day: String
    let onCancel: (SheetDismissMethod) -> Void
    let onContinue: () -> Void

    var body: some View {
        SearchSheet(label: SearchCopy.beSure, onDismiss: onCancel) {
            VStack(spacing: 0) {
                // `<em>Be sure.</em>` -- the whole phrase is the italic run, washed, in --liq-primary.
                // No letter spacing in the reference: 0, not WashHeadline's default -0.02em.
                WashHeadline(parts: [(SearchCopy.beSure, true)], fontSize: 22, lineHeightMultiple: loraNormalLine,
                             trackingEm: 0, color: .liqPrimaryDeep, centered: true)
                (Text(SearchCopy.beSureLineLead(name))
                    + Text(SearchCopy.beSureLineEmphasis(day)).font(F.manrope(14, .bold)).foregroundColor(.liqPurple)
                    + Text("."))
                    .font(F.manrope(14, .semibold))
                    .foregroundColor(.liqFg)
                    .multilineTextAlignment(.center)
                    .lineSpacing(TypeMetrics.lineSpacing(PS.manropeSemi, size: 14, multiple: 1.45))
                    .cssLineBox(14 * 1.45)
                    .padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(RoundedRectangle(cornerRadius: 14)
                .fill(LinearGradient(colors: Color.suGradBlush, startPoint: .top, endPoint: .bottom)))

            VStack(alignment: .leading, spacing: 12) {
                Text(SearchCopy.beSureSub)
                    .font(F.lora(16, italic: true))
                    .foregroundColor(.liqPurple)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                ForEach(Array(SearchCopy.beSureRows.enumerated()), id: \.offset) { _, row in
                    HStack(alignment: .top, spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 8).fill(Color.liqRaised)
                            BrandIconView(icon: row.0, size: 14, stroke: 2.2, tint: .liqPurple)
                        }
                        .frame(width: 28, height: 28)
                        .accessibilityHidden(true)
                        Text(row.1)
                            .font(F.manrope(13, .regular))
                            .foregroundColor(.liqNeutral)
                            .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 13, multiple: 1.45))
                            .cssLineBox(13 * 1.45)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .padding(.top, 18)

            ButtonPair {
                PrimaryButton(SearchCopy.cancel, variant: .ghost, height: sheetButtonHeight, labelSize: 15,
                              horizontalPadding: pairButtonPadding) { onCancel(.close) }
                PrimaryButton(label: SearchCopy.continue, variant: .sunset, height: sheetButtonHeight, labelSize: 15,
                              horizontalPadding: pairButtonPadding, action: onContinue, leading: { SendMark() })
            }
            .padding(.top, 22)
        }
    }
}

/// The optional message (callout 23). The draft arrives pre-filled and is edited here; the field never
/// takes more than 100 -- a paste is cut there. The counter is read out politely, and the text is never
/// logged or tracked.
struct MessageSheet: View {
    let name: String
    let draft: String
    let onDraft: (String) -> Void
    let onDismiss: (SheetDismissMethod) -> Void
    let onSkip: () -> Void
    let onSend: () -> Void

    var body: some View {
        SearchSheet(label: SearchCopy.messageTitleEmphasis + SearchCopy.messageTitleRest(name), onDismiss: onDismiss,
                    endOnKeyboard: true) {
            // CSS `normal`, Lora's own 1.28: at 375 the title takes two lines.
            WashHeadline(parts: [(SearchCopy.messageTitleEmphasis, true), (SearchCopy.messageTitleRest(name), false)],
                         fontSize: 26, lineHeightMultiple: loraNormalLine, trackingEm: -0.01)
                .padding(.bottom, 8)
            Text(SearchCopy.messageBody)
                .font(F.manrope(14, .regular))
                .foregroundColor(.liqMuted)
                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 14, multiple: 1.5))
                .cssLineBox(14 * 1.5)
                .padding(.bottom, 18)
            TextEditor(text: Binding(get: { draft }, set: { onDraft($0) }))
                .font(F.manrope(15, .regular))
                .foregroundColor(.liqFg)
                .tint(.liqPurple)
                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 15, multiple: 1.5))
                .scrollContentBackground(.hidden)
                // The reference's `padding: 16`, less the editor's own text inset (8 above, 5 beside).
                .padding(EdgeInsets(top: 8, leading: 11, bottom: 8, trailing: 11))
                .frame(minHeight: 120)
                .background(RoundedRectangle(cornerRadius: 16).fill(Color.liqElevated))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.liqPurple, lineWidth: 1.5))
                .accessibilityLabel(Text(SearchCopy.messageTitleEmphasis + SearchCopy.messageTitleRest(name)))
            // 10.5, not the reference's 6: its textarea is INLINE, and the browser leaves the line's
            // descender room under it -- 4.5 measured on the signed-off sheet.
            HStack {
                Spacer(minLength: 0)
                Text(messageCounter(draft)).font(F.manrope(12, .regular)).foregroundColor(.liqSubtle)
                    // The ticket's "polite live region": VoiceOver reads the new count as it changes.
                    .accessibilityAddTraits(.updatesFrequently)
            }
            .padding(.top, 10.5)
            ButtonPair {
                PrimaryButton(SearchCopy.skipMessage, variant: .ghost, height: sheetButtonHeight, labelSize: 15,
                              horizontalPadding: pairButtonPadding, action: onSkip)
                PrimaryButton(label: SearchCopy.sendMessage, variant: .sunset, height: sheetButtonHeight, labelSize: 15,
                              horizontalPadding: pairButtonPadding, action: onSend, leading: { SendMark() })
            }
            .padding(.top, 18)
        }
    }
}

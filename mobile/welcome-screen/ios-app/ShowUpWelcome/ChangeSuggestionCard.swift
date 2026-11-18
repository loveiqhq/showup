// ChangeSuggestionCard.swift
// ShowUp · the suggestion in the thread (SHOWUP-196, Date 05 C, F, H, L), `ChangeSuggestionCard` of
// `screen-change-reference.jsx`, with its numbers. Android's `ChangeSuggestionCard.kt`.
//
// A structured message, not a text one: mine right, theirs left, 84 % of the thread, radius 20, a 1.5 violet border
// at 22 %, the small shadow. The header names who and what, the value is Lora 24 with the old one struck beside it,
// and the footer is the status: waiting (with the compact binding note), the deadline box with `Suggest another`
// and `I agree`, agreed, replaced or expired. Replaced and expired sit at .55.
//
// The card holds no state: `agreeing` (the request is on its way: both buttons inert, `I agree` at .6) and
// `agreeFailed` (L: the red line, which retries) come from the chat's model, which changes nothing else until the
// server confirms. `bindingTime` and `bindingPlace` are the date's CURRENT values -- a pending suggestion moves
// neither.

import SwiftUI

struct ChangeSuggestionCard: View {
    let suggestion: ChangeSuggestion
    let name: String
    let bindingTime: Date
    let bindingPlace: String
    let zone: TimeZone
    /// The thread's width: the card is 84 % of it.
    let width: CGFloat
    let agreeing: Bool
    let agreeFailed: Bool
    let onAgree: () -> Void
    let onCounter: () -> Void

    var body: some View {
        let mine = suggestion.byYou
        let isTime = suggestion.kind == .time
        let faded = suggestion.status == .replaced || suggestion.status == .expired
        let value = isTime ? suggestion.time.map { changeClock($0, zone: zone) } ?? "" : suggestion.place?.name ?? ""
        let was = isTime ? changeClock(suggestion.wasTime, zone: zone) : suggestion.wasPlaceName ?? ""
        let deadline = changeClock(suggestion.deadline, zone: zone)
        let shape = RoundedRectangle(cornerRadius: 20)
        HStack(spacing: 0) {
            if mine { Spacer(minLength: 0) }
            VStack(alignment: .leading, spacing: 0) {
                head(mine: mine, isTime: isTime, value: value, was: was)
                footer(mine: mine, deadline: deadline)
                Rectangle().fill(Color.liqBorderSoft).frame(height: 1)
                Text(messageTime(suggestion.createdAt, zone: zone))
                    .font(F.manrope(11, .semibold)).foregroundColor(.liqSubtle)
                    .frame(maxWidth: .infinity, alignment: mine ? .trailing : .leading)
                    .padding(.horizontal, 14).padding(.vertical, 7)
            }
            .frame(width: width * 0.84, alignment: .leading)
            .background(ShadowedShape(shape: shape, fill: Color.liqElevated, shadows: shadowSm))
            .clipShape(shape)
            .overlay(shape.strokeBorder(Color.liqPurple.opacity(0.22), lineWidth: 1.5))
            .opacity(faded ? 0.55 : 1)
            // One element to VoiceOver, its two buttons still reachable on their own.
            .accessibilityElement(children: .contain)
            if !mine { Spacer(minLength: 0) }
        }
        .fitAnchor("change:card")
    }

    private func head(mine: Bool, isTime: Bool, value: String, was: String) -> some View {
        let who = mine ? ChangeCopy.cardYou : ChangeCopy.cardThey(name)
        let what = isTime ? ChangeCopy.cardTime : ChangeCopy.cardPlace
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                BrandIconView(icon: isTime ? .clock : .mapPin, size: 13, stroke: 2.4, tint: .liqPurple)
                Text("\(who) · \(what)".uppercased())
                    .font(F.manrope(11, .heavy)).tracking(0.06).foregroundColor(.liqPurple)
            }
            valueRow(value: value, was: was)
                .padding(.top, 6)
            if !isTime, let place = suggestion.place {
                Text("\(placeTypeLabel(place.type)) · \(place.address)")
                    .font(F.manrope(12.5, .regular)).foregroundColor(.liqMuted)
                    .padding(.top, 3)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(EdgeInsets(top: 12, leading: 14, bottom: 13, trailing: 14))
    }

    /// The reference's `align-items: baseline; flex-wrap: wrap; gap: 10`: the old value on the new one's baseline,
    /// dropping to its own line under a long name instead of squeezing or truncating. `ViewThatFits` takes the
    /// one-line form when both fit (the same answer the Date screen's `BaselineWrap` gives) and the stack when not.
    private func valueRow(value: String, was: String) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                valueText(value).fixedSize()
                wasText(was).fixedSize()
            }
            VStack(alignment: .leading, spacing: 10) {
                valueText(value).fixedSize(horizontal: false, vertical: true)
                wasText(was).fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func valueText(_ value: String) -> some View {
        Text(value).font(F.lora(24, bold: true)).foregroundColor(.liqFg).cssLineBox(24 * 1.1)
    }

    private func wasText(_ was: String) -> some View {
        Text(was).font(F.manrope(13, .regular)).foregroundColor(.liqSubtle).strikethrough()
    }

    @ViewBuilder private func footer(mine: Bool, deadline: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            switch suggestion.status {
            case .agreed:
                status(icon: .check, size: 15, stroke: 2.8, ink: .liqSuccessFg, font: F.manrope(13, .bold),
                       text: suggestion.answeredAt.map { ChangeCopy.agreedAt(changeClock($0, zone: zone)) } ?? ChangeCopy.agreed)
            case .replaced:
                Text(ChangeCopy.replaced).font(F.manrope(12.5, .semibold)).foregroundColor(.liqMuted)
            case .expired:
                status(icon: .clock, size: 13, stroke: 2.4, ink: .liqMuted, font: F.manrope(12.5, .bold),
                       text: ChangeCopy.expired(deadline))
            case .pending:
                if mine {
                    status(icon: .clock, size: 13, stroke: 2.4, ink: .liqMuted, font: F.manrope(12.5, .bold),
                           text: ChangeCopy.waitingFor(name))
                    BindingNote(name: name, time: changeClock(bindingTime, zone: zone), place: bindingPlace,
                                deadline: deadline, compact: true)
                } else {
                    DeadlineBox(deadline: deadline, time: changeClock(bindingTime, zone: zone), place: bindingPlace)
                    buttons
                    if agreeFailed { ChangeRetryLine(message: ChangeCopy.agreeFailed, onRetry: onAgree) }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(EdgeInsets(top: 0, leading: 14, bottom: 13, trailing: 14))
    }

    private func status(icon: BrandIcon, size: CGFloat, stroke: CGFloat, ink: Color, font: Font, text: String) -> some View {
        HStack(spacing: 6) {
            BrandIconView(icon: icon, size: size, stroke: stroke, tint: ink)
            Text(text).font(font).foregroundColor(ink)
        }
    }

    /// `Suggest another` first, `I agree` second: h 44 pills, gap 8. While `agreeing` both are inert and `I agree`
    /// is at .6.
    private var buttons: some View {
        HStack(spacing: 8) {
            Button(action: onCounter) {
                Text(ChangeCopy.suggestAnother).font(F.manrope(13.5, .bold)).foregroundColor(.liqFg)
                    .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
                    .background(Capsule().fill(Color.liqElevated))
                    .overlay(Capsule().strokeBorder(Color.liqBorder, lineWidth: 1))
            }
            .buttonStyle(PressScale())
            .fitTapArea()
            .disabled(agreeing)
            Button(action: onAgree) {
                Text(ChangeCopy.iAgree).font(F.manrope(14, .bold)).foregroundColor(.white)
                    .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
                    .background(SunsetFill(shape: Capsule(), shadows: shadowCta))
                    .opacity(agreeing ? 0.6 : 1)
            }
            .buttonStyle(PressScale())
            .fitTapArea()
            .disabled(agreeing)
        }
    }
}

/// The amber box: `Answer by {deadline}` and what stays binding until then. Shared with the request card on the Date
/// screen, which draws the same box (the ticket: "deadline box as on the card above").
struct DeadlineBox: View {
    let deadline: String
    let time: String
    let place: String

    private static let ink = Color(hex: 0x78350F)
    private static let body = Color(hex: 0x92400E)
    private static let icon = Color(hex: 0xB45309)

    var body: some View {
        HStack(alignment: .top, spacing: 9) {
            BrandIconView(icon: .clock, size: 15, stroke: 2.4, tint: Self.icon).padding(.top, 1)
            VStack(alignment: .leading, spacing: 2) {
                Text(ChangeCopy.answerBy(deadline)).font(F.manrope(13.5, .bold)).foregroundColor(Self.ink)
                PrettyText(runs: [(ChangeCopy.untilYouAgree, false), (ChangeCopy.untilBinding(time: time, place: place), true),
                                  (ChangeCopy.untilRest, false)],
                           font: chatFont(PS.manropeMedium, 12.5, .medium), lineHeight: 12.5 * 1.4, color: Self.body,
                           emphasisFont: chatFont(PS.manropeBold, 12.5, .bold), emphasisColor: Self.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(EdgeInsets(top: 9, leading: 11, bottom: 9, trailing: 11))
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0xF59E0B).opacity(0.09)))
        .accessibilityElement(children: .combine)
    }
}

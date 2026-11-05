// DateCards.swift
// ShowUp · the Date screen's cards (SHOWUP-193 ② to ⑭, ⑱)
//
// Each card is `ScreenMatchCoord`'s, from `screen-date-reference.jsx`, with its numbers; Android's
// `DateCards.kt` is the twin, card for card. Where CSS counts a border in a box's size and SwiftUI only
// draws it, the padding here is the reference's plus the border -- the comment says so each time.

import SwiftUI

// MARK: - tones and small parts

/// The reference's `REASON_TONES`: ink, panel fill, chip border.
struct ToneColors {
    let fg: Color
    let bg: Color
    let border: Color
}

func toneColors(_ tone: ReasonTone) -> ToneColors {
    switch tone {
    case .violet: return ToneColors(fg: .liqPurple, bg: Color.liqPurple.opacity(0.10), border: Color.liqPurple.opacity(0.20))
    case .orange: return ToneColors(fg: .liqOrange, bg: Color.liqOrange.opacity(0.11), border: Color.liqOrange.opacity(0.22))
    case .danger: return ToneColors(fg: .liqDangerFg, bg: Color.liqDanger.opacity(0.10), border: Color.liqDanger.opacity(0.22))
    case .darkRed: return ToneColors(fg: .liqReasonDarkRed, bg: Color.liqReasonDarkRed.opacity(0.09),
                                     border: Color.liqReasonDarkRed.opacity(0.28))
    }
}

/// One layer of a CSS `box-shadow`, as Card.swift draws `--liq-shadow-md`: each layer on its own copy of
/// the shape, so neither is a shadow of the other. A SwiftUI shadow radius is the CSS blur / 2.
struct ShadowLayer {
    let color: Color
    let y: CGFloat
    let blur: CGFloat
}

/// `--liq-shadow-cta`: `0 8 20` at .32 and `0 2 6` at .20 of the CTA orange.
let shadowCta = [ShadowLayer(color: Color.liqOrange.opacity(0.32), y: 8, blur: 20),
                 ShadowLayer(color: Color.liqOrange.opacity(0.20), y: 2, blur: 6)]
/// `--liq-shadow-violet`: `0 8 20` at .28 and `0 2 6` at .18 of the violet.
let shadowViolet = [ShadowLayer(color: Color.liqPurple.opacity(0.28), y: 8, blur: 20),
                    ShadowLayer(color: Color.liqPurple.opacity(0.18), y: 2, blur: 6)]

/// A full pill filled with `fill` under `shadows`.
struct ShadowedPill<Fill: ShapeStyle>: View {
    let fill: Fill
    let shadows: [ShadowLayer]

    var body: some View {
        ZStack {
            Capsule().fill(fill)
            ForEach(shadows.indices, id: \.self) { i in
                Capsule().fill(fill).shadow(color: shadows[i].color, radius: shadows[i].blur / 2, y: shadows[i].y)
            }
        }
    }
}

/// A square icon tile: the reference's `width/height N, borderRadius r, background, centred icon`.
struct IconTile<Fill: ShapeStyle>: View {
    let size: CGFloat
    let radius: CGFloat
    let fill: Fill
    let icon: BrandIcon
    let iconSize: CGFloat
    let tint: Color
    let stroke: CGFloat

    var body: some View {
        BrandIconView(icon: icon, size: iconSize, stroke: stroke, tint: tint)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: radius).fill(fill))
            .accessibilityHidden(true)
    }
}

/// A Lora / Manrope UIFont for `PrettyText`, which lays out through UIKit.
private func uiFont(_ name: String, _ size: CGFloat, _ weight: UIFont.Weight) -> UIFont {
    TypeMetrics.uiFont(name, size, fallback: .systemFont(ofSize: size, weight: weight))
}

// MARK: - ② hero

/// The match's first photo, h 196, radius 24, `object-position: center 24%`, with `It's a date.` --
/// Lora 700 italic 30, −0.02em, white, centred, 14 from the top, `text-shadow: 0 2 12 rgba(0,0,0,.18)`.
struct DateHero: View {
    let photoUrl: URL?

    var body: some View {
        ZStack(alignment: .top) {
            PhotoBackdrop(url: photoUrl, index: 0, focusTop24: true)
            Text(DateCopy.hero)
                .font(F.lora(30, bold: true, italic: true))
                .tracking(-0.02 * 30)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .shadow(color: Color.black.opacity(0.18), radius: 6, y: 2)
                .frame(maxWidth: .infinity)
                .padding(.top, 14)
                .accessibilityAddTraits(.isHeader)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 196)
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }
}

// MARK: - ③ time + place

/// A label and a value on one baseline, gap 8, the value dropping to its own line when the two do not
/// fit -- `display: flex; align-items: baseline; flex-wrap: wrap; gap: 8`. A long place name wraps.
private struct BaselineWrap: View {
    let label: String
    let value: String

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 8) { labelText; valueText.fixedSize() }
            VStack(alignment: .leading, spacing: 8) {
                Text(label).font(F.manrope(14, .bold)).tracking(0.02).foregroundColor(.liqPurple)
                    .fixedSize(horizontal: false, vertical: true)
                valueText.fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var labelText: some View {
        Text(label).font(F.manrope(14, .bold)).tracking(0.02).foregroundColor(.liqPurple).fixedSize()
    }

    private var valueText: some View {
        Text(value).font(F.lora(20, bold: true)).foregroundColor(.liqFg)
    }
}

/// ③ The time + place card: `#E8DCF5`, padding 13 / 14, the latest AGREED time and place.
struct TimePlaceCard: View {
    let date: ActiveDate
    let zone: TimeZone
    let copied: Bool
    let onCopy: () -> Void

    var body: some View {
        let headline = meetingHeadline(name: date.match.name, day: date.meetDay)
        // 13 / 14 inside the card's 1 px border.
        Card(fill: AnyShapeStyle(Color.liqLilacCard), padding: EdgeInsets(top: 14, leading: 15, bottom: 14, trailing: 15)) {
            HStack(alignment: .center, spacing: 9) {
                IconTile(size: 26, radius: 9, fill: Color.liqPurple, icon: .heartFilled, iconSize: 14, tint: .white, stroke: 2)
                // Two lines of Lora 20 / 1.15 -- tighter than the font, which only UIKit sets.
                PrettyText(runs: [(headline.lead, false), (headline.day, true)],
                           font: uiFont(PS.loraBold, 20, .bold), lineHeight: 20 * 1.15, color: .liqFg,
                           emphasisFont: uiFont(PS.loraBoldItalic, 20, .bold), emphasisColor: .liqPurple,
                           pretty: false)
                    .accessibilityAddTraits(.isHeader)
            }
            .padding(.bottom, 13)
            HStack(alignment: .top, spacing: 12) {
                IconTile(size: 40, radius: 13, fill: Color.liqElevated, icon: .clock, iconSize: 19, tint: .liqPurple, stroke: 2.2)
                BaselineWrap(label: DateCopy.meetingTime, value: meetTime(date.meetAt, zone: zone))
            }
            if let place = date.place {
                Rectangle().fill(Color.liqFg.opacity(0.10)).frame(height: 1).padding(.vertical, 10)
                HStack(alignment: .top, spacing: 12) {
                    IconTile(size: 40, radius: 13, fill: Color.liqElevated, icon: .mapPin, iconSize: 19, tint: .liqPurple, stroke: 2.2)
                    VStack(alignment: .leading, spacing: 0) {
                        BaselineWrap(label: DateCopy.meetingPlace, value: place.name)
                        HStack(alignment: .center, spacing: 8) {
                            if let address = place.address {
                                Text(address)
                                    .font(F.manrope(12.5, .regular))
                                    .foregroundColor(.liqMuted)
                                    .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 12.5, multiple: 1.35))
                                    .fixedSize(horizontal: false, vertical: true)
                                    .cssLineBox(12.5 * 1.35)
                            }
                            CopyAddress(copied: copied, onCopy: onCopy)
                        }
                        .padding(.top, 3)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }
}

/// ④ The Copy pill: padding 4 10 4 8, white, 1 px border-soft, violet 11.5 / 700, copy icon 13.
/// `Copied` + check in the success tint while `copied`; the colours cross over 160 ms. No toast.
/// Drawn at its own size, answering at 44 (`overflowTapTarget`): the floor grew the time card by 18.
struct CopyAddress: View {
    let copied: Bool
    let onCopy: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button(action: onCopy) {
            HStack(spacing: 5) {
                BrandIconView(icon: copied ? .check : .copy, size: 13, stroke: 2.4, tint: ink)
                Text(copied ? DateCopy.copied : DateCopy.copy)
                    .font(F.manrope(11.5, .bold))
                    .foregroundColor(ink)
                    .fixedSize()
            }
            // 4 10 4 8 inside the 1 px border, which CSS counts in the pill's size.
            .padding(EdgeInsets(top: 5, leading: 9, bottom: 5, trailing: 11))
            .background(Capsule().fill(copied ? Color.liqSuccess.opacity(0.14) : Color.liqElevated))
            .overlay(Capsule().strokeBorder(copied ? Color.liqSuccess.opacity(0.32) : Color.liqBorderSoft, lineWidth: 1))
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.16), value: copied)
            .overflowTapTarget()
        }
        .buttonStyle(PressScale())
        .fixedSize()
        .accessibilityLabel(Text(copied ? DateCopy.copied : DateCopy.copy))
    }

    private var ink: Color { copied ? .liqSuccessFg : .liqPurple }
}

// MARK: - ⑥ ⑦ ⑧ Coordinate

/// A 48-tall pill CTA with a message icon -- `Open chat` and `Read & reply`.
private struct CoordinateCta: View {
    let label: String
    let fill: Color
    let shadows: [ShadowLayer]
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                BrandIconView(icon: .messageCircle, size: 18, stroke: 2.2, tint: .white)
                Text(label).font(F.manrope(15, .bold)).foregroundColor(.white)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(ShadowedPill(fill: fill, shadows: shadows))
            .fitTapArea()
        }
        .buttonStyle(PressScale())
    }
}

private func coordinateTitle(_ name: String) -> some View {
    Text(DateCopy.coordTitle(name))
        .font(F.lora(20, bold: true))
        .foregroundColor(.liqFg)
        .fixedSize(horizontal: false, vertical: true)
        .cssLineBox(20 * 1.1)
}

/// The sub line: 14 / 1.35 muted, `textWrap: 'pretty'` -- the sheet's A reads `… before` / `you meet`.
private func coordinateSub(_ text: String) -> some View {
    PrettyText(text, font: uiFont(PS.manropeRegular, 14, .regular), lineHeight: 14 * 1.35, color: .liqMuted)
}

/// ⑥ locked · ⑦ available · ⑧ unread: ONE card in three states, never a second one. The state is the
/// screen's (`dateState`); this draws it.
struct CoordinateModule: View {
    let state: DateState
    let date: ActiveDate
    let now: Date
    let zone: TimeZone
    let onOpenChat: () -> Void

    var body: some View {
        if state == .unread, let unread = date.unread {
            UnreadCoordinate(match: date.match, unread: unread, now: now, zone: zone, onOpenChat: onOpenChat)
        } else {
            // 18 inside the 1.5 border.
            Card(borderColor: .liqLavender100, borderWidth: 1.5,
                 padding: EdgeInsets(top: 19.5, leading: 19.5, bottom: 19.5, trailing: 19.5)) {
                if state == .locked {
                    HStack(alignment: .center, spacing: 14) {
                        IconTile(size: 48, radius: 14, fill: Color.liqPurple.opacity(0.10), icon: .messageCircle,
                                 iconSize: 22, tint: .liqPurple, stroke: 2.1)
                        VStack(alignment: .leading, spacing: 4) {
                            coordinateTitle(date.match.name)
                            coordinateSub(DateCopy.coordLockedSub)
                        }
                        // The column takes the width it is given, as Android's `weight(1f)`: a word wider than
                        // it (a long name at a phone's exact width) wraps or overflows by a hair -- it must
                        // never widen the card (the 402-wide fit sweep, 3 Nov 2026).
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    // No progress bar (2 Nov 2026): the time alone says it. Not tappable.
                    Text(lockedLine(meetDay: date.meetDay, unlockAt: date.unlockAt, zone: zone))
                        .font(F.manrope(14, .bold))
                        .foregroundColor(.liqPurple)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 14)
                } else {
                    HStack(alignment: .top, spacing: 14) {
                        IconTile(size: 48, radius: 14, fill: Color.liqPurple, icon: .messageCircle,
                                 iconSize: 22, tint: .white, stroke: 2.1)
                        VStack(alignment: .leading, spacing: 5) {
                            HStack(alignment: .center, spacing: 9) {
                                Circle().fill(Color.liqSuccess).frame(width: 9, height: 9).accessibilityHidden(true)
                                coordinateTitle(date.match.name)
                            }
                            coordinateSub(DateCopy.coordOpenSub(date.match.name))
                        }
                        // The column takes the width it is given, as Android's `weight(1f)`: a word wider than
                        // it (a long name at a phone's exact width) wraps or overflows by a hair -- it must
                        // never widen the card (the 402-wide fit sweep, 3 Nov 2026).
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    CoordinateCta(label: DateCopy.openChat, fill: .liqPurple, shadows: shadowViolet, action: onOpenChat)
                        .padding(.top, 16)
                }
            }
        }
    }
}

/// ⑧ The unread card: the reason's tone on a 1.5 border and a 4 rail, the avatar with its count, the
/// tag, `needs a response · {time}`, the preview clamped to two lines, and `Read & reply` in the tone.
private struct UnreadCoordinate: View {
    let match: DateMatch
    let unread: DateUnread
    let now: Date
    let zone: TimeZone
    let onOpenChat: () -> Void

    var body: some View {
        let style = chatReasonStyle(unread.reason)
        let tone = toneColors(style.tone)
        // The rail and the body sit inside the 1.5 border, as CSS's `overflow: hidden` card has them.
        Card(borderColor: tone.border, borderWidth: 1.5, padding: EdgeInsets(top: 1.5, leading: 1.5, bottom: 1.5, trailing: 1.5)) {
            UnevenRoundedRectangle(topLeadingRadius: 18.5, topTrailingRadius: 18.5)
                .fill(tone.fg)
                .frame(height: 4)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center, spacing: 12) {
                    MatchAvatar(match: match, count: unread.count, tone: tone.fg)
                    VStack(alignment: .leading, spacing: 2) {
                        ViewThatFits(in: .horizontal) {
                            HStack(alignment: .center, spacing: 8) { name; tag(tone) }
                            VStack(alignment: .leading, spacing: 4) { name; tag(tone) }
                        }
                        Text(DateCopy.needsResponse(unreadTime(sentAt: unread.sentAt, now: now, zone: zone)))
                            .font(F.manrope(12.5, .regular))
                            .foregroundColor(.liqMuted)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                VStack(alignment: .leading, spacing: 7) {
                    ReasonBadge(style: style, onLight: true)
                    if let body = unread.body {
                        // `textWrap: 'pretty'` before the 2-line clamp: the sheet's `… sorry!` / `Almost there.”`.
                        PrettyText("“\(body)”", font: uiFont(PS.manropeMedium, 14, .medium), lineHeight: 14 * 1.4,
                                   color: .liqFg)
                            .lines(2)
                    }
                }
                .padding(EdgeInsets(top: 11, leading: 13, bottom: 11, trailing: 13))
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 14).fill(tone.bg))
                .padding(.top, 13)
                CoordinateCta(label: DateCopy.readAndReply, fill: tone.fg,
                              shadows: style.tone == .orange ? shadowCta : (style.tone == .danger ? [] : shadowViolet),
                              action: onOpenChat)
                    .padding(.top, 14)
            }
            .padding(16)
        }
    }

    private var name: some View {
        Text(match.name).font(F.lora(19, bold: true)).foregroundColor(.liqFg).cssLineBox(19 * 1.1)
    }

    private func tag(_ tone: ToneColors) -> some View {
        HStack(spacing: 4) {
            Circle().fill(tone.fg).frame(width: 5, height: 5)
            Text(unreadTag(unread.count).uppercased())
                .font(F.manrope(10, .heavy))
                .tracking(0.05)
                .foregroundColor(tone.fg)
                .lineLimit(1)
                .fixedSize()
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 2)
        .background(Capsule().fill(tone.bg))
    }
}

/// The reference's `ReasonBadge`: uppercase reason label with its icon, in the tone's tint.
struct ReasonBadge: View {
    let style: ChatReasonStyle
    let onLight: Bool

    var body: some View {
        let tone = toneColors(style.tone)
        HStack(spacing: 5) {
            BrandIconView(icon: style.icon, size: 11, stroke: 2.5, tint: onLight ? tone.fg : .white)
            // One line wherever it fits; at the largest type on a narrow phone it wraps rather than runs
            // past the panel.
            Text(style.label.uppercased())
                .font(F.manrope(10.5, .heavy))
                .tracking(0.05)
                .foregroundColor(onLight ? tone.fg : .white)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(EdgeInsets(top: 3, leading: 7, bottom: 3, trailing: 9))
        .background(Capsule().fill(onLight ? tone.bg : Color.white.opacity(0.22)))
    }
}

/// The pulse ring's two values over one beat.
private struct PulseRing {
    var scale: CGFloat = 1
    var opacity: Double = 0.55
}

/// The kit's `Avatar` at 46 with the unread count badge (18, the tone's fill, a 2.5 white ring) and the
/// pulsing ring behind it (`su-pulse-ring`: 1 → 2.6 scale, .55 → 0 by 70%, 1.8 s, the brand curve).
/// Under reduced motion the ring is not drawn.
private struct MatchAvatar: View {
    let match: DateMatch
    let count: Int
    let tone: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack(alignment: .topTrailing) {
            ZStack {
                PhotoBackdrop(url: match.photoUrl, index: 0)
                if match.photoUrl == nil {
                    Text(String(match.name.prefix(1)))
                        .font(F.lora(46 * 0.42, bold: true))
                        .foregroundColor(Color.white.opacity(0.92))
                }
            }
            .frame(width: 46, height: 46)
            .clipShape(Circle())
            .aboveWash(.circle)
            if !reduceMotion {
                Circle()
                    .fill(tone)
                    .frame(width: 18, height: 18)
                    .keyframeAnimator(initialValue: PulseRing(), repeating: true) { ring, v in
                        ring.scaleEffect(v.scale).opacity(v.opacity)
                    } keyframes: { _ in
                        let rise = Motion.pulseRing * 0.7
                        let curve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.22, y: 1),
                                                     endControlPoint: UnitPoint(x: 0.36, y: 1))
                        KeyframeTrack(\.scale) {
                            LinearKeyframe(2.6, duration: rise, timingCurve: curve)
                            LinearKeyframe(2.6, duration: Motion.pulseRing - rise)
                        }
                        KeyframeTrack(\.opacity) {
                            LinearKeyframe(0, duration: rise, timingCurve: curve)
                            LinearKeyframe(0, duration: Motion.pulseRing - rise)
                        }
                    }
                    .offset(x: 3 - 2.5, y: -3 + 2.5)
                    .accessibilityHidden(true)
            }
            // A fixed-size mark, as the TabBar's badge: the count is spoken in the card's text.
            Text("\(count)")
                .font(F.manrope(10.5, .heavy))
                .foregroundColor(.white)
                .lineLimit(1)
                .frame(width: 18, height: 18)
                .background(Circle().fill(tone))
                .padding(2.5)
                .background(Circle().fill(Color.white))
                .aboveWash(.circle)
                .offset(x: 3, y: -3)
                .accessibilityHidden(true)
        }
        .frame(width: 46, height: 46)
    }
}

// MARK: - ⑨ Report a problem

/// ⑨ Always present while the date is active. Danger 6 % fill, 1 px danger 20 %, shield tile 40.
struct ReportRow: View {
    let onReport: () -> Void

    var body: some View {
        Button(action: onReport) {
            HStack(alignment: .center, spacing: 13) {
                IconTile(size: 40, radius: 12, fill: Color.liqElevated, icon: .shield, iconSize: 19, tint: .liqDanger, stroke: 2.1)
                (Text(DateCopy.reportTitle).font(F.manrope(13.5, .bold)).foregroundColor(.liqFg)
                    + Text(" " + DateCopy.reportBody).font(F.manrope(13.5, .regular)).foregroundColor(.liqMuted))
                    .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 13.5, multiple: 1.4))
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .cssLineBox(13.5 * 1.4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                BrandIconView(icon: .chevronRight, size: 18, stroke: 2.2, tint: .liqSubtle)
            }
            // 13 / 15 inside the 1 px border.
            .padding(EdgeInsets(top: 14, leading: 16, bottom: 14, trailing: 16))
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.liqDanger.opacity(0.06)))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.liqDanger.opacity(0.20), lineWidth: 1))
            .fitTapArea()
        }
        .buttonStyle(PressScale())
    }
}

// MARK: - ⑩ intent + Show-up count

private func eyebrow(_ text: String, size: CGFloat = 11) -> some View {
    Text(text.uppercased())
        .font(F.manrope(size, .bold))
        .tracking(0.08)
        .foregroundColor(.liqSubtle)
}

/// A shared value as a selected chip, with the icon the sheet draws (`vibeIcon`, `commonChips`).
private func sharedChip(_ label: String, icon: BrandIcon?, tone: ChipTone, size: ChipSize, iconSize: CGFloat,
                        stroke: CGFloat) -> some View {
    Chip(label: label, selected: true, tone: tone, size: size) { ink in
        if let icon { BrandIconView(icon: icon, size: iconSize, stroke: stroke, tint: ink) }
    }
}

/// ⑩ Only what matches: the intent line when both motivations are equal, otherwise the row keeps the
/// count under `{name}'s Show-up`; `You both want` with the shared vibes only, absent when none.
struct IntentCard: View {
    let date: ActiveDate

    var body: some View {
        let shared = intentShared(date.motivationYou, date.motivationThem)
        // 11 / 13 inside the 1 px border.
        Card(padding: EdgeInsets(top: 12, leading: 14, bottom: 12, trailing: 14)) {
            VStack(alignment: .leading, spacing: 8) {
                // The count beside the intent where both fit on one line; under it where they do not
                // (the largest type, or the narrowest phone) -- fitted to the card, never past it.
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .center, spacing: 12) {
                        intentLine(shared)
                        ShowUpCount(count: date.match.attendedCount, size: .sm)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        intentLine(shared)
                        ShowUpCount(count: date.match.attendedCount, size: .sm, fitWidth: .infinity)
                    }
                }
                if !date.sharedVibes.isEmpty {
                    Rectangle().fill(Color.liqBorderSoft).frame(height: 1)
                    VStack(alignment: .leading, spacing: 5) {
                        eyebrow(DateCopy.youBothWant)
                        WrapLayout(spacing: 6, lineSpacing: 6) {
                            ForEach(date.sharedVibes, id: \.self) { id in
                                sharedChip(vibeLabel(id) ?? id, icon: vibeIcon(id), tone: .orange, size: .sm, iconSize: 11, stroke: 2.4)
                            }
                        }
                    }
                }
            }
        }
    }
}

extension IntentCard {
    /// The heart (shared) or the shield, and the eyebrow over the shared motivation.
    fileprivate func intentLine(_ shared: Bool) -> some View {
        HStack(alignment: .center, spacing: 12) {
            if shared {
                BrandIconView(icon: .heartFilled, size: 20, tint: .liqOrange)
            } else {
                BrandIconView(icon: .shieldCheck, size: 20, tint: .liqPurple)
            }
            VStack(alignment: .leading, spacing: 0) {
                eyebrow(shared ? DateCopy.mutualIntent : DateCopy.theirShowUp(date.match.name))
                if shared {
                    Text(motivationOf(date.motivationThem)?.title ?? date.motivationThem ?? "")
                        .font(F.manrope(15, .semibold))
                        .foregroundColor(.liqFg)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

// MARK: - ⑪ quick actions

/// ⑪ Three tiles, gap 8 (Order Ride hidden until a ride-app API exists). Directions needs a place, so
/// with none there are two.
struct QuickActions: View {
    let hasPlace: Bool
    let onDirections: () -> Void
    let onShare: () -> Void
    let onCalendar: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            if hasPlace { tile(.navigation, DateCopy.directions, onDirections) }
            tile(.share, DateCopy.shareDate, onShare)
            tile(.calendar, DateCopy.calendar, onCalendar)
        }
    }

    private func tile(_ icon: BrandIcon, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                BrandIconView(icon: icon, size: 18, stroke: 2, tint: .liqPurple)
                // Wraps between words (`Share / Date`) at a large type on a narrow phone; a single word
                // wider than the tile (`Directions` at the largest size) shrinks rather than breaks.
                Text(label)
                    .font(F.manrope(12, .semibold))
                    .foregroundColor(.liqFg)
                    .multilineTextAlignment(.center)
                    .lineLimit(label.contains(" ") ? 2 : 1)
                    .minimumScaleFactor(0.5)
            }
            // 14 / 0 inside the 1 px border.
            .padding(EdgeInsets(top: 15, leading: 1, bottom: 15, trailing: 1))
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.liqElevated))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.liqBorderSoft, lineWidth: 1))
            .fitTapArea()
        }
        .buttonStyle(PressScale())
    }
}

// MARK: - ⑫ About

/// ⑫ The mini profile: photo 92 r16, `{name}, {age}`, area · distance, `ShowUpCount` xs, and the link.
struct AboutCard: View {
    let match: DateMatch
    let onSeeProfile: () -> Void

    var body: some View {
        // Padding 0, its contents inside the 1 px border.
        Card(padding: EdgeInsets(top: 1, leading: 1, bottom: 1, trailing: 1)) {
            HStack(alignment: .center, spacing: 14) {
                PhotoBackdrop(url: match.photoUrl, index: 0)
                    .frame(width: 92, height: 92)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 6) {
                    Text(match.age.map { DateCopy.nameAge(match.name, $0) } ?? match.name)
                        .font(F.lora(20, bold: true))
                        .foregroundColor(.liqFg)
                    if let line = aboutLine(district: match.district, distanceKm: match.distanceKm) {
                        Text(line).font(F.manrope(12, .medium)).foregroundColor(.liqSubtle)
                    }
                    // Fitted to the column: one line where it fits, wrapped (or a long word shrunk) where not.
                    ShowUpCount(count: match.attendedCount, size: .xs, fitWidth: .infinity)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(14)
            // Drawn 43 tall, as the reference's 12 / 12 around a 13 line under its 1 px `border-top`,
            // which CSS counts in the height -- hence 13 above. It answers at 44.
            Button(action: onSeeProfile) {
                HStack(spacing: 6) {
                    Text(DateCopy.seeProfile(match.name)).font(F.manrope(13, .semibold)).foregroundColor(.liqPurple)
                    BrandIconView(icon: .chevronRight, size: 14, stroke: 2.4, tint: .liqPurple)
                }
                .frame(maxWidth: .infinity)
                .padding(EdgeInsets(top: 13, leading: 16, bottom: 12, trailing: 16))
                .background(Color.liqRaised)
                .overlay(alignment: .top) { Rectangle().fill(Color.liqBorderSoft).frame(height: 1) }
                .overflowTapTarget()
            }
            .buttonStyle(PressScale())
            .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 19, bottomTrailingRadius: 19))
        }
    }
}

// MARK: - ⑬ in common

/// ⑬ Only what both filled in: the motivation when it is shared, then the shared interests. No overlap,
/// no card (Search's rule).
struct CommonCard: View {
    let date: ActiveDate

    var body: some View {
        let chips = commonChips(date)
        if !chips.isEmpty {
            // 16 inside the 1 px border.
            Card(padding: EdgeInsets(top: 17, leading: 17, bottom: 17, trailing: 17)) {
                eyebrow(DateCopy.inCommon, size: 13).padding(.bottom, 10)
                WrapLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(chips.indices, id: \.self) { i in
                        sharedChip(chips[i].label, icon: chips[i].icon, tone: .lavender, size: .md, iconSize: 13, stroke: 2.2)
                    }
                }
            }
        }
    }
}

// MARK: - ⑭ How the date works

/// ⑭ Rewritten 2 Nov 2026 to what the app does: no timer, no "halfway". Four rows, gap 14.
struct DateWorksCard: View {
    let date: ActiveDate
    let zone: TimeZone

    var body: some View {
        let rows: [(BrandIcon, String, String)] = [
            (.mapPin, worksMeetLine(date, zone: zone), DateCopy.worksMeetSub),
            (.navigation, DateCopy.worksCheckIn, DateCopy.worksCheckInSub(date.match.name)),
            (.clock, DateCopy.worksThirty, DateCopy.worksThirtySub),
            (.check, DateCopy.worksEnd, DateCopy.worksEndSub),
        ]
        // 16 inside the 1 px border.
        Card(padding: EdgeInsets(top: 17, leading: 17, bottom: 17, trailing: 17)) {
            Text(DateCopy.worksTitle)
                .font(F.lora(18, bold: true))
                .foregroundColor(.liqFg)
                .accessibilityAddTraits(.isHeader)
                .padding(.bottom, 14)
            VStack(alignment: .leading, spacing: 14) {
                ForEach(rows.indices, id: \.self) { i in
                    HStack(alignment: .top, spacing: 14) {
                        IconTile(size: 32, radius: 10,
                                 fill: LinearGradient(colors: Color.suGradBlush, startPoint: .top, endPoint: .bottom),
                                 icon: rows[i].0, iconSize: 16, tint: .liqOrange, stroke: 2.2)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(rows[i].1).font(F.manrope(15, .bold)).foregroundColor(.liqFg)
                                .fixedSize(horizontal: false, vertical: true)
                            Text(rows[i].2)
                                .font(F.manrope(13, .regular))
                                .foregroundColor(.liqMuted)
                                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 13, multiple: 1.4))
                                .fixedSize(horizontal: false, vertical: true)
                                .cssLineBox(13 * 1.4)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
    }
}

// MARK: - ⑱ Cancel this date

/// ⑱ The link at the end of the scroll: danger 13 / 600, centred, padding 10 0. Date 07's sheet.
struct CancelLink: View {
    let onCancel: () -> Void

    var body: some View {
        Button(action: onCancel) {
            Text(DateCopy.cancelDate)
                .font(F.manrope(13, .semibold))
                .foregroundColor(.liqDanger)
                .padding(.vertical, 10)
                .overflowTapTarget()
        }
        .buttonStyle(PressScale())
        .frame(maxWidth: .infinity)
    }
}

// DateCards.swift
// ShowUp · the Date screen's cards (SHOWUP-193 ② to ⑭, ⑱)
//
// Each card is `ScreenMatchCoord`'s, from `screen-date-reference.jsx`, with its numbers; Android's
// `DateCards.kt` is the twin, card for card. Where CSS counts a border in a box's size and SwiftUI only
// draws it, the padding here is the reference's plus the border -- the comment says so each time.

import SwiftUI

// MARK: - tones and small parts

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

/// `--liq-shadow-sm`: `0 1 2` at .04 of the shadow ink -- the outlined buttons' and chips' lift (SHOWUP-194).
let shadowSm = [ShadowLayer(color: Color.liqShadowInk.opacity(0.04), y: 1, blur: 2)]
/// `--liq-shadow-md`: `0 4 12` at .06 and `0 1 3` at .04 of the shadow ink -- the kit's `Card` (SHOWUP-194).
let shadowMd = [ShadowLayer(color: Color.liqShadowInk.opacity(0.06), y: 4, blur: 12),
                ShadowLayer(color: Color.liqShadowInk.opacity(0.04), y: 1, blur: 3)]

/// Any shape filled with `fill` under `shadows`, each layer on its own copy of the shape so neither is a
/// shadow of the other (SHOWUP-194: the chat's bubbles and chips). CSS's first layer is listed last.
struct ShadowedShape<S: Shape, Fill: ShapeStyle>: View {
    let shape: S
    let fill: Fill
    let shadows: [ShadowLayer]

    var body: some View {
        ZStack {
            ForEach(Array(shadows.reversed().enumerated()), id: \.offset) { _, layer in
                shape.fill(fill).shadow(color: layer.color, radius: layer.blur / 2, y: layer.y)
            }
            if shadows.isEmpty { shape.fill(fill) }
        }
    }
}

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
            // One line, as the sheet: at the largest type it shrinks to the photo's width instead of
            // being cut to `It's a...` (the 4 Nov 2026 device audit).
            Text(DateCopy.hero)
                .font(F.lora(30, bold: true, italic: true))
                .tracking(-0.02 * 30)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
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
                // Wrapped, a word wider than the column shrinks rather than breaks (`FitWords`).
                FitWords(text: value, font: { s in F.lora(20 * s, bold: true) }) { s in
                    Text(value).font(F.lora(20 * s, bold: true)).foregroundColor(.liqFg)
                        .fixedSize(horizontal: false, vertical: true)
                }
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
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

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
                        // The address wraps beside Copy while its widest word fits there; where it does not
                        // (the largest type on a narrow phone) Copy goes under it, rather than the address
                        // breaking inside a word -- `Oran` / `iens` / `tras` / `se` (the 4 Nov 2026 audit).
                        Group {
                            if let address = place.address {
                                ViewThatFits(in: .horizontal) {
                                    HStack(alignment: .center, spacing: 8) {
                                        addressText(address)
                                        CopyAddress(copied: copied, onCopy: onCopy)
                                    }
                                    VStack(alignment: .leading, spacing: 6) {
                                        addressText(address)
                                        CopyAddress(copied: copied, onCopy: onCopy)
                                    }
                                }
                            } else {
                                CopyAddress(copied: copied, onCopy: onCopy)
                            }
                        }
                        .padding(.top, 3)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }
}

extension TimePlaceCard {
    /// The address, 12.5 / 1.35 muted, its line box growing with the type; a word wider than its
    /// column shrinks (`FitWords`).
    fileprivate func addressText(_ address: String) -> some View {
        FitWords(text: address, font: { s in F.manrope(12.5 * s, .regular) }) { s in
            Text(address)
                .font(F.manrope(12.5 * s, .regular))
                .foregroundColor(.liqMuted)
                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 12.5 * s, multiple: 1.35))
                .fixedSize(horizontal: false, vertical: true)
                .cssLineBox(12.5 * 1.35 * s * typeScale)
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
            .animation(reduceMotion ? nil : .easeInOut(duration: Motion.tint), value: copied)
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
    CoordinateTitle(name: name)
}

/// Lora 20 / 1.1. The line box grows with the type, as the font does -- unscaled, each line past the
/// first ran over the sub line under it at the largest sizes (the 4 Nov 2026 device audit). A word
/// wider than the column shrinks rather than breaks (`FitWords`).
private struct CoordinateTitle: View {
    let name: String
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    var body: some View {
        let title = DateCopy.coordTitle(name)
        FitWords(text: title, font: { s in F.lora(20 * s, bold: true) }) { s in
            Text(title)
                .font(F.lora(20 * s, bold: true))
                .foregroundColor(.liqFg)
                .fixedSize(horizontal: false, vertical: true)
                .cssLineBox(20 * 1.1 * s * typeScale)
        }
    }
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
    /// The change tiles under the locked line: `changeTileReasons`, empty until Date 05 ships.
    var changeTiles: [ChatReason] = []
    var onChangeTile: (ChatReason) -> Void = { _ in }

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
                    if !changeTiles.isEmpty { ChangeTiles(tiles: changeTiles, onTap: onChangeTile) }
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
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    var body: some View {
        let reason = unread.reason
        // A message sent with no chip draws no badge; its card keeps the orange the reference's
        // `reasonFor` falls back to, so it still reads as waiting for a reply.
        let tone = reason?.tone ?? .orange
        // The rail and the body sit inside the 1.5 border, as CSS's `overflow: hidden` card has them.
        Card(borderColor: tone.chipBorder, borderWidth: 1.5, padding: EdgeInsets(top: 1.5, leading: 1.5, bottom: 1.5, trailing: 1.5)) {
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
                    if let reason { ReasonBadge(reason: reason, onLight: true) }
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
                              shadows: tone == .orange ? shadowCta : (tone == .danger ? [] : shadowViolet),
                              action: onOpenChat)
                    .padding(.top, 14)
            }
            .padding(16)
        }
    }

    private var name: some View {
        FitWords(text: match.name, font: { s in F.lora(19 * s, bold: true) }) { s in
            Text(match.name).font(F.lora(19 * s, bold: true)).foregroundColor(.liqFg)
                .cssLineBox(19 * 1.1 * s * typeScale)
        }
    }

    private func tag(_ tone: ReasonTone) -> some View {
        HStack(spacing: 4) {
            Circle().fill(tone.fg).frame(width: 5, height: 5)
            // Shrinks rather than widens the card where even the stacked form leaves it too little room
            // (AX5 on a 320 phone pushed the whole screen 35 wider, the 4 Nov 2026 device audit).
            Text(unreadTag(unread.count).uppercased())
                .font(F.manrope(10, .heavy))
                .tracking(0.05)
                .foregroundColor(tone.fg)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 2)
        .background(Capsule().fill(tone.bg))
    }
}

/// The locked card's change line and tiles (5 Nov 2026): `Request to change the location or meet time.` (13.5 /
/// 1.4 muted, 4 below the locked line), then the tiles in two columns, gap 8, 14 below -- each white with a 1 px
/// soft border, radius 16, pad 14 8, the violet icon 18 / 2 over the label 13 / 600. The card is still not
/// tappable; only the tiles are, each opening the Date 05 picker. The two in a row are one height. Android's
/// `ChangeTiles`.
private struct ChangeTiles: View {
    let tiles: [ChatReason]
    let onTap: (ChatReason) -> Void

    var body: some View {
        PrettyText(DateCopy.coordChangeLine, font: uiFont(PS.manropeRegular, 13.5, .regular), lineHeight: 13.5 * 1.4,
                   color: .liqMuted)
            .padding(.top, 4)
        HStack(spacing: 8) {
            ForEach(tiles, id: \.self) { reason in
                Button(action: { onTap(reason) }) {
                    VStack(spacing: 6) {
                        BrandIconView(icon: reason.icon, size: 18, stroke: 2, tint: .liqPurple)
                        Text(reason.label).font(F.manrope(13, .semibold)).foregroundColor(.liqFg)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    // Pad 14 8, plus the 1 px border, which CSS counts in the box.
                    .padding(EdgeInsets(top: 15, leading: 9, bottom: 15, trailing: 9))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.liqElevated))
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.liqBorderSoft, lineWidth: 1))
                }
                .buttonStyle(PressScale())
                .fitTapArea()
            }
            if tiles.count == 1 { Color.clear.frame(maxWidth: .infinity) }
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.top, 14)
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
            MatchPhoto(name: match.name, photoURL: match.photoUrl, size: 46)
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
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    var body: some View {
        Button(action: onReport) {
            HStack(alignment: .center, spacing: 13) {
                IconTile(size: 40, radius: 12, fill: Color.liqElevated, icon: .shield, iconSize: 19, tint: .liqDanger, stroke: 2.1)
                (Text(DateCopy.reportTitle).font(F.manrope(13.5, .bold)).foregroundColor(.liqFg)
                    + Text(" " + DateCopy.reportBody).font(F.manrope(13.5, .regular)).foregroundColor(.liqMuted))
                    .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 13.5, multiple: 1.4))
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .cssLineBox(13.5 * 1.4 * typeScale)
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

/// The eyebrow on one line, as the intent row measures it beside the count.
private func oneLineEyebrow(_ text: String, size: CGFloat = 11) -> some View {
    Text(text.uppercased())
        .font(F.manrope(size, .bold))
        .tracking(0.08)
        .foregroundColor(.liqSubtle)
}

/// The eyebrow, wrapping; a word wider than its column shrinks rather than being cut -- it read
/// `MUTUAL INTE...` at AX5 (the 4 Nov 2026 device audit).
private func eyebrow(_ text: String, size: CGFloat = 11) -> some View {
    let upper = text.uppercased()
    return FitWords(text: upper, font: { s in F.manrope(size * s, .bold) }) { s in
        Text(upper)
            .font(F.manrope(size * s, .bold))
            .tracking(0.08)
            .foregroundColor(.liqSubtle)
            .fixedSize(horizontal: false, vertical: true)
    }
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
                        intentLine(shared, fitted: false)
                        ShowUpCount(count: date.match.attendedCount, size: .sm)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        intentLine(shared, fitted: true)
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
    /// `fitted`: wrapping, its words shrunk to the column (`FitWords`) -- under the count. Beside it, the
    /// one-line texts, so the row picks "beside" only where the whole intent fits on one line, as
    /// Android's `BesideOrUnder` does.
    fileprivate func intentLine(_ shared: Bool, fitted: Bool) -> some View {
        HStack(alignment: .center, spacing: 12) {
            if shared {
                BrandIconView(icon: .heartFilled, size: 20, tint: .liqOrange)
            } else {
                BrandIconView(icon: .shieldCheck, size: 20, tint: .liqPurple)
            }
            VStack(alignment: .leading, spacing: 0) {
                let label = shared ? DateCopy.mutualIntent : DateCopy.theirShowUp(date.match.name)
                if fitted { eyebrow(label) } else { oneLineEyebrow(label) }
                if shared {
                    let title = motivationOf(date.motivationThem)?.title ?? date.motivationThem ?? ""
                    if fitted {
                        FitWords(text: title, font: { s in F.manrope(15 * s, .semibold) }) { s in
                            Text(title).font(F.manrope(15 * s, .semibold)).foregroundColor(.liqFg)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    } else {
                        Text(title).font(F.manrope(15, .semibold)).foregroundColor(.liqFg)
                    }
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
                    // A word wider than the column beside the photo shrinks: it was cut to `Leo...`.
                    let title = match.age.map { DateCopy.nameAge(match.name, $0) } ?? match.name
                    FitWords(text: title, font: { s in F.lora(20 * s, bold: true) }) { s in
                        Text(title).font(F.lora(20 * s, bold: true)).foregroundColor(.liqFg)
                            .fixedSize(horizontal: false, vertical: true)
                    }
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
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

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
                                .cssLineBox(13 * 1.4 * typeScale)
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

// DateFooter.swift
// ShowUp · the Date screen's footer bar: the Check-in bar or the End Date bar (SHOWUP-193 ⑯)
//
// ONE slot, swapped on `checkedIn`: the Check-in bar (with its `End date` link) from the match until
// check-in, the End Date bar after. The bars are Date 03's and Date 06's -- this ticket's acceptance
// criteria draw them here now, and those tickets add what their buttons do. Android's `DateFooter.kt`.

import SwiftUI

/// Below this the bar's text column is too narrow to read, and its action takes its own line.
private let barTextMin: CGFloat = 140

/// `lead` (the tile and the text) and `action` on one row, `gap` apart and centred, while `lead` keeps
/// at least `barTextMin`; otherwise `action` goes under it, full width. Words never truncate: at the
/// largest type on a narrow phone the layout yields instead.
private struct BarRowLayout: Layout {
    let gap: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        let width = proposal.width ?? 390
        let action = subviews[1].sizeThatFits(.unspecified)
        let room = width - action.width - gap
        if room >= barTextMin {
            let lead = subviews[0].sizeThatFits(ProposedViewSize(width: room, height: nil))
            return CGSize(width: width, height: max(lead.height, action.height))
        }
        let lead = subviews[0].sizeThatFits(ProposedViewSize(width: width, height: nil))
        let below = subviews[1].sizeThatFits(ProposedViewSize(width: width, height: nil))
        return CGSize(width: width, height: lead.height + gap + below.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let action = subviews[1].sizeThatFits(.unspecified)
        let room = bounds.width - action.width - gap
        if room >= barTextMin {
            let lead = subviews[0].sizeThatFits(ProposedViewSize(width: room, height: nil))
            subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.midY - lead.height / 2), anchor: .topLeading,
                              proposal: ProposedViewSize(width: room, height: lead.height))
            subviews[1].place(at: CGPoint(x: bounds.maxX - action.width, y: bounds.midY - action.height / 2),
                              anchor: .topLeading, proposal: ProposedViewSize(action))
        } else {
            let lead = subviews[0].sizeThatFits(ProposedViewSize(width: bounds.width, height: nil))
            subviews[0].place(at: bounds.origin, anchor: .topLeading,
                              proposal: ProposedViewSize(width: bounds.width, height: lead.height))
            subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.minY + lead.height + gap), anchor: .topLeading,
                              proposal: ProposedViewSize(width: bounds.width, height: nil))
        }
    }
}

private func uiFont(_ name: String, _ size: CGFloat, _ weight: UIFont.Weight) -> UIFont {
    TypeMetrics.uiFont(name, size, fallback: .systemFont(ofSize: size, weight: weight))
}

/// The Check-in bar (Date 03's, the reference's `CheckInBar`): deep ink, top corners 22, a map pin tile,
/// `Your date with {name}`, the sunset `Check-in` pill, and the `End date` link under it -- which opens
/// the same wrap-up sheet as the End Date bar (2 Nov 2026), never the cancel flow.
struct CheckInBar: View {
    let name: String
    let onCheckIn: () -> Void
    let onEndDate: () -> Void

    var body: some View {
        let shape = UnevenRoundedRectangle(topLeadingRadius: 22, topTrailingRadius: 22)
        VStack(spacing: 6) {
            BarRowLayout(gap: 14) {
                HStack(alignment: .center, spacing: 14) {
                    IconTile(size: 46, radius: 14, fill: Color.white.opacity(0.12), icon: .mapPin, iconSize: 22, tint: .white, stroke: 2.2)
                    VStack(alignment: .leading, spacing: 3) {
                        // Lora 18 / 1.15 over two lines -- tighter than the font, which only UIKit sets.
                        PrettyText(runs: [(DateCopy.barTitle(name), false)], font: uiFont(PS.loraBold, 18, .bold),
                                   lineHeight: 18 * 1.15, color: .white, emphasisFont: nil, emphasisColor: nil,
                                   pretty: false)
                        // `textWrap: 'pretty'`, as both bars' sublines.
                        PrettyText(DateCopy.checkInBarSub, font: uiFont(PS.manropeRegular, 12.5, .regular),
                                   lineHeight: 12.5 * 1.4, color: .white)
                    }
                }
                Button(action: onCheckIn) {
                    Text(DateCopy.checkIn)
                        .font(F.manrope(15, .heavy))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .fixedSize()
                        .padding(.horizontal, 20)
                        .frame(height: 46)
                        .background(
                            ShadowedPill(fill: Color.liqOrange, shadows: shadowCta)
                                .overlay(CSSLinearGradient(angle: 135, colors: Color.suGradSunsetStops.map(\.color),
                                                           stops: Color.suGradSunsetStops.map(\.location))
                                    .clipShape(Capsule()))
                        )
                }
                .buttonStyle(PressScale())
            }
            Button(action: onEndDate) {
                Text(DateCopy.endDateLink)
                    .font(F.manrope(13, .semibold))
                    .foregroundColor(.liqLilacCard)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .overflowTapTarget()
            }
            .buttonStyle(PressScale())
        }
        .padding(EdgeInsets(top: 16, leading: 16, bottom: 10, trailing: 16))
        .frame(maxWidth: .infinity)
        // `rgba(29,17,41,.86)` over `backdrop-filter: blur(20px)`: the blurred glass over this screen's
        // cream is the ink at .86 laid on cream, and that is what is drawn -- the glass alone let the
        // cards' text read through it. `0 -6px 24px rgba(29,17,41,.22)` above.
        .background(
            ZStack {
                shape.fill(Color.liqCream)
                shape.fill(Color.liqFg.opacity(0.86))
            }
            .compositingGroup()
            .shadow(color: Color.liqFg.opacity(0.22), radius: 12, y: -6)
        )
        .fitAnchor("date:footer-bar")
    }
}

/// The End Date bar (Date 06's, the reference's `DateStatusBar`, not complete): cream glass, a 1 px
/// border-soft top rule, a calendar tile, and the outlined `End Date` pill.
struct DateStatusBar: View {
    let name: String
    let onEndDate: () -> Void

    var body: some View {
        BarRowLayout(gap: 12) {
            HStack(alignment: .center, spacing: 12) {
                IconTile(size: 38, radius: 12, fill: Color.liqPurple.opacity(0.10), icon: .calendar, iconSize: 19,
                         tint: .liqPurple, stroke: 2.1)
                VStack(alignment: .leading, spacing: 0) {
                    Text(DateCopy.barTitle(name)).font(F.manrope(13.5, .bold)).foregroundColor(.liqFg)
                        .fixedSize(horizontal: false, vertical: true)
                    PrettyText(DateCopy.endBarSub, font: uiFont(PS.manropeRegular, 11.5, .regular),
                               lineHeight: 11.5 * 1.35, color: .liqMuted)
                }
            }
            // Drawn 40 tall as the reference; it answers at 44.
            Button(action: onEndDate) {
                Text(DateCopy.endDate)
                    .font(F.manrope(14, .bold))
                    .foregroundColor(.liqPurple)
                    .lineLimit(1)
                    .fixedSize()
                    .padding(.horizontal, 16)
                    .frame(height: 40)
                    .background(Capsule().fill(Color.liqElevated))
                    .overlay(Capsule().strokeBorder(Color.liqPurple, lineWidth: 1.5))
                    .overflowTapTarget()
            }
            .buttonStyle(PressScale())
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(Color.liqCream.opacity(0.96))
        .overlay(alignment: .top) { Rectangle().fill(Color.liqBorderSoft).frame(height: 1) }
        .fitAnchor("date:footer-bar")
    }
}

/// ⑯ The bar for `checkedIn`: the Check-in bar before check-in, the End Date bar after.
struct DateBar: View {
    let checkedIn: Bool
    let name: String
    let onCheckIn: () -> Void
    let onEndDate: () -> Void

    var body: some View {
        if checkedIn {
            DateStatusBar(name: name, onEndDate: onEndDate)
        } else {
            CheckInBar(name: name, onCheckIn: onCheckIn, onEndDate: onEndDate)
        }
    }
}

// CheckInBar.swift
// ShowUp · A, the Check-in bar (SHOWUP-195), from `CheckInBar` in `screen-checkin-reference.jsx`. Android's
// `CheckInBar.kt`.
//
// The footer slot of the Date screen until the person has checked in. Larger and filled, so it reads as THE action
// on arrival.
//
// NO BACKDROP BLUR. The reference's `rgba(29,17,41,.86)` + `blur(20px)` is composited here: the ink at 86% over the
// cream, opaque, so what scrolls behind the bar never shows through it (the 193 rule for the TabBar's glass, kept).
//
// R12, THE EXTREME SIZES. The text column never goes below its longest word. Where pin + text + button cannot share
// one row, the button goes under the text, full width, 10 below.

import SwiftUI
import UIKit

/// R15: the `End date` link is built and switched off. It opens Date 06's wrap-up sheet, which does not exist yet;
/// the host passes `onEnd` only when this is on and that sheet ships.
let endDateLinkShown = false

/// `0 -6px 24px rgba(29,17,41,.22)`, cast up.
private let barLift = [ShadowLayer(color: Color.liqFg.opacity(0.22), y: -6, blur: 24)]

/// Padding 16 16 10, column gap 6. The row is centred, gap 14: the pin tile 46 (radius 14, white 12%, `map-pin` 22 /
/// 2.2), the text (Lora 700 18 / 1.15 over Manrope 12.5 / 1.4, 3 below, pretty) and `Check-in` (h 46, pad 0 20, pill,
/// sunset, Manrope 800 15, nowrap, `--liq-shadow-cta`). Under it the `End date` link when `onEnd` is given: centred,
/// pad 8 12, Manrope 600 13 `#E8DCF5`, answering at 44.
struct CheckInBar: View {
    let name: String
    let onCheckIn: () -> Void
    var onEnd: (() -> Void)? = nil

    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    private var titleFont: UIFont { chatFont(PS.loraBold, 18, .bold) }
    private var lineFont: UIFont { chatFont(PS.manropeRegular, 12.5, .regular) }

    /// The longest word of the two paragraphs, at the type size the person chose.
    private var longestWord: CGFloat {
        max(widestWordWidth(ArrivalCopy.barTitle(name), titleFont), widestWordWidth(ArrivalCopy.barLine, lineFont)) * typeScale
    }

    var body: some View {
        let shape = UnevenRoundedRectangle(topLeadingRadius: 22, topTrailingRadius: 22)
        VStack(spacing: 6) {
            BarRowLayout(minTextWidth: longestWord) {
                BrandIconView(icon: .mapPin, size: 22, stroke: 2.2, tint: .white)
                    .frame(width: 46, height: 46)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.12)))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 0) {
                    PrettyText(ArrivalCopy.barTitle(name), font: titleFont, lineHeight: 18 * 1.15, color: .white)
                        .accessibilityAddTraits(.isHeader)
                    PrettyText(ArrivalCopy.barLine, font: lineFont, lineHeight: 12.5 * 1.4, color: .white)
                        .padding(.top, 3)
                }
                Button(action: onCheckIn) {
                    Text(ArrivalCopy.barButton)
                        .font(F.manrope(15, .heavy))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .fixedSize()
                        .padding(.horizontal, 20)
                        .frame(maxWidth: .infinity, minHeight: 46)
                        // The glow behind, then the reference's 135deg sunset clipped to the pill.
                        .background(SunsetFill(shape: Capsule(), shadows: shadowCta))
                        .fitTapArea()
                }
                .buttonStyle(PressScale())
            }
            if let onEnd {
                Button(action: onEnd) {
                    Text(ArrivalCopy.endDate)
                        .font(F.manrope(13, .semibold))
                        .foregroundColor(.liqLilacCard)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .overflowTapTarget(wide: true)
                }
                .buttonStyle(PressScale())
            }
        }
        .padding(EdgeInsets(top: 16, leading: 16, bottom: 10, trailing: 16))
        .frame(maxWidth: .infinity)
        .background {
            // The shadow's fill is the bar's own, so the rounded bottom corners it draws are covered.
            ZStack {
                ShadowedShape(shape: shape, fill: Color.liqFg, shadows: barLift)
                shape.fill(Color.liqCream)
                shape.fill(Color.liqFg.opacity(0.86))
            }
        }
    }
}

/// A shape filled with the reference's `--su-grad-sunset` at CSS's 135deg for ITS box (not the corner to corner
/// of `Gradients.sunset`), under `shadows`. Shared by the bar's pill and the sheets' buttons (SHOWUP-195).
struct SunsetFill<S: Shape>: View {
    let shape: S
    let shadows: [ShadowLayer]

    var body: some View {
        ZStack {
            ShadowedShape(shape: shape, fill: Color.liqPurple, shadows: shadows)
            CSSLinearGradient(angle: 135, colors: Color.suGradSunsetStops.map(\.color),
                              stops: Color.suGradSunsetStops.map(\.location))
                .clipShape(shape)
        }
    }
}

/// The pin, the text and the button in one row (gap 14, centred) where the text keeps at least its longest word;
/// otherwise the pin and the text share the first row and the button runs full width under them, 10 below (R12).
/// Three subviews, in that order.
private struct BarRowLayout: Layout {
    let minTextWidth: CGFloat
    private let gap: CGFloat = 14
    private let under: CGFloat = 10

    /// Where each subview goes, and the size the row takes, for `width`.
    private struct Plan {
        var size: CGSize
        var frames: [CGRect]
    }

    private func plan(_ width: CGFloat, _ subviews: Subviews) -> Plan {
        guard subviews.count == 3 else { return Plan(size: .zero, frames: []) }
        let pin = subviews[0].sizeThatFits(.unspecified)
        let button = subviews[2].sizeThatFits(.unspecified)
        if pin.width + gap + minTextWidth + gap + button.width <= width {
            let text = subviews[1].sizeThatFits(ProposedViewSize(width: width - pin.width - button.width - 2 * gap, height: nil))
            let h = max(pin.height, text.height, button.height)
            return Plan(size: CGSize(width: width, height: h), frames: [
                CGRect(x: 0, y: (h - pin.height) / 2, width: pin.width, height: pin.height),
                CGRect(x: pin.width + gap, y: (h - text.height) / 2, width: text.width, height: text.height),
                CGRect(x: width - button.width, y: (h - button.height) / 2, width: button.width, height: button.height),
            ])
        }
        let text = subviews[1].sizeThatFits(ProposedViewSize(width: max(0, width - pin.width - gap), height: nil))
        let full = subviews[2].sizeThatFits(ProposedViewSize(width: width, height: nil))
        let h = max(pin.height, text.height)
        return Plan(size: CGSize(width: width, height: h + under + full.height), frames: [
            CGRect(x: 0, y: (h - pin.height) / 2, width: pin.width, height: pin.height),
            CGRect(x: pin.width + gap, y: (h - text.height) / 2, width: text.width, height: text.height),
            CGRect(x: 0, y: h + under, width: width, height: full.height),
        ])
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        plan(proposal.width ?? 320, subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let laid = plan(bounds.width, subviews)
        for (subview, frame) in zip(subviews, laid.frames) {
            subview.place(at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY), anchor: .topLeading,
                          proposal: ProposedViewSize(width: frame.width, height: frame.height))
        }
    }
}

#Preview("Check-in bar") {
    VStack {
        Spacer()
        CheckInBar(name: "Leonie", onCheckIn: {})
    }
    .background(Color.liqCream)
}

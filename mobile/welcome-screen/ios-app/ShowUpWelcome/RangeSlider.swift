//
//  RangeSlider.swift
//  ShowUp · `RangeSlider` + its thumbs -- one thumb or two over an integer range (SHOWUP-182)
//
//  The Swift twin of `designsystem/RangeSlider.kt`. Built by Check-In 05 for its age and distance
//  filters; Search's filter sheet consumes it rather than building its own ("a second copy is a
//  bug"). Numbers from `screen-checkin-filters-reference.jsx`:
//
//    · 44 tall, the track INSET 22 each side -- half the thumb's hit box -- "so a thumb at either
//      end, hit area included, never overflows the list: no sideways pan, no clipped target"
//    · track: 4 tall at y 20, `--liq-border`, a full pill; the picked span in violet
//    · thumb: 28 drawn (white, a 1.5 violet border, `0 2 8` violet glow), 44 hit target
//
//  TWO THUMBS NEVER CROSS. The caller's `onChange` carries the other thumb when one is dragged past
//  it (`moveAgeThumb`). Thumbs that SIT on the same value are told apart by the first movement --
//  left moves the lower, right the upper -- or two thumbs parked together at an end could never be
//  separated again.
//
//  ACCESSIBLE: each thumb is an adjustable element with its label and its value, stepping by one
//  on swipe up / down, its range bounded by the other thumb.
//
//  The thumbs and the picked span are PLACED by offset, the one positioned drawing in the
//  Check-In: where a thumb sits is the value, which a stack cannot express.
//

import SwiftUI

struct RangeSlider: View {
    /// One value (a single thumb) or two (a range), each within `bounds`.
    let values: [Int]
    let bounds: ClosedRange<Int>
    /// Each thumb's accessible label, in the same order.
    let labels: [String]
    /// A thumb moved to a value; the caller keeps two thumbs from crossing.
    let onChange: (_ thumb: Int, _ value: Int) -> Void

    /// Which thumb this drag moves, decided by where it began: nil before a drag, -1 for none,
    /// -2 while two coincident thumbs wait for the first movement. Disposable gesture state.
    @State private var grabbed: Int?

    /// The thumb's hit box -- and twice the track's inset.
    private let thumbHit = ComponentSizes.minTapTarget
    private var inset: CGFloat { thumbHit / 2 }
    private var dual: Bool { values.count > 1 }

    var body: some View {
        GeometryReader { geo in
            let track = max(geo.size.width - 2 * inset, 1)
            let from = dual ? fraction(values[0]) : 0
            let to = dual ? fraction(values[1]) : fraction(values.first ?? bounds.lowerBound)
            ZStack(alignment: .topLeading) {
                Capsule()
                    .fill(Color.liqBorder)
                    .frame(width: track, height: 4)
                    .offset(x: inset, y: 20)
                Capsule()
                    .fill(Color.liqPurple)
                    .frame(width: max(0, (to - from) * track), height: 4)
                    .offset(x: inset + from * track, y: 20)
                ForEach(values.indices, id: \.self) { i in
                    thumb(i, track: track)
                }
            }
            .frame(width: geo.size.width, height: thumbHit, alignment: .topLeading)
            // THE DRAG COVERS THE WHOLE 44 BAND, inset included: a thumb at either end has half its
            // hit box in the inset, and that half must answer too. Simultaneous, so a vertical
            // drag still scrolls the list it sits in; a drag that starts vertical moves nothing.
            .contentShape(Rectangle())
            .simultaneousGesture(drag(track: track))
        }
        .frame(height: thumbHit)
    }

    private func fraction(_ v: Int) -> CGFloat {
        bounds.upperBound == bounds.lowerBound ? 0
            : CGFloat(v - bounds.lowerBound) / CGFloat(bounds.upperBound - bounds.lowerBound)
    }

    private func drag(track: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { g in
                if grabbed == nil {
                    let mostlyVertical = abs(g.translation.height) > abs(g.translation.width)
                    grabbed = mostlyVertical ? -1 : pick(at: g.startLocation.x, track: track)
                }
                if grabbed == -2 { grabbed = g.translation.width < 0 ? 0 : 1 }
                guard let thumb = grabbed, thumb >= 0 else { return }
                let ratio = min(max((g.location.x - inset) / track, 0), 1)
                let span = CGFloat(bounds.upperBound - bounds.lowerBound)
                onChange(thumb, bounds.lowerBound + Int((ratio * span).rounded()))
            }
            .onEnded { _ in grabbed = nil }
    }

    /// The thumb under `x`: none, the only one, both waiting (they coincide), or the nearer.
    private func pick(at x: CGFloat, track: CGFloat) -> Int {
        func thumbX(_ v: Int) -> CGFloat { inset + fraction(v) * track }
        let near = values.indices.filter { abs(thumbX(values[$0]) - x) <= inset }
        if near.isEmpty { return -1 }
        if near.count == 1 { return near[0] }
        if values[0] == values[1] { return -2 }
        return near.min { abs(thumbX(values[$0]) - x) < abs(thumbX(values[$1]) - x) } ?? -1
    }

    /// One thumb: a 44 hit box centred on its value, a 28 white disc with a violet edge and glow.
    private func thumb(_ i: Int, track: CGFloat) -> some View {
        let v = values[i]
        let lo = dual && i == 1 ? values[0] : bounds.lowerBound
        let hi = dual && i == 0 ? values[1] : bounds.upperBound
        return ZStack {
            Circle()
                .fill(Color.white)
                // `0 2px 8px rgba(129,42,236,.18)` -- the chips' violet glow.
                .shadow(color: .liqChipGlow, radius: 4, y: 2)
                .overlay(Circle().strokeBorder(Color.liqPurple, lineWidth: 1.5))
                .frame(width: 28, height: 28)
        }
        .frame(width: thumbHit, height: thumbHit)
        .offset(x: fraction(v) * track)
        .accessibilityElement()
        .accessibilityLabel(Text(i < labels.count ? labels[i] : ""))
        .accessibilityValue(Text("\(v)"))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: onChange(i, min(v + 1, hi))
            case .decrement: onChange(i, max(v - 1, lo))
            @unknown default: break
            }
        }
    }
}

#Preview("Range slider · ages 25 to 35, distance 15") {
    VStack(spacing: 22) {
        RangeSlider(values: [25, 35], bounds: 18...65, labels: ["Minimum age", "Maximum age"], onChange: { _, _ in })
        RangeSlider(values: [15], bounds: 1...50, labels: ["Distance in km"], onChange: { _, _ in })
    }
    .padding(Spacing.screenGutter)
    .background(Color.liqCream)
}

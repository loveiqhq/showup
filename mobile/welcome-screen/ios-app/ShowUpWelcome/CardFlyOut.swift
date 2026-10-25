//
//  CardFlyOut.swift
//  ShowUp · the fly-out a card makes when it gets a final answer (SHOWUP-185 20, reused by 186)
//
//  The Swift twin of `search/CardFlyOut.kt`. "One implementation for the grid and this list"
//  (`useCardFlyOut` in the kit). t = 0 when the back transition has settled: hold
//  `Motion.gridLeaveHold` (the filled heart or the label is visible), then the card moves to the
//  visible list's centre x and 72% of its height, scales to .12 and fades to 0 over `Motion.gridLeave`
//  on `cubic-bezier(.55,0,.8,.2)`; then it is gone and the rest move to their new slots over
//  `Motion.gridReflow` on `cubic-bezier(.2,.7,.2,1)`. With reduced motion: a `Motion.gridLeaveReduced`
//  fade in place, then the reflow.
//
//  THE FLYING CARD IS DRAWN ABOVE THE WHOLE LIST, in `FlyOutLayer`, not inside its own slot -- so it
//  passes over the row below, as the kit's `z-index: 5` lets it, whatever the lazy container does with
//  its children's order. The slot keeps its place, drawn empty, while a copy of the card flies in the
//  layer over the list; when the flight ends the slot goes and the rest reflow. The layer is cut at the
//  list's edge, as the kit's scroll container cuts its cards.
//
//  UNDO MID-FLIGHT stops it: the card's leaving ends, the copy is dropped, and the card is drawn in its
//  slot again at full opacity, with no transform left behind.
//

import SwiftUI

/// The list's own coordinate space -- its visible box, so a slot's frame in it moves as it scrolls.
let premiumListSpace = "premiumList"

/// `cubic-bezier(.55,0,.8,.2)`: the fly-out accelerates away. Reduced motion: a linear fade.
func gridLeaveAnimation(reduced: Bool) -> Animation {
    reduced ? .linear(duration: Motion.gridLeaveReduced) : .timingCurve(0.55, 0, 0.8, 0.2, duration: Motion.gridLeave)
}

/// `cubic-bezier(.2,.7,.2,1)`: the reflow settles in -- the rest moving into the gap a card left.
let gridReflowAnimation: Animation = .timingCurve(0.2, 0.7, 0.2, 1, duration: Motion.gridReflow)

/// What a list's cards in flight share with the layer that draws them: one per list, held by it, read
/// by `FlyOutLayer` and written by each card's `cardFlyOut`.
@MainActor
@Observable
final class FlyOutHost {
    /// The visible list's size: where every flight is aimed. Its origin is `premiumListSpace`'s.
    var viewport: CGSize = .zero
    /// Each card's slot in `premiumListSpace`, as last laid out before it took off.
    var slots: [String: CGRect] = [:]
    /// The cards in flight, and where each is going, from its slot's centre.
    var targets: [String: CGSize] = [:]
    /// Reduced motion when the flight began: a fade in place.
    var reduced = false

    func isFlying(_ key: String) -> Bool { targets[key] != nil }
}

extension View {
    /// The fly-out, on one card's slot. While the card flies the slot draws nothing -- the copy in the
    /// layer above is the card -- and keeps its place until the layer reports the flight over.
    ///
    /// - Parameters:
    ///   - leaving: the card got a final answer and is on its way out.
    ///   - settled: the list is the screen again -- the back transition has finished.
    ///   - frozen: previews only: the card held part-way through its flight, as frames K and H draw it.
    func cardFlyOut(host: FlyOutHost, key: String, leaving: Bool, settled: Bool, frozen: Bool = false) -> some View {
        modifier(CardFlyOut(host: host, key: key, leaving: leaving, settled: settled, frozen: frozen))
    }
}

private struct CardFlyOut: ViewModifier {
    let host: FlyOutHost
    let key: String
    let leaving: Bool
    let settled: Bool
    let frozen: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        let inFlight = leaving && (frozen || host.isFlying(key))
        content
            // The slot's box until its flight starts -- then it is the flight's starting point and
            // holds still. The WHOLE box, not the part of it the list shows: a card half below the
            // fold flies at its own size (the 185 fit sweep, 25 Oct 2026).
            .background(GeometryReader { g in
                let frame = g.frame(in: .named(premiumListSpace))
                Color.clear
                    .onAppear { record(frame) }
                    .onChange(of: frame) { _, now in record(now) }
            })
            .opacity(inFlight ? 0 : 1)
            .onChange(of: leaving) { _, now in if !now { host.targets[key] = nil } }
            .task(id: leaving && settled) {
                guard leaving, settled, !frozen else { return }
                try? await Task.sleep(nanoseconds: UInt64(Motion.gridLeaveHold * 1_000_000_000))
                // Not before both boxes are laid out: taken any earlier, the flight has nowhere to aim.
                guard !Task.isCancelled, leaving, let slot = host.slots[key], host.viewport != .zero else { return }
                host.reduced = reduceMotion
                host.targets[key] = flyOutTarget(card: slot, view: CGRect(origin: .zero, size: host.viewport))
            }
    }

    private func record(_ frame: CGRect) {
        if !host.isFlying(key) { host.slots[key] = frame }
    }
}

/// The layer over a list where its cards fly: each flight is `card` drawn again at its slot, scaled
/// about its centre, moved toward its target and faded -- `transform: translate(dx, dy) scale(s);
/// opacity` -- above every other card. Drawn only, never a control: the slot keeps the card's name.
///
/// - Parameters:
///   - onLeft: a flight is over: the card can leave the list.
///   - frozenKeys / frozenAt: previews only -- cards held this far into their flight, already eased.
struct FlyOutLayer<Card: View>: View {
    let host: FlyOutHost
    let onLeft: (String) -> Void
    var frozenKeys: [String] = []
    var frozenAt: CGFloat? = nil
    @ViewBuilder let card: (String) -> Card

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(flightKeys, id: \.self) { key in
                if let slot = host.slots[key] {
                    let frozen = frozenKeys.contains(key) ? frozenAt : nil
                    FlyingCopy(slot: slot,
                               target: host.targets[key]
                                   ?? flyOutTarget(card: slot, view: CGRect(origin: .zero, size: host.viewport)),
                               reduced: frozen == nil && host.reduced,
                               frozen: frozen,
                               onDone: {
                                   host.targets[key] = nil
                                   onLeft(key)
                               },
                               card: { card(key) })
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .clipped()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var flightKeys: [String] {
        let live = host.targets.keys.sorted()
        return live + frozenKeys.filter { !live.contains($0) }
    }
}

/// One card in flight, from 0 at its slot to 1 at its target. Its progress is its own, started when it
/// appears in the layer, so the flight always begins from the slot.
private struct FlyingCopy<Card: View>: View {
    let slot: CGRect
    let target: CGSize
    let reduced: Bool
    let frozen: CGFloat?
    let onDone: () -> Void
    @ViewBuilder let card: () -> Card

    @State private var progress: CGFloat = 0

    var body: some View {
        let p = frozen ?? progress
        let scale = reduced ? 1 : 1 - (1 - flyOutScale) * p
        card()
            .frame(width: slot.width, height: slot.height)
            .scaleEffect(scale)
            .offset(x: reduced ? 0 : target.width * p, y: reduced ? 0 : target.height * p)
            .opacity(1 - p)
            .position(x: slot.midX, y: slot.midY)
            .onAppear {
                guard frozen == nil else { return }
                withAnimation(gridLeaveAnimation(reduced: reduced)) {
                    progress = 1
                } completion: {
                    onDone()
                }
            }
    }
}

/// How far into a frozen flight `ms` is, eased as the flight eases: previews of frames K and H.
func frozenFlightProgress(_ ms: Double) -> CGFloat {
    let t = min(1, max(0, ms / (Motion.gridLeave * 1000)))
    return CGFloat(cubicBezierY(x: t, 0.55, 0, 0.8, 0.2))
}

/// The y of a CSS `cubic-bezier(x1, y1, x2, y2)` at x, by bisection on the curve's x.
func cubicBezierY(x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
    func coordinate(_ t: Double, _ a: Double, _ b: Double) -> Double {
        3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t
    }
    var low = 0.0
    var high = 1.0
    for _ in 0..<40 {
        let mid = (low + high) / 2
        if coordinate(mid, x1, x2) < x { low = mid } else { high = mid }
    }
    return coordinate((low + high) / 2, y1, y2)
}

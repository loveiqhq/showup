//
//  ConfettiRain.swift
//  ShowUp · Embrace 2's one-shot confetti (SHOWUP-166)
//
//  The Swift twin of `profile/ConfettiRain.kt`; its header explains the keyframes this reproduces —
//  the fall eased over its whole length, the fade eased again over the last 20% alone (a CSS timing
//  function applies per keyframe interval), the alternate swing that eases into each turn, and the
//  orthographic flip that is a horizontal scale by cos(angle).
//
//  DRIVEN BY THE DISPLAY: `TimelineView(.animation)` asks for a frame each refresh and `Canvas` draws
//  every piece under a transform. No per-piece view, no layout, nothing but transforms and opacity.
//
//  DETERMINISTIC: the table is fixed, so every screenshot is comparable.
//

import SwiftUI

/// "The layer is unmounted at 3000 ms. Never loops."
let confettiTotalMs: Double = 3_000

enum ConfettiShape: Sendable { case strip, square, dot }

/// One row of `CONFETTI_PIECES`: `[left %, delay ms, fall ms, rot0 deg, spin deg, sway px, shape,
/// colour, size px]`.
struct ConfettiPiece: Sendable {
    let leftPercent: Double
    let delayMs: Double
    let fallMs: Double
    let rot0: Double
    let spin: Double
    let sway: Double
    let shape: ConfettiShape
    let color: Color
    let size: Double
}

private func piece(_ left: Double, _ delay: Double, _ fall: Double, _ rot0: Double, _ spin: Double,
                   _ sway: Double, _ shape: ConfettiShape, _ color: Color, _ size: Double) -> ConfettiPiece {
    ConfettiPiece(leftPercent: left, delayMs: delay, fallMs: fall, rot0: rot0, spin: spin,
                  sway: sway, shape: shape, color: color, size: size)
}

/// The 32-row table, verbatim. Tokens only: orange 500 / 400, primary 500 / 400.
let confettiPieces: [ConfettiPiece] = {
    let o5 = Color.liqOrange, o4 = Color.liqOrange400, v5 = Color.liqPurple, v4 = Color.liqPurple400
    return [
        piece(4, 0, 2300, -18, 540, 14, .strip, o5, 14),
        piece(12, 260, 2100, 42, -420, 18, .square, v5, 10),
        piece(19, 80, 2500, 12, 600, 12, .strip, o4, 12),
        piece(26, 420, 2000, -30, -360, 20, .dot, v4, 9),
        piece(33, 140, 2400, 68, 480, 16, .strip, v5, 14),
        piece(40, 560, 2200, -12, -540, 14, .square, o4, 11),
        piece(47, 40, 2600, 24, 420, 22, .strip, o5, 13),
        piece(54, 320, 2050, -52, -600, 12, .dot, v5, 8),
        piece(61, 180, 2350, 18, 360, 18, .strip, o4, 12),
        piece(68, 480, 2150, -8, -480, 16, .square, o5, 10),
        piece(75, 100, 2450, 36, 540, 14, .strip, v4, 13),
        piece(82, 380, 2000, 54, -420, 20, .strip, o4, 11),
        piece(89, 220, 2300, -22, 600, 12, .dot, o5, 9),
        piece(96, 600, 2100, 14, -360, 18, .strip, v5, 12),
        piece(8, 660, 2250, -40, 480, 16, .square, o4, 10),
        piece(16, 360, 2550, 30, -540, 14, .strip, o5, 13),
        piece(23, 20, 1950, -14, 420, 22, .dot, v5, 8),
        piece(30, 500, 2400, 62, -600, 12, .strip, o4, 11),
        piece(37, 240, 2150, -36, 360, 18, .square, v4, 9),
        piece(44, 700, 2050, 20, -480, 16, .strip, o5, 12),
        piece(51, 160, 2500, -28, 540, 14, .strip, v5, 10),
        piece(58, 440, 2200, 46, -420, 20, .dot, o4, 9),
        piece(65, 60, 2350, 8, 600, 12, .strip, o5, 12),
        piece(72, 620, 2000, -16, -360, 18, .square, v5, 10),
        piece(79, 300, 2450, 58, 480, 16, .strip, v4, 13),
        piece(86, 520, 2100, -44, -540, 14, .strip, o4, 11),
        piece(93, 120, 2300, 26, 420, 22, .dot, v5, 8),
        piece(2, 400, 2150, -6, -600, 12, .strip, v4, 12),
        piece(49, 680, 2250, 34, 360, 18, .strip, o5, 11),
        piece(63, 280, 2600, -24, -480, 16, .square, o4, 9),
        piece(35, 580, 2050, 50, 540, 14, .dot, o5, 9),
        piece(85, 200, 2400, -10, -420, 20, .strip, v5, 12),
    ]
}()

/// `confettiSwayMs(i)`: 700 · 820 · 940 · 1060 by index.
func confettiSwayMs(_ index: Int) -> Double { 700 + Double(index % 4) * 120 }

/// A CSS `cubic-bezier(x1, y1, x2, y2)` timing function, solved for y at a given x.
struct CubicBezier: Sendable {
    let x1: Double, y1: Double, x2: Double, y2: Double

    private func sample(_ a1: Double, _ a2: Double, _ t: Double) -> Double {
        let u = 1 - t
        return 3 * u * u * t * a1 + 3 * u * t * t * a2 + t * t * t
    }

    func callAsFunction(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        // Bisection on the x curve: monotonic for every timing function CSS allows, and exact to
        // well under a pixel in 30 steps.
        var lo = 0.0, hi = 1.0, t = x
        for _ in 0..<30 {
            t = (lo + hi) / 2
            if sample(x1, x2, t) < x { lo = t } else { hi = t }
        }
        return sample(y1, y2, t)
    }
}

private let fallEase = CubicBezier(x1: 0.3, y1: 0.4, x2: 0.6, y2: 1)
private let swayEase = CubicBezier(x1: 0.42, y1: 0, x2: 0.58, y2: 1)

/// Where one piece is at `elapsedMs` after t = 0. Pure; tested in ConfettiRainTests.
struct ConfettiFrame: Equatable, Sendable {
    let y: Double
    let alpha: Double
    let swayX: Double
    let rotation: Double
    let flipScale: Double
}

func confettiFrame(_ p: ConfettiPiece, index: Int, elapsedMs: Double, height: Double) -> ConfettiFrame {
    let local = max(elapsedMs - p.delayMs, 0)
    let t = min(max(local / p.fallMs, 0), 1)
    let y = -40 + (height + 80) * fallEase(t)
    let alpha = t < 0.8 ? 1 : 1 - fallEase((t - 0.8) / 0.2)

    let swing = confettiSwayMs(index)
    let cycle = Int(local / swing)
    let phase = local.truncatingRemainder(dividingBy: swing) / swing
    let directed = cycle % 2 == 0 ? phase : 1 - phase
    let s = swayEase(directed)
    return ConfettiFrame(
        y: y,
        alpha: alpha,
        swayX: -p.sway + 2 * p.sway * s,
        rotation: p.rot0 + p.spin * s,
        flipScale: cos(Double.pi * s)
    )
}

/// The rain. Plays ONCE, `confettiTotalMs` long, starting when the push transition has settled.
///
/// NOTHING WITH REDUCE MOTION — "not a static scatter" — and nothing when `play` is false: a pop
/// back onto the bridge does not replay it.
///
/// `played` IS THE CALLER'S, so it outlives this view: set when the rain starts, it keeps a scene
/// restore onto the bridge from playing it a second time — Android's `rememberSaveable` twin.
struct ConfettiRain: View {
    let play: Bool
    @Binding var played: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var start: Date?
    @State private var done = false

    var body: some View {
        Group {
            if play && !reduceMotion && !done {
                TimelineView(.animation(minimumInterval: nil, paused: start == nil)) { timeline in
                    Canvas { ctx, size in
                        guard let start else { return }
                        let elapsed = timeline.date.timeIntervalSince(start) * 1000
                        for (i, p) in confettiPieces.enumerated() {
                            let f = confettiFrame(p, index: i, elapsedMs: elapsed, height: size.height)
                            guard f.alpha > 0 else { continue }
                            draw(p, f, x: size.width * p.leftPercent / 100, in: &ctx)
                        }
                    }
                }
                .task {
                    guard !played else { done = true; return }
                    // t = 0 when the push transition has settled — not on mount, where it would play
                    // behind the incoming slide. The transition is `Motion.screen` long.
                    try? await Task.sleep(nanoseconds: UInt64(Motion.screen * 1_000_000_000))
                    guard !Task.isCancelled else { return }
                    played = true
                    start = Date()
                    try? await Task.sleep(nanoseconds: UInt64(confettiTotalMs * 1_000_000))
                    guard !Task.isCancelled else { return }
                    done = true
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// One piece: its LEFT edge at `left %`, its top at the fall offset — then swayed, spun and
    /// flipped about its own centre.
    private func draw(_ p: ConfettiPiece, _ f: ConfettiFrame, x: Double, in ctx: inout GraphicsContext) {
        let w = p.shape == .strip ? p.size * 0.5 : p.size
        let h = p.shape == .strip ? p.size * 1.4 : p.size
        var g = ctx
        g.opacity = f.alpha
        // CSS order, applied right to left: rotateY (the flip), rotate, translateX — so the context
        // is moved first, then spun, then scaled, about the piece's centre.
        g.translateBy(x: x + w / 2 + f.swayX, y: f.y + h / 2)
        g.rotate(by: .degrees(f.rotation))
        g.scaleBy(x: f.flipScale, y: 1)
        let rect = CGRect(x: -w / 2, y: -h / 2, width: w, height: h)
        let path: Path
        switch p.shape {
        case .dot: path = Path(ellipseIn: rect)
        case .square: path = Path(roundedRect: rect, cornerRadius: 2)
        case .strip: path = Path(roundedRect: rect, cornerRadius: 1.5)
        }
        g.fill(path, with: .color(p.color))
    }
}

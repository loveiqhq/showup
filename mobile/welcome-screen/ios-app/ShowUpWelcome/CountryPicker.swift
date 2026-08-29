//  CountryPicker.swift
//  ShowUp · the flag mark and the country list sheet (SHOWUP-143)
//
//  The ticket puts the country list itself out of scope, so this is the smallest thing that lets a
//  user actually change their dial code: a sheet, one row per country, the current one marked. No
//  search — at this length the list scrolls fine, and a search field is a second input to design
//  and test for no benefit yet.

import SwiftUI

/// 22 x 14 with a hairline, so a white stripe against a white field still reads as a flag.
struct FlagView: View {
    let country: Country
    var width: CGFloat = 22

    private var height: CGFloat { width * 14 / 22 }

    /// A five-pointed star, point upward: outer and inner radii alternating every 36 degrees.
    static func star(_ cx: CGFloat, _ cy: CGFloat, _ outer: CGFloat) -> Path {
        let inner = outer * 0.382          // the ratio a regular pentagram gives
        var p = Path()
        for i in 0..<10 {
            let r = i % 2 == 0 ? outer : inner
            let a = (-90.0 + Double(i) * 36.0) * .pi / 180
            let pt = CGPoint(x: cx + r * CGFloat(cos(a)), y: cy + r * CGFloat(sin(a)))
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
        return p
    }

    var body: some View {
        Group {
            switch country.flag {
            case let .bands(horizontal, stripes):
                let total = CGFloat(stripes.reduce(0) { $0 + $1.1 })
                if horizontal {
                    VStack(spacing: 0) {
                        ForEach(Array(stripes.enumerated()), id: \.offset) { _, s in
                            Color(hex: s.0).frame(height: height * CGFloat(s.1) / total)
                        }
                    }
                } else {
                    HStack(spacing: 0) {
                        ForEach(Array(stripes.enumerated()), id: \.offset) { _, s in
                            Color(hex: s.0).frame(width: width * CGFloat(s.1) / total)
                        }
                    }
                }

            case let .cross(bg, arm, inner, centred):
                // The Nordic cross sits left of centre; the Swiss one is centred. Arm thickness is
                // ~2/9 of the height either way, which reads correctly at this size.
                let t = height * 0.22
                let vx = centred ? (width - t) / 2 : width * 0.30 - t / 2
                ZStack(alignment: .topLeading) {
                    Color(hex: bg)
                    Color(hex: arm).frame(height: t).offset(y: (height - t) / 2)
                    Color(hex: arm).frame(width: t).offset(x: vx)
                    if let inner {
                        let thin = t * 0.45
                        Color(hex: inner).frame(height: thin).offset(y: (height - thin) / 2)
                        Color(hex: inner).frame(width: thin).offset(x: vx + (t - thin) / 2)
                    }
                }

            // Everything that is not just stripes: a hoist triangle, a canton, a crescent, a
            // leaf. Drawn from fractions of the box, so one description is right at any size.
            case let .layers(shapes):
                Canvas { ctx, size in
                    let w = size.width, h = size.height
                    for shape in shapes {
                        switch shape {
                        case let .fill(c):
                            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(hex: c)))

                        case let .stripes(horizontal, colors):
                            let n = CGFloat(colors.count)
                            for (i, c) in colors.enumerated() {
                                // +1 closes the hairline seams rounding leaves between bands.
                                let r = horizontal
                                    ? CGRect(x: 0, y: h * CGFloat(i) / n, width: w, height: h / n + 1)
                                    : CGRect(x: w * CGFloat(i) / n, y: 0, width: w / n + 1, height: h)
                                ctx.fill(Path(r), with: .color(Color(hex: c)))
                            }

                        case let .box(x, y, bw, bh, c):
                            ctx.fill(Path(CGRect(x: x * w, y: y * h, width: bw * w, height: bh * h)),
                                     with: .color(Color(hex: c)))

                        case let .poly(pts, c):
                            var p = Path()
                            for (i, pt) in pts.enumerated() {
                                let cg = CGPoint(x: pt.0 * w, y: pt.1 * h)
                                if i == 0 { p.move(to: cg) } else { p.addLine(to: cg) }
                            }
                            p.closeSubpath()
                            ctx.fill(p, with: .color(Color(hex: c)))

                        case let .disc(cx, cy, r, c):
                            ctx.fill(Path(ellipseIn: CGRect(x: cx * w - r * h, y: cy * h - r * h,
                                                            width: r * h * 2, height: r * h * 2)),
                                     with: .color(Color(hex: c)))

                        case let .ring(cx, cy, r, sw, c):
                            ctx.stroke(Path(ellipseIn: CGRect(x: cx * w - r * h, y: cy * h - r * h,
                                                              width: r * h * 2, height: r * h * 2)),
                                       with: .color(Color(hex: c)), lineWidth: sw * h)

                        case let .star(cx, cy, r, c):
                            ctx.fill(FlagView.star(cx * w, cy * h, r * h), with: .color(Color(hex: c)))

                        case let .checks(x, y, cw0, ch0, n, a, b):
                            let cw = cw0 * w / CGFloat(n), ch = ch0 * h / CGFloat(n)
                            for row in 0..<n {
                                for col in 0..<n {
                                    let c = (row + col) % 2 == 0 ? a : b
                                    ctx.fill(Path(CGRect(x: x * w + CGFloat(col) * cw,
                                                         y: y * h + CGFloat(row) * ch,
                                                         width: cw + 0.5, height: ch + 0.5)),
                                             with: .color(Color(hex: c)))
                                }
                            }
                        }
                    }
                }

            // Honest fallback. See the note on FlagArt.code.
            case .code:
                ZStack {
                    Color.liqRaised
                    Text(country.iso)
                        .font(F.manrope(8, .bold))
                        .tracking(0.5)
                        .foregroundColor(.liqMuted)
                }
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: 2, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .strokeBorder(Color.liqFg.opacity(0.35), lineWidth: 0.5)
        )
    }
}

/// The list, as a sheet over the screen.
///
/// Opens scrolled to the current country rather than at the top — the user is here to change a
/// value they can already see, so showing them where they are is the first useful thing.
struct CountrySheet: View {
    let current: Country
    let onPick: (Country) -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Capsule()
                .fill(Color.liqBorder)
                .frame(width: 40, height: 4)
                .padding(.top, 10)

            Text("Choose your country")
                .font(F.manrope(17, .bold))
                .foregroundColor(.liqFg)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.top, 16)
                .padding(.bottom, 12)

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(COUNTRIES) { c in
                            let selected = c.iso == current.iso
                            Button {
                                onPick(c)
                            } label: {
                                HStack(spacing: 14) {
                                    FlagView(country: c)
                                    Text(c.name)
                                        .font(F.manrope(15, selected ? .bold : .medium))
                                        .foregroundColor(.liqFg)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    Text(c.dial)
                                        .font(F.manrope(15, .semibold))
                                        .foregroundColor(selected ? .liqPurple : .liqMuted)
                                    if selected {
                                        BrandIconView(icon: .check, size: 16, stroke: 2.4, tint: .liqPurple)
                                    } else {
                                        Color.clear.frame(width: 16, height: 16)
                                    }
                                }
                                .padding(.horizontal, 24)
                                .padding(.vertical, 13)
                                .background(selected ? Color.liqOrange.opacity(0.07) : Color.clear)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .id(c.iso)
                        }
                    }
                }
                .onAppear { proxy.scrollTo(current.iso, anchor: .center) }
            }

            PillButton("Close", variant: .ghost, action: onDismiss)
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
        }
        .background(Color.liqElevated)
    }
}

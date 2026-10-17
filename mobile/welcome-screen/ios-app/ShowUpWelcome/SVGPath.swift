//  SVGPath.swift
//  ShowUp · an SVG path string, read into a SwiftUI `Path` at its own 24-unit geometry
//
//  The Swift twin of the Kotlin side's `PathParser` use in `WelcomeShell.kt` (`KitPaths`). Most of
//  the icons in `BrandIconView` are hand-built from their SVG, which is fine for lines and the
//  odd quarter circle. The gear and the flame the 48hrs Check-In added (SHOWUP-178, 180) are
//  nothing but arcs and curves -- twenty-odd `a1.65 1.65 0 0 0 ...` segments -- and converting each
//  arc's END-point form to SwiftUI's CENTRE form by hand is exactly where a sign flips unseen. So
//  the kit's own `d` strings are read here, by the algorithm the SVG specification gives (F.6.5),
//  and the drawing is the kit's by construction.
//
//  Supports what the kit's paths use: M L H V C S A Z, absolute and relative, with implicit repeats.
//  Arcs are circular in every kit path (rx == ry, no rotation); an elliptical one is drawn with the
//  mean radius rather than refused, and none exists today. Anything else ends the path where it is.

import SwiftUI

enum SVGPath {
    /// The path `d` describes, in its own coordinates.
    static func parse(_ d: String) -> Path {
        var path = Path()
        var scanner = PathScanner(d)
        var current = CGPoint.zero
        var start = CGPoint.zero
        // The previous cubic's second control point, for `S`.
        var lastControl: CGPoint?
        var command: Character = "M"

        while scanner.skipSeparators() {
            if let c = scanner.command() { command = c }
            let relative = command.isLowercase
            func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                relative ? CGPoint(x: current.x + x, y: current.y + y) : CGPoint(x: x, y: y)
            }
            switch command.lowercased().first ?? "z" {
            case "m":
                guard let x = scanner.number(), let y = scanner.number() else { return path }
                current = point(x, y)
                start = current
                path.move(to: current)
                lastControl = nil
                // Coordinates after a move are an implicit line-to.
                command = relative ? "l" : "L"
            case "l":
                guard let x = scanner.number(), let y = scanner.number() else { return path }
                current = point(x, y)
                path.addLine(to: current)
                lastControl = nil
            case "h":
                guard let x = scanner.number() else { return path }
                current = CGPoint(x: relative ? current.x + x : x, y: current.y)
                path.addLine(to: current)
                lastControl = nil
            case "v":
                guard let y = scanner.number() else { return path }
                current = CGPoint(x: current.x, y: relative ? current.y + y : y)
                path.addLine(to: current)
                lastControl = nil
            case "c":
                guard let x1 = scanner.number(), let y1 = scanner.number(),
                      let x2 = scanner.number(), let y2 = scanner.number(),
                      let x = scanner.number(), let y = scanner.number() else { return path }
                let c1 = point(x1, y1)
                let c2 = point(x2, y2)
                current = point(x, y)
                path.addCurve(to: current, control1: c1, control2: c2)
                lastControl = c2
            case "s":
                guard let x2 = scanner.number(), let y2 = scanner.number(),
                      let x = scanner.number(), let y = scanner.number() else { return path }
                // The first control point mirrors the previous cubic's second.
                let c1 = lastControl.map { CGPoint(x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
                let c2 = point(x2, y2)
                current = point(x, y)
                path.addCurve(to: current, control1: c1, control2: c2)
                lastControl = c2
            case "a":
                guard let rx = scanner.number(), let ry = scanner.number(), scanner.number() != nil,
                      let large = scanner.flag(), let sweep = scanner.flag(),
                      let x = scanner.number(), let y = scanner.number() else { return path }
                let end = point(x, y)
                addArc(to: &path, from: current, to: end, radius: (rx + ry) / 2, large: large, sweep: sweep)
                current = end
                lastControl = nil
            case "z":
                path.closeSubpath()
                current = start
                lastControl = nil
            default:
                return path
            }
        }
        return path
    }

    /// An SVG circular arc from `p1` to `p2`, converted from end-point to centre form (SVG F.6.5).
    ///
    /// The radius grows when it is too small to span the chord, as the specification requires. The
    /// sweep flag runs the angle POSITIVE (clockwise on screen, y down); SwiftUI's `clockwise` is in
    /// the flipped sense, so sweep 1 is `clockwise: false`.
    static func addArc(to path: inout Path, from p1: CGPoint, to p2: CGPoint, radius: CGFloat,
                       large: Bool, sweep: Bool) {
        guard radius > 0, p1 != p2 else {
            path.addLine(to: p2)
            return
        }
        let hx = (p1.x - p2.x) / 2
        let hy = (p1.y - p2.y) / 2
        let half = hx * hx + hy * hy
        var r = radius
        if half > r * r { r = sqrt(half) }
        let coefficient = sqrt(max(0, (r * r - half) / half)) * (large == sweep ? -1 : 1)
        let centre = CGPoint(x: coefficient * hy + (p1.x + p2.x) / 2,
                             y: -coefficient * hx + (p1.y + p2.y) / 2)
        let a1 = atan2(p1.y - centre.y, p1.x - centre.x)
        let a2 = atan2(p2.y - centre.y, p2.x - centre.x)
        path.addArc(center: centre, radius: r, startAngle: .radians(Double(a1)),
                    endAngle: .radians(Double(a2)), clockwise: !sweep)
    }

    /// Reads numbers, flags and commands out of a path string. SVG lets numbers run together --
    /// `.34.7.34` is three of them, `1.65-1.82` two -- so this scans rather than splits.
    private struct PathScanner {
        private let chars: [Character]
        private var i = 0

        init(_ s: String) { chars = Array(s) }

        /// Skips whitespace and commas. False at the end of the string.
        mutating func skipSeparators() -> Bool {
            while i < chars.count, chars[i] == " " || chars[i] == "," || chars[i].isNewline || chars[i] == "\t" {
                i += 1
            }
            return i < chars.count
        }

        /// A command letter, if one is next.
        mutating func command() -> Character? {
            guard i < chars.count, chars[i].isLetter, chars[i] != "e", chars[i] != "E" else { return nil }
            defer { i += 1 }
            return chars[i]
        }

        /// An arc flag: one character, `0` or `1`, possibly with no separator after it.
        mutating func flag() -> Bool? {
            _ = skipSeparators()
            guard i < chars.count, chars[i] == "0" || chars[i] == "1" else { return nil }
            defer { i += 1 }
            return chars[i] == "1"
        }

        mutating func number() -> CGFloat? {
            _ = skipSeparators()
            var s = ""
            if i < chars.count, chars[i] == "-" || chars[i] == "+" { s.append(chars[i]); i += 1 }
            var seenDot = false
            var seenDigit = false
            while i < chars.count {
                let c = chars[i]
                if c.isNumber { s.append(c); seenDigit = true; i += 1 }
                else if c == ".", !seenDot { s.append(c); seenDot = true; i += 1 }
                else { break }
            }
            if i < chars.count, chars[i] == "e" || chars[i] == "E" {
                s.append("e"); i += 1
                if i < chars.count, chars[i] == "-" || chars[i] == "+" { s.append(chars[i]); i += 1 }
                while i < chars.count, chars[i].isNumber { s.append(chars[i]); i += 1 }
            }
            guard seenDigit, let v = Double(s) else { return nil }
            return CGFloat(v)
        }
    }
}

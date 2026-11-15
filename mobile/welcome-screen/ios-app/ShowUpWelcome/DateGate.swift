// DateGate.swift
// ShowUp · the location gate's DATE context: the date card and its countdown pill (SHOWUP-195 G).
// Android's `DateGate.kt`.
//
// The kit's `DateGateCard` (docs/superpowers/specs/kit/2026-11-10-date-gate-kit-excerpt.jsx): the date on the gate,
// in the purple of the Date screen's time + place card, so the person recognises what they are locked out of. The
// layout around it is `ProfileLocationView`'s own date context -- one location screen, three contexts, never a copy.

import SwiftUI
import UIKit

/// The pill under the date card: counting down before the meet time, up after it.
enum GatePill: Equatable, Sendable {
    case starts(hours: Int, minutes: Int)
    case started(minutes: Int)
}

/// `Starts in {h}h {m}m` today before the meet time, `Started {n} min ago` after it, nothing for tomorrow. Counting
/// down is rounded up to the minute; counting up is whole minutes elapsed.
func gatePill(meetDay: String, meetAt: Date, now: Date) -> GatePill? {
    let seconds = Int(meetAt.timeIntervalSince1970.rounded(.down)) - Int(now.timeIntervalSince1970.rounded(.down))
    if now >= meetAt { return .started(minutes: (-seconds) / 60) }
    guard meetDay == "today" else { return nil }
    // Counting down rounds UP, so the last minute reads 0h 1m and never 0h 0m. Before the meet time the seconds
    // are positive, so integer division is the floor.
    let delta = (seconds + 59) / 60
    return .starts(hours: delta / 60, minutes: delta % 60)
}

/// Padding 14, radius 20, `#E8DCF5`, gap 12. Row one: the match's avatar (40, radius 13), gap 10, `You and {name} are
/// meeting {day}` in Lora 700 19 / 1.2, balanced, the day in violet italic. Row two: `clock` and `map-pin` 17 / 2.2
/// violet beside Lora 700 19 values, gap 14, wrapping. Row three, when there is a pill: h 30, pad 0 12, pill,
/// `clock` 14 / 2.4, Manrope 700 13 white -- orange `Starts in`, red `Started`, followed after the meet time by
/// `{name} may be waiting` in red 13 / 700.
struct DateGateCard: View {
    let name: String
    let meetDay: String
    let meetAt: Date
    let place: String?
    let photoUrl: URL?
    let now: Date
    let zone: TimeZone

    var body: some View {
        let pill = gatePill(meetDay: meetDay, meetAt: meetAt, now: now)
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 10) {
                MatchPhoto(name: name, photoURL: photoUrl, size: 40, cornerRadius: 13)
                title
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            WrapLayout(spacing: 14, lineSpacing: 14) {
                CardValue(icon: .clock, value: meetTime(meetAt, zone: zone))
                if let place { CardValue(icon: .mapPin, value: place) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let pill {
                WrapLayout(spacing: 10, lineSpacing: 10) {
                    let late: Bool = { if case .started = pill { return true } else { return false } }()
                    HStack(spacing: 6) {
                        BrandIconView(icon: .clock, size: 14, stroke: 2.4, tint: .white)
                            .accessibilityHidden(true)
                        Text(pillText(pill))
                            .font(F.manrope(13, .bold))
                            .foregroundColor(.white)
                            // Wraps inside the row rather than running out of it on a narrow phone (`Started N min
                            // ago` at 200 % type on a 320 phone, 13 Nov 2026): the capsule grows a line, as Android's.
                            .multilineTextAlignment(.leading)
                    }
                    .padding(.horizontal, 12)
                    .frame(minHeight: 30)
                    .background(Capsule().fill(late ? Color.liqDanger : Color.liqOrange))
                    .fitAnchor(FitAnchorID.dateGatePill)
                    if late {
                        Text(LocationCopy.Date.mayBeWaiting(name))
                            .font(F.manrope(13, .bold))
                            .foregroundColor(.liqDanger)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color.liqLilacCard))
        .fitAnchor(FitAnchorID.dateGateCard)
        .accessibilityElement(children: .combine)
    }

    private func pillText(_ pill: GatePill) -> String {
        switch pill {
        case .starts(let hours, let minutes): return LocationCopy.Date.startsIn(hours, minutes)
        case .started(let minutes): return LocationCopy.Date.started(minutes)
        }
    }

    /// `You and {name} are meeting `, then the day in violet italic.
    private var title: some View {
        var text = PrettyText(
            runs: [(LocationCopy.Date.cardLead + name + LocationCopy.Date.cardMid, false), (meetDay, true)],
            font: TypeMetrics.uiFont(PS.loraBold, 19, fallback: .systemFont(ofSize: 19, weight: .bold)),
            lineHeight: 19 * 1.2, color: .liqFg,
            emphasisFont: TypeMetrics.uiFont(PS.loraBoldItalic, 19, fallback: .italicSystemFont(ofSize: 19)),
            emphasisColor: .liqPurple)
        text.balance = true
        return text
    }
}

/// An icon (17 / 2.2, violet) and a Lora 700 19 value, gap 6.
private struct CardValue: View {
    let icon: BrandIcon
    let value: String

    var body: some View {
        HStack(spacing: 6) {
            BrandIconView(icon: icon, size: 17, stroke: 2.2, tint: .liqPurple)
                .accessibilityHidden(true)
            Text(value)
                .font(F.lora(19, bold: true))
                .foregroundColor(.liqFg)
        }
    }
}

//
//  CheckInAvailabilityChrome.swift
//  ShowUp · Check-In 02's parts (SHOWUP-179): HowItWorks, ClearAll, AvailabilitySlot, the grid,
//  AvailabilitySummary, LegendPill, the legend
//
//  The Swift twin of `checkin/AvailabilityChrome.kt` (the dialog is `CheckInNoHoursDialog.swift`).
//  Numbers from `screen-checkin-availability-reference.jsx`. `HowItWorks` is built here once and is
//  the explainer every later step would reuse.
//

import SwiftUI

/// What VoiceOver is told on Check-In 02, as plain functions so they are tested with no view --
/// SwiftUI builds no accessibility tree in a unit test (`FitDevices.swift` has the record).
enum CheckInA11y {
    /// A slot's state after its hour: `5pm, not selected` / `preferred` / `available` (story 02).
    static func slotValue(_ tier: SlotTier?) -> String {
        switch tier {
        case .none: return "not selected"
        case .some(.preferred): return "preferred"
        case .some(.available): return "available"
        }
    }

    /// How it works' disclosure state.
    static func disclosureValue(_ open: Bool) -> String { open ? "Expanded" : "Collapsed" }

    /// A selectable row or chip's state, as `CheckRow` and `LifePill` already say it.
    static func checkedValue(_ on: Bool) -> String { on ? "Checked" : "Not checked" }
}

/// A slot's three looks, and the legend's: fill, ink, border.
private struct TierLook {
    let fill: Color
    let ink: Color
    let border: Color

    init(_ tier: SlotTier?) {
        switch tier {
        case .none:
            fill = .liqElevated
            ink = .liqFg
            border = .liqBorder
        case .some(.preferred):
            fill = .liqPurple
            ink = .white
            border = .liqPurple
        case .some(.available):
            fill = .liqTierAvailableFill
            ink = .liqTierAvailableInk
            border = .liqTierAvailableBorder
        }
    }
}

/// `HowItWorks` -- the disclosure row and its panel. The row: `alert-circle` 15 + `How it works`
/// (13 / 700, primary) + a chevron that turns over in 200 ms; `action` sits at the trailing end
/// (Clear all). The panel opens inline, 10 below: lilac, padding 14 / 16, radius 14, 13 / 1.5,
/// paragraphs 8 apart, entering with `su-toast-in`. Collapsed on every entry (the model resets it).
struct HowItWorks<Action: View>: View {
    let open: Bool
    let onToggle: () -> Void
    @ViewBuilder let action: () -> Action
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 0) {
                // The disclosure: a button with its state spoken, answering at 44 without moving
                // anything.
                Button(action: onToggle) {
                    HStack(spacing: Spacing.sm) {
                        BrandIconView(icon: .alertCircle, size: 15, stroke: 2, tint: .liqPurple)
                        Text(CheckInCopy.howItWorks)
                            .font(F.manrope(13, .bold))
                            .foregroundColor(.liqPurple)
                        BrandIconView(icon: .chevronDown, size: 15, stroke: 2.4, tint: .liqPurple)
                            .rotationEffect(.degrees(open ? 180 : 0))
                            .animation(reduceMotion ? nil : Motion.cssEase(Motion.reveal), value: open)
                    }
                    .overflowTapTarget()
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(CheckInCopy.howItWorks))
                .accessibilityValue(Text(CheckInA11y.disclosureValue(open)))
                .accessibilityAddTraits(.isButton)
                Spacer(minLength: Spacing.md)
                action()
            }
            if open {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    ForEach(Array(CheckInCopy.howItWorksPanel.enumerated()), id: \.offset) { _, runs in
                        paragraph(runs)
                    }
                }
                .padding(.horizontal, Spacing.xxl)
                .padding(.vertical, 14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: Radius.control).fill(Color.liqCoachLilacFill))
                .modifier(CheckInReveal())
                .padding(.top, Spacing.lg)
            }
        }
    }

    private func paragraph(_ runs: [(String, Bool)]) -> some View {
        runs.reduce(Text("")) { text, run in
            text + Text(run.0).font(F.manrope(13, run.1 ? .bold : .regular))
        }
        .foregroundColor(.liqCoachLilacInk)
        .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 13, multiple: 1.5))
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// `Clear all` -- `x` 14 + 13 / 700 in `--liq-fg-subtle`, gap 5. Shown only with a selection.
struct ClearAll: View {
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 5) {
                BrandIconView(icon: .close, size: 14, stroke: 2.2, tint: .liqSubtle)
                Text(CheckInCopy.clearAll)
                    .font(F.manrope(13, .bold))
                    .foregroundColor(.liqSubtle)
            }
            .overflowTapTarget()
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(CheckInCopy.clearAll))
        .accessibilityAddTraits(.isButton)
    }
}

/// `AvailabilitySlot` -- one hour: 36 tall, a full pill, 13 / 600, colours per tier, `all 180ms`.
/// DRAWN 36, ANSWERS AT 44 (`overflowTapTarget`); rows are 5 apart, so two rows' touch areas share
/// a band in the gap, where the lower slot wins -- the chips' and pills' compromise. The label
/// shrinks to the pill rather than truncating: at the largest type on a 320 phone `noon` is wider
/// than its slot, and `no...` is no label at all.
struct AvailabilitySlot: View {
    let hour: Int
    let tier: SlotTier?
    let onClick: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let look = TierLook(tier)
        Button(action: onClick) {
            Text(formatHour(hour))
                .font(F.manrope(13, .semibold))
                .foregroundColor(look.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .padding(.horizontal, Spacing.xs)
                .frame(maxWidth: .infinity, minHeight: 36)
                .background(Capsule().fill(look.fill))
                .overlay(Capsule().strokeBorder(look.border, lineWidth: 1))
                .animation(reduceMotion ? nil : Motion.cssEase(Motion.fast), value: tier)
                .overflowTapTarget()
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(formatHour(hour)))
        .accessibilityValue(Text(CheckInA11y.slotValue(tier)))
        .accessibilityAddTraits(.isButton)
    }
}

/// The grid: four columns, 5 apart both ways. Every slot of the day, in order.
struct AvailabilityGridRows: View {
    let grid: AvailabilityGrid
    let picked: [Int: SlotTier]
    let onTap: (Int) -> Void

    var body: some View {
        let rows = stride(from: 0, to: grid.hours.count, by: 4).map { Array(grid.hours[$0..<min($0 + 4, grid.hours.count)]) }
        VStack(spacing: 5) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: 5) {
                    ForEach(row, id: \.self) { h in
                        AvailabilitySlot(hour: h, tier: picked[h], onClick: { onTap(h) })
                    }
                    // A short last row keeps its cells the width of the full rows above.
                    ForEach(0..<(4 - row.count), id: \.self) { _ in
                        Color.clear.frame(maxWidth: .infinity, maxHeight: 0)
                    }
                }
            }
        }
    }
}

/// "Auto-shrinks to 0.78" -- the summary's floor before it wraps instead.
private let summaryMinScale: CGFloat = 0.78
/// The least room beside the label the ranges wrap in before they move under it.
private let summaryMinValue: CGFloat = 80

/// `AvailabilitySummary` -- what the taps add up to: `Your availability` beside the ranges. Lilac
/// once anything is picked, white before.
///
/// THREE LAYOUTS, chosen by what fits, and at the default type always the first: the ranges on the
/// label's line, shrunk to 0.78 at most -- "one line, never wraps". Past that floor, which only the
/// largest type reaches, the ranges WRAP beside the label rather than lose their end ("never an
/// incomplete truth"); and when even that leaves under 80, they go under the label.
struct AvailabilitySummary: View {
    let grid: AvailabilityGrid
    let picked: [Int: SlotTier]

    var body: some View {
        let summary = availabilitySummary(grid.hours, picked)
        let any = summary != nil
        SummaryLayout(gap: Spacing.md) {
            Text(CheckInCopy.summaryLabel)
                .font(F.manrope(12.5, .bold))
                .foregroundColor(any ? .liqTierAvailableInk : .liqSubtle)
                .lineLimit(1)
                .minimumScaleFactor(summaryMinScale)
            Text(summary ?? CheckInCopy.summaryEmpty)
                .font(F.manrope(13.5, any ? .heavy : .semibold))
                .foregroundColor(any ? .liqPrimary600 : .liqFaint)
                .minimumScaleFactor(summaryMinScale)
        }
        // Border-box: the 1 border is outside the 8 / 14 padding, so the inset is the sum.
        .padding(.horizontal, 15)
        .padding(.vertical, Spacing.md + 1)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.errorBox).fill(any ? Color.liqTierAvailableFill : Color.liqElevated))
        .overlay(RoundedRectangle(cornerRadius: Radius.errorBox)
            .strokeBorder(any ? Color.liqTierAvailableBorder : Color.liqBorderSoft, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}

/// The summary's three layouts. The label and the ranges, first baselines aligned on one line.
private struct SummaryLayout: Layout {
    let gap: CGFloat

    private enum Mode { case shrunk, wrapped, stacked }

    private struct Plan {
        let mode: Mode
        let label: CGSize
        let value: CGSize
        let valueProposal: ProposedViewSize
    }

    private func plan(_ width: CGFloat, _ subviews: Subviews) -> Plan {
        let label = subviews[0].sizeThatFits(.unspecified)
        let need = subviews[1].sizeThatFits(.unspecified)
        let room = width - label.width - gap
        if need.width * summaryMinScale <= room {
            // One line: the height is the ranges' own single line, so the text shrinks, not wraps.
            let proposal = ProposedViewSize(width: room, height: need.height)
            return Plan(mode: .shrunk, label: label, value: subviews[1].sizeThatFits(proposal), valueProposal: proposal)
        }
        if room >= summaryMinValue {
            let proposal = ProposedViewSize(width: room, height: nil)
            return Plan(mode: .wrapped, label: label, value: subviews[1].sizeThatFits(proposal), valueProposal: proposal)
        }
        let full = ProposedViewSize(width: width, height: nil)
        return Plan(mode: .stacked, label: subviews[0].sizeThatFits(full),
                    value: subviews[1].sizeThatFits(full), valueProposal: full)
    }

    /// The two first baselines, and the y each subview sits at so they share one.
    private func rowOffsets(_ p: Plan, _ subviews: Subviews) -> (label: CGFloat, value: CGFloat) {
        let lb = subviews[0].dimensions(in: ProposedViewSize(p.label))[VerticalAlignment.firstTextBaseline]
        let vb = subviews[1].dimensions(in: p.valueProposal)[VerticalAlignment.firstTextBaseline]
        let line = max(lb, vb)
        return (line - lb, line - vb)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        let width = proposal.width ?? 0
        let p = plan(width, subviews)
        switch p.mode {
        case .shrunk, .wrapped:
            let y = rowOffsets(p, subviews)
            return CGSize(width: width, height: max(y.label + p.label.height, y.value + p.value.height))
        case .stacked:
            return CGSize(width: width, height: p.label.height + 2 + p.value.height)
        }
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let p = plan(bounds.width, subviews)
        switch p.mode {
        case .shrunk, .wrapped:
            let y = rowOffsets(p, subviews)
            subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.minY + y.label), anchor: .topLeading,
                              proposal: ProposedViewSize(p.label))
            subviews[1].place(at: CGPoint(x: bounds.minX + p.label.width + gap, y: bounds.minY + y.value),
                              anchor: .topLeading, proposal: p.valueProposal)
        case .stacked:
            subviews[0].place(at: bounds.origin, anchor: .topLeading, proposal: ProposedViewSize(width: bounds.width, height: nil))
            subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.minY + p.label.height + 2), anchor: .topLeading,
                              proposal: p.valueProposal)
        }
    }
}

/// `LegendPill` -- padding 5 / 10, a full pill, 11.5 / 700, in a tier's colours; `clear` is idle's.
struct LegendPill: View {
    let label: String
    let tier: SlotTier?

    var body: some View {
        let look = TierLook(tier)
        Text(label)
            .font(F.manrope(11.5, .bold))
            .foregroundColor(look.ink)
            .lineLimit(1)
            .fixedSize()
            // Border-box: the 1 border is outside the 5 / 10 padding, so the inset is the sum.
            .padding(.horizontal, Spacing.lg + 1)
            .padding(.vertical, 6)
            .background(Capsule().fill(look.fill))
            .overlay(Capsule().strokeBorder(look.border, lineWidth: 1))
    }
}

/// The legend, pinned above the CTA: a hairline, then `Tap an hour to cycle through:` (11 / 700,
/// muted) over the three pills 7 apart. mt 6, pt 9, mb 8; its two lines 5 apart.
struct AvailabilityLegend: View {
    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Color.liqBorderSoft).frame(height: 1)
            Color.clear.frame(height: 9)
            // `white-space: nowrap` in the reference -- one line at the default type, centred, and
            // allowed to wrap at the largest type rather than run off the screen.
            Text(CheckInCopy.legendLead)
                .font(F.manrope(11, .bold))
                .foregroundColor(.liqMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Color.clear.frame(height: 5)
            LegendRowLayout(gap: 7, rowGap: 5, spill: 16) {
                LegendPill(label: CheckInCopy.legendPreferred, tier: .preferred)
                LegendPill(label: CheckInCopy.legendAvailable, tier: .available)
                LegendPill(label: CheckInCopy.legendClear, tier: nil)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.sm)
        .padding(.bottom, Spacing.md)
        .accessibilityElement(children: .combine)
    }
}

/// The three legend pills, centred on ONE row -- as the reference lays them out, which does not fit
/// every phone: Chrome measures the row at 338, so on a 375 phone (327 to give) it spills about 6
/// into each side gutter, and on a 320 it would run off the screen. So: one centred row while it
/// fits the column OR spills no more than `spill` into each gutter -- exactly the reference at 375
/// -- and centred rows, `rowGap` apart, past that (the 320 phone, the largest type), never a
/// truncated pill.
private struct LegendRowLayout: Layout {
    let gap: CGFloat
    let rowGap: CGFloat
    let spill: CGFloat

    private func rows(_ width: CGFloat, _ sizes: [CGSize]) -> [[Int]] {
        let total = sizes.map(\.width).reduce(0, +) + gap * CGFloat(max(sizes.count - 1, 0))
        if total <= width + 2 * spill { return [Array(sizes.indices)] }
        var out: [[Int]] = [[]]
        var used: CGFloat = 0
        for i in sizes.indices {
            let next = out[out.count - 1].isEmpty ? sizes[i].width : used + gap + sizes[i].width
            if !out[out.count - 1].isEmpty && next > width {
                out.append([i])
                used = sizes[i].width
            } else {
                out[out.count - 1].append(i)
                used = next
            }
        }
        return out
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        let width = proposal.width ?? sizes.map(\.width).reduce(0, +)
        let laid = rows(width, sizes)
        let heights = laid.map { row in row.map { sizes[$0].height }.max() ?? 0 }
        return CGSize(width: width, height: heights.reduce(0, +) + rowGap * CGFloat(max(laid.count - 1, 0)))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        var y = bounds.minY
        for row in rows(bounds.width, sizes) {
            let rowWidth = row.map { sizes[$0].width }.reduce(0, +) + gap * CGFloat(max(row.count - 1, 0))
            let height = row.map { sizes[$0].height }.max() ?? 0
            var x = bounds.minX + (bounds.width - rowWidth) / 2
            for i in row {
                subviews[i].place(at: CGPoint(x: x, y: y + (height - sizes[i].height) / 2), anchor: .topLeading,
                                  proposal: ProposedViewSize(sizes[i]))
                x += sizes[i].width + gap
            }
            y += height + rowGap
        }
    }
}

// ChangeSheets.swift
// ShowUp · the time and place pickers, Date 05 A, B, K, M and N (SHOWUP-196), from `screen-change-reference.jsx`
//
// One shell, `ChangeFullSheet`: a full screen with one scrolling list and a footer pinned to the bottom of the
// column -- the binding note (or K's error) over the two buttons. The footer is always above the fold; the list
// ends above it, so its last row scrolls clear. Android's `ChangeSheets.kt`.
//
// The screens take values and closures: `ChangePickerUi` in, taps out. They never receive a navigator, so every
// frame renders in every state at every size with no device.

import SwiftUI

/// The values both pickers read from the date, already the date's own: the match's first name, the BINDING time and
/// place (a pending suggestion moves nothing), the server's later slots and the phone's zone.
struct ChangeSheetContext: Equatable, Sendable {
    var name: String
    var bindingTime: Date
    var bindingPlace: String
    var slots: [Date]
    var zone: TimeZone

    var time: String { changeClock(bindingTime, zone: zone) }
    var deadline: String { changeClock(changeDeadline(binding: bindingTime), zone: zone) }
}

// MARK: the shell

/// The full-screen shell: the list scrolls (pad 0 20), the footer sits at the bottom of the column (pad 14 20, gap
/// 10, the soft rule above, a violet-free ink shadow cast up). The footer's fill is the reference's 94% cream, drawn
/// as the opaque cream it composites to over the cream page and WITHOUT backdrop blur: the 193 rule for pinned
/// bars -- a translucent bar over white rows would show their text through it.
///
/// It is a `safeAreaInset`, so it rides the keyboard and the home indicator instead of being placed by a Y, and the
/// list ends above it. The reference's 30 bottom padding is the mock home indicator; here the real inset replaces it.
private struct ChangeNoteCapKey: EnvironmentKey {
    static let defaultValue: CGFloat = .infinity
}

extension EnvironmentValues {
    /// The tallest the footer's note may be before it scrolls (a quarter of the sheet): the buttons stay pinned.
    fileprivate var changeNoteCap: CGFloat {
        get { self[ChangeNoteCapKey.self] }
        set { self[ChangeNoteCapKey.self] = newValue }
    }
}

struct ChangeFullSheet<Content: View, Footer: View>: View {
    @ViewBuilder let content: () -> Content
    @ViewBuilder let footer: () -> Footer

    var body: some View {
        // A quarter of the screen caps the footer's note, which scrolls inside it, so at the largest type on a short
        // phone the note never pushes the buttons off the screen. Android's `ChangeFooter`.
        GeometryReader { proxy in
            sheet(footerCap: proxy.size.height * 0.25)
        }
    }

    private func sheet(footerCap: CGFloat) -> some View {
        ScrollView {
            VStack(spacing: 0) { content() }
                .padding(EdgeInsets(top: Spacing.md, leading: 20, bottom: 16, trailing: 20))
                .fitAnchor("change:list")
        }
        .background(Color.liqCream.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            footer()
                .environment(\.changeNoteCap, footerCap)
                // Pad 14 20 plus the 1 pt rule CSS counts in the box.
                .padding(EdgeInsets(top: 15, leading: 20, bottom: 14, trailing: 20))
                .frame(maxWidth: .infinity)
                .background(
                    Color.liqCream
                        .shadow(color: Color.liqFg.opacity(0.08), radius: 14, y: -10)
                        .ignoresSafeArea(edges: .bottom))
                .overlay(alignment: .top) { Rectangle().fill(Color.liqBorderSoft).frame(height: 1) }
                .fitAnchor("change:footer")
                .fitPinnedBar()
        }
    }
}

// MARK: the head

private struct ChangeSheetHead: View {
    let title: String
    let currentLabel: String
    let currentValue: String
    let line2: String
    let line3: String

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14)
        VStack(spacing: 0) {
            // Margin 4 0 6.
            Text(title)
                .font(F.lora(24, bold: true)).tracking(-0.01 * 24)
                .foregroundColor(.liqFg)
                .multilineTextAlignment(.center)
                // Lora 24 in CSS line boxes of 1.15, which only UIKit sets: the title's lines sit as drawn.
                .cssLineBox(24 * 1.15)
                .padding(.top, 4).padding(.bottom, 6)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: 0) {
                (Text(currentLabel).foregroundColor(.liqMuted)
                    + Text(currentValue).fontWeight(.bold).foregroundColor(.liqFg))
                    .font(F.manrope(13.5, .regular))
                    .frame(maxWidth: .infinity, alignment: .leading)
                subLine(line2)
                subLine(line3).padding(.top, 6)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            // Pad 12 14 plus the 1 pt border, which CSS counts in the box.
            .padding(EdgeInsets(top: 13, leading: 15, bottom: 13, trailing: 15))
            .background(shape.fill(Color.liqPurple.opacity(0.07)))
            .overlay(shape.strokeBorder(Color.liqPurple.opacity(0.14), lineWidth: 1))
        }
        .padding(.bottom, 16)
    }

    private func subLine(_ text: String) -> some View {
        PrettyText(text, font: chatFont(PS.manropeRegular, 13.5, .regular), lineHeight: 13.5 * 1.45, color: .liqMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: the binding note and the error

/// The binding rule (A, B, C, D): the amber box 12 14 (compact 9 11), radius 14, `alert-circle` 17 (15), the first
/// sentence bold. `youDecide`: the match suggested it, so the binding holds until YOU agree. Strings in, so the card,
/// the request card and the pickers each pass the values they hold.
///
/// The amber is the kit's warning tone for this one note (`rgba(245,158,11,.09)`, `#78350f`), which no colour token
/// holds -- it is Date 05's alone, so it stays here.
struct BindingNote: View {
    let name: String
    let time: String
    let place: String
    let deadline: String
    var compact = false
    var youDecide = false

    private static let ink = Color(hex: 0x78350F)
    private static let icon = Color(hex: 0xB45309)

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14)
        let size: CGFloat = compact ? 12 : 13
        let bold = youDecide
            ? ChangeCopy.bindingBoldYou(time: time, place: place, deadline: deadline)
            : ChangeCopy.bindingBold(time: time, place: place, name: name, deadline: deadline)
        HStack(alignment: .top, spacing: 10) {
            BrandIconView(icon: .alertCircle, size: compact ? 15 : 17, stroke: 2.2, tint: Self.icon)
                .padding(.top, 1)
                .accessibilityHidden(true)
            PrettyText(runs: [(bold, true), (ChangeCopy.bindingRest, false)],
                       font: chatFont(PS.manropeRegular, size, .regular), lineHeight: size * 1.45, color: Self.ink,
                       emphasisFont: chatFont(PS.manropeBold, size, .bold), emphasisColor: Self.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        // Pad 12 14 (compact 9 11) plus the 1 pt border.
        .padding(EdgeInsets(top: compact ? 10 : 13, leading: compact ? 12 : 15, bottom: compact ? 10 : 13, trailing: compact ? 12 : 15))
        .background(shape.fill(Color(hex: 0xF59E0B).opacity(0.09)))
        .overlay(shape.strokeBorder(Color(hex: 0xD97706).opacity(0.28), lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}

/// K's box, the Date 02 / 194 `SheetErrorBox` look with the ticket's string: it replaces the binding note.
struct ChangeError: View {
    let message: String

    var body: some View { SheetErrorBox(message: message) }
}

// MARK: the footer both pickers share

private struct ChangeFooter: View {
    let ctx: ChangeSheetContext
    let ui: ChangePickerUi
    let primary: String
    let onSuggest: () -> Void
    let onCancel: () -> Void
    @Environment(\.changeNoteCap) private var noteCap

    @ViewBuilder private var note: some View {
        if ui.sendFailed {
            ChangeError(message: ChangeCopy.sendFailed)
        } else {
            BindingNote(name: ctx.name, time: ctx.time, place: ctx.bindingPlace, deadline: ctx.deadline)
        }
    }

    var body: some View {
        VStack(spacing: 10) {
            ViewThatFits(in: .vertical) {
                note
                ScrollView(.vertical, showsIndicators: false) { note }
            }
            .frame(maxHeight: noteCap)
            SheetPrimaryButton(label: primary, enabled: ui.hasSelection, waiting: ui.sending, anchor: "change:primary", action: onSuggest)
                .accessibilityIdentifier("change-primary")
            SheetSecondaryButton(label: ChangeCopy.cancel, inert: ui.sending, anchor: "change:cancel", action: onCancel)
                .accessibilityIdentifier("change-cancel")
        }
    }
}

// MARK: A · the time picker

/// A, and K when `ChangePickerUi.sendFailed`: the head, then a 4-column grid of the server's slots (gap 6, h 44,
/// radius 12). The primary reads `Pick a time` (disabled) until one is selected, then `Suggest {slot}`.
struct TimeChangeSheet: View {
    let ctx: ChangeSheetContext
    let ui: ChangePickerUi
    let onSelect: (Date) -> Void
    let onSuggest: () -> Void
    let onCancel: () -> Void

    var body: some View {
        let latest = latestSlot(ctx.slots).map { changeClock($0, zone: ctx.zone) } ?? ""
        ChangeFullSheet {
            ChangeSheetHead(
                title: ChangeCopy.timeTitle,
                currentLabel: ChangeCopy.timeCurrent, currentValue: ctx.time,
                line2: ChangeCopy.timeChoose(name: ctx.name, latest: latest),
                line3: ChangeCopy.agreeBy(name: ctx.name, deadline: ctx.deadline, binding: ctx.time))
            slotGrid
        } footer: {
            ChangeFooter(
                ctx: ctx, ui: ui,
                primary: ui.slot.map { ChangeCopy.suggest(changeClock($0, zone: ctx.zone)) } ?? ChangeCopy.pickTime,
                onSuggest: onSuggest, onCancel: onCancel)
        }
    }

    /// The largest of 4, 3, 2 columns at which the widest label stays on one line: `ViewThatFits` takes the first grid
    /// whose one-line ideal width fits the column (each label is `fixedSize` and padded), so the choice follows the
    /// measured text at the current type size. A cell is at least 44 and grows with its text; the gap stays 6.
    private var slotGrid: some View {
        ViewThatFits(in: .horizontal) {
            slotRows(columns: 4)
            slotRows(columns: 3)
            slotRows(columns: 2)
        }
        .padding(.bottom, 16)
    }

    private func slotRows(columns: Int) -> some View {
        let rows = stride(from: 0, to: ctx.slots.count, by: columns).map { Array(ctx.slots[$0..<min($0 + columns, ctx.slots.count)]) }
        return VStack(spacing: 6) {
            ForEach(rows.indices, id: \.self) { r in
                HStack(spacing: 6) {
                    ForEach(rows[r], id: \.self) { slot in slotCell(slot) }
                    // A short last row keeps its columns: the grid is equal tracks, not a stretched row.
                    ForEach(0..<(columns - rows[r].count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity, minHeight: 0) }
                }
            }
        }
    }

    private func slotCell(_ slot: Date) -> some View {
        let on = slot == ui.slot
        let shape = RoundedRectangle(cornerRadius: 12)
        return Button { onSelect(slot) } label: {
            Text(changeClock(slot, zone: ctx.zone))
                .font(F.manrope(15, .bold)).monospacedDigit()
                .foregroundColor(on ? .white : .liqFg)
                .lineLimit(1).fixedSize(horizontal: true, vertical: false)
                .padding(.horizontal, 6)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(shape.fill(on ? Color.liqPurple : Color.liqElevated))
                .overlay { if !on { shape.strokeBorder(Color.liqBorder, lineWidth: 1) } }
        }
        .buttonStyle(PressScale())
        .fitTapArea()
        .fitAnchor(ifGiven: slot == ctx.slots.last ? "change:last-row" : nil)
        .disabled(ui.sending)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

// MARK: B · the place picker

/// B, with K (the error over the binding note), M (the list's failure) and N (a query with no result). The search
/// field is usable in every one of them; the primary reads `Pick a place` (disabled) until a row is selected, then
/// `Suggest {venue}`.
struct PlaceChangeSheet: View {
    @FocusState private var searchFocused: Bool
    let ctx: ChangeSheetContext
    let ui: ChangePickerUi
    let onQuery: (String) -> Void
    let onSelect: (PlaceOption) -> Void
    let onRetry: () -> Void
    let onSuggest: () -> Void
    let onCancel: () -> Void

    var body: some View {
        ChangeFullSheet {
            ChangeSheetHead(
                title: ChangeCopy.placeTitle,
                currentLabel: ChangeCopy.placeCurrent, currentValue: ctx.bindingPlace,
                line2: ChangeCopy.placeChoose,
                line3: ChangeCopy.agreeBy(name: ctx.name, deadline: ctx.deadline, binding: ctx.bindingPlace))
            searchField
            list
        } footer: {
            ChangeFooter(
                ctx: ctx, ui: ui,
                primary: ui.place.map { ChangeCopy.suggest($0.name) } ?? ChangeCopy.pickPlace,
                onSuggest: onSuggest, onCancel: onCancel)
        }
    }

    private var searchField: some View {
        let shape = RoundedRectangle(cornerRadius: 14)
        return HStack(spacing: 10) {
            BrandIconView(icon: .search, size: 18, stroke: 2.2, tint: .liqSubtle).accessibilityHidden(true)
            // The placeholder is a Text of its own so it wraps instead of being cut at a large type size.
            ZStack(alignment: .leading) {
                if ui.query.isEmpty {
                    Text(ChangeCopy.searchPlaceholder).font(F.manrope(15, .medium)).foregroundColor(.liqSubtle)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .allowsHitTesting(false).accessibilityHidden(true)
                }
                TextField("", text: Binding(get: { ui.query }, set: { onQuery($0) }))
                .font(F.manrope(15, .medium))
                .foregroundColor(.liqFg)
                .submitLabel(.search)
                .focused($searchFocused)
                .autocorrectionDisabled()
                .accessibilityLabel(ChangeCopy.searchPlaceholder)
                .accessibilityIdentifier("change-search")
            }
        }
        .padding(.vertical, 10)
        .frame(minHeight: 48)
        // Pad 0 14 plus the 1.5 border.
        .padding(.horizontal, 15.5)
        .background(shape.fill(Color.liqElevated))
        .overlay(shape.strokeBorder(Color.liqBorder, lineWidth: 1.5))
        // The whole 48-tall field answers a tap and focuses the input, not only the text line inside it.
        .contentShape(shape)
        .onTapGesture { searchFocused = true }
        .padding(.bottom, 12)
    }

    @ViewBuilder private var list: some View {
        switch ui.list {
        case .loading:
            // Empty, and spoken: no new visible string or skeleton (R17).
            Color.clear.frame(maxWidth: .infinity, minHeight: 44)
                .accessibilityElement()
                .accessibilityLabel(ChangeCopy.loadingPlaces)
        case .failed:
            failed
        case .loaded(let places, let google, let forQuery):
            if places.isEmpty {
                if !forQuery.isEmpty { noPlaces(forQuery) }
            } else {
                placeList(places, google: google, forQuery: forQuery)
            }
        }
    }

    private func placeList(_ places: [PlaceOption], google: Bool, forQuery: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // Uppercase is the kit's `text-transform`: drawn so, read (and tested) as the ticket's own string.
            Text(forQuery.isEmpty ? ChangeCopy.nearby : ChangeCopy.results)
                .font(F.manrope(11.5, .heavy)).tracking(0.08).textCase(.uppercase)
                .foregroundColor(.liqSubtle)
                .padding(EdgeInsets(top: 0, leading: 2, bottom: 8, trailing: 2))
            VStack(spacing: 8) {
                ForEach(places, id: \.placeId) { place in
                    PlaceRow(place: place, on: place.placeId == ui.place?.placeId, enabled: !ui.sending) { onSelect(place) }
                        .fitAnchor(ifGiven: place.placeId == places.last?.placeId ? "change:last-row" : nil)
                }
            }
            if google {
                // Google's attribution for Places results shown without a Google map (R2): the text form, Manrope
                // 400 12 in `#5E5E5E`, one line, not localised, under the list. That grey is Google's mandated
                // attribution colour -- a third-party brand value the house rule allows as a literal; see Google
                // Maps Platform's Places policy, "Attribution" for results displayed without a map.
                Text(ChangeCopy.googleMaps)
                    .font(F.manrope(12, .regular))
                    .foregroundColor(Color(hex: 0x5E5E5E))
                    .lineLimit(1)
                    .padding(.top, 12)
            }
            Color.clear.frame(height: 16)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// M: pad 28 12, gap 12, the message (at most 290) and a `Try again` pill; the search field above stays usable.
    private var failed: some View {
        VStack(spacing: 12) {
            PrettyText(ChangeCopy.placesFailed, font: chatFont(PS.manropeMedium, 15, .medium), lineHeight: 15 * 1.45,
                       color: .liqMuted, centered: true)
                .frame(maxWidth: 290)
            Button(action: onRetry) {
                Text(ChangeCopy.tryAgain).font(F.manrope(14.5, .bold)).foregroundColor(.liqFg)
                    .padding(.horizontal, 22)
                    .frame(minHeight: 44)
                    .background(Capsule().fill(Color.liqElevated))
                    .overlay(Capsule().strokeBorder(Color.liqBorder, lineWidth: 1))
            }
            .buttonStyle(PressScale())
            .fitTapArea()
            .fitAnchor("change:try-again")
        }
        .frame(maxWidth: .infinity)
        .padding(EdgeInsets(top: 28, leading: 12, bottom: 28, trailing: 12))
        .accessibilityElement(children: .contain)
    }

    /// N: pad 28 12, gap 4, no red and no retry: a query with no result is an answer, not a failure.
    private func noPlaces(_ query: String) -> some View {
        VStack(spacing: 4) {
            PrettyText(ChangeCopy.noPlaces(query), font: chatFont(PS.manropeBold, 15, .bold), lineHeight: 15 * 1.4,
                       color: .liqFg, centered: true)
            Text(ChangeCopy.tryAnother).font(F.manrope(13.5, .regular)).foregroundColor(.liqMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(EdgeInsets(top: 28, leading: 12, bottom: 28, trailing: 12))
    }
}

/// One row: pad 11 12, radius 16, white with the soft border -- selected, violet 7% and a 1.5 violet border; the
/// icon tile 40 radius 12, the name, the TYPE tag, `{address} · {distance}` 2 below, and the 22 radio.
private struct PlaceRow: View {
    let place: PlaceOption
    let on: Bool
    let enabled: Bool
    let action: () -> Void

    private var nameText: some View {
        Text(place.name).font(F.manrope(15, .bold)).foregroundColor(.liqFg).fixedSize(horizontal: false, vertical: true)
    }

    private var tagText: some View {
        Text(placeTypeTag(place.type)).font(F.manrope(10.5, .heavy)).tracking(0.05).foregroundColor(.liqPurple)
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16)
        // The border counts in the box: the selected 1.5 is wider than the resting 1, as in CSS.
        let bw: CGFloat = on ? 1.5 : 1
        Button(action: action) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 12).fill(Color.liqRaised)
                    .frame(width: 40, height: 40)
                    .overlay(BrandIconView(icon: .mapPin, size: 18, stroke: 2.2, tint: .liqPurple))
                VStack(alignment: .leading, spacing: 2) {
                    // Name and TYPE on one line while both fit; the tag under the name when one more word would break it.
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 7) { nameText; tagText }
                        VStack(alignment: .leading, spacing: 3.5) { nameText; tagText }
                    }
                    Text("\(place.address) · \(formatDistance(place.distanceMeters))")
                        .font(F.manrope(12.5, .regular)).foregroundColor(.liqMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ZStack {
                    Circle().fill(on ? Color.liqPurple : Color.liqElevated)
                    if !on { Circle().strokeBorder(Color.liqBorder, lineWidth: 1.5) }
                    if on { BrandIconView(icon: .check, size: 13, stroke: 3, tint: .white) }
                }
                .frame(width: 22, height: 22)
            }
            .padding(EdgeInsets(top: 11 + bw, leading: 12 + bw, bottom: 11 + bw, trailing: 12 + bw))
            .frame(maxWidth: .infinity)
            .background(shape.fill(on ? Color.liqPurple.opacity(0.07) : Color.liqElevated))
            .overlay(shape.strokeBorder(on ? Color.liqPurple : Color.liqBorderSoft, lineWidth: bw))
        }
        .buttonStyle(PressScale())
        .disabled(!enabled)
        .accessibilityAddTraits(on ? .isSelected : [])
        .accessibilityIdentifier("change-place-\(place.placeId)")
    }
}

// MARK: the screen the route shows

/// The picker for `ChangePickerUi.kind`: what the route draws.
struct ChangePickerScreen: View {
    let ctx: ChangeSheetContext
    let ui: ChangePickerUi
    let onSelectSlot: (Date) -> Void
    let onSelectPlace: (PlaceOption) -> Void
    let onQuery: (String) -> Void
    let onRetry: () -> Void
    let onSuggest: () -> Void
    let onCancel: () -> Void

    var body: some View {
        switch ui.kind {
        case .time:
            TimeChangeSheet(ctx: ctx, ui: ui, onSelect: onSelectSlot, onSuggest: onSuggest, onCancel: onCancel)
        case .place:
            PlaceChangeSheet(ctx: ctx, ui: ui, onQuery: onQuery, onSelect: onSelectPlace, onRetry: onRetry,
                             onSuggest: onSuggest, onCancel: onCancel)
        }
    }
}

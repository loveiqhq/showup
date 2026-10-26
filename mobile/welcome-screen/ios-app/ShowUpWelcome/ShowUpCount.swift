//
//  ShowUpCount.swift
//  ShowUp · the Show-up display: a count of dates attended, never a percentage (SHOWUP-184)
//
//  The Swift twin of `designsystem/ShowUpCount.kt`. NEW SHARED COMPONENT, "next to Chip" as the ticket
//  puts it: `N dates attended` counts dates both people confirmed; 0 reads `New in the App` whether the
//  person is new or has had no confirmed date yet. A missed date is never counted, shown or recorded.
//
//  The reference's `ShowUpCount`: an inline pill, gap 6, Manrope 700 at line-height 1, the number at
//  800. `New` is 10% violet on violet with `sparkles`; a count is the kit's `good` tone (10% green on
//  #0A7A47) with `shield-check`. The icon is the type size + 1, and grows with the type.
//
//  `variant="dark"` (the Premium grid card, SHOWUP-185): over a photo, a count on `showUpTone(100)`'s
//  dark pill, `New in the App` on white at .92, both over a backdrop blur of 8 -- `glass`, what that
//  blur sees, drawn by the caller, who knows the photo.
//

import SwiftUI

enum ShowUpCountSize: Sendable {
    /// `4px 9px` at 11 -- the meet card's.
    case xs, sm, md

    var fontSize: CGFloat { switch self { case .xs: return 11; case .sm: return 12; case .md: return 12.5 } }
    var horizontal: CGFloat { switch self { case .xs: return 9; case .sm, .md: return 12 } }
    var vertical: CGFloat { switch self { case .xs: return 4; case .sm, .md: return 7 } }
}

/// The zero state's words: "whether the user is new or has had no confirmed date yet".
let showUpNew = "New in the App"

/// `N dates attended` · `1 date attended` · `New in the App`. Never a percentage.
func showUpCountLabel(_ count: Int) -> String {
    if count <= 0 { return showUpNew }
    return count == 1 ? "1 date attended" : "\(count) dates attended"
}

struct ShowUpCount<Glass: View>: View {
    let count: Int
    var size: ShowUpCountSize = .md
    /// `variant="dark"`, over a photo.
    var dark = false
    /// The width the pill has to fit, when the caller knows it -- a narrow card or column. One line
    /// wherever it fits; otherwise it wraps between words, and where one word alone is wider than the
    /// room beside the icon (the largest type on a narrow phone) the type shrinks just enough, never
    /// breaking inside a word and never running past the card (the 185/186 audit, 26 Oct 2026). Nil
    /// keeps the pill's own width, as the meet card's row measures it.
    var fitWidth: CGFloat? = nil
    /// Behind the fill, clipped to the pill: the photo under it, blurred.
    @ViewBuilder var glass: () -> Glass

    /// The type size the user chose, as a factor of the default -- the icon follows the type.
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    var body: some View {
        let isNew = count <= 0
        let ink: Color = isNew ? .liqPurple : (dark ? .liqShowUpDarkInk : .liqSuccessFg)
        let fill: Color = dark
            ? (isNew ? Color.liqElevated.opacity(0.92) : .liqShowUpDarkFill)
            : (isNew ? Color.liqPurple.opacity(0.10) : Color.liqSuccess.opacity(0.10))
        let fs = size.fontSize
        let icon = (fs + 1) * typeScale
        let shrink = wordScale(room: fitWidth.map { $0 - 2 * size.horizontal - icon - 6 })
        HStack(spacing: 6) {
            BrandIconView(icon: isNew ? .sparkles : .shieldCheck, size: icon, stroke: 2.2, tint: ink)
            ViewThatFits(in: .horizontal) {
                label(isNew: isNew, scale: shrink)
                    .foregroundColor(ink)
                    .lineLimit(1)
                    .fixedSize()
                    // `line-height: 1` as CSS lays it out: the pill is the icon and its padding, not
                    // Manrope's natural line.
                    .cssLineBox(fs * shrink * typeScale)
                // Wrapped: Manrope's own line, since SwiftUI sets no line closer than the font's.
                label(isNew: isNew, scale: shrink)
                    .foregroundColor(ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, size.horizontal)
        .padding(.vertical, size.vertical)
        // The pill hugs what it holds; the cap below only limits what it is offered.
        .background(ZStack { glass(); Capsule().fill(fill) }.clipShape(Capsule()))
        .frame(maxWidth: fitWidth, alignment: .leading)
        .fixedSize(horizontal: fitWidth == nil, vertical: false)
        // One element for VoiceOver: the sentence, not an icon and two runs of text.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(showUpCountLabel(count)))
    }

    /// How far the type shrinks so the widest word fits `room`: 1 when it does, never under .6.
    private func wordScale(room: CGFloat?) -> CGFloat {
        guard let room, room > 0 else { return 1 }
        let font = TypeMetrics.uiFont(PS.manropeXBold, size.fontSize * typeScale,
                                      fallback: .systemFont(ofSize: size.fontSize * typeScale, weight: .heavy))
        let widest = showUpCountLabel(count).split(separator: " ")
            .map { (String($0) as NSString).size(withAttributes: [.font: font]).width }
            .max() ?? 0
        return widest <= room ? 1 : max(0.6, room / widest)
    }

    private func label(isNew: Bool, scale: CGFloat) -> Text {
        let fs = size.fontSize * scale
        if isNew { return Text(showUpNew).font(F.manrope(fs, .bold)) }
        let rest = showUpCountLabel(count).dropFirst(String(count).count)
        return Text(String(count)).font(F.manrope(fs, .heavy)) + Text(String(rest)).font(F.manrope(fs, .bold))
    }
}

extension ShowUpCount where Glass == EmptyView {
    /// The count with nothing behind its fill: every use but the photo card's.
    init(count: Int, size: ShowUpCountSize = .md, dark: Bool = false, fitWidth: CGFloat? = nil) {
        self.init(count: count, size: size, dark: dark, fitWidth: fitWidth, glass: { EmptyView() })
    }
}

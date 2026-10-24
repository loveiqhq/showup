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

struct ShowUpCount: View {
    let count: Int
    var size: ShowUpCountSize = .md

    /// The type size the user chose, as a factor of the default -- the icon follows the type.
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    var body: some View {
        let isNew = count <= 0
        let ink: Color = isNew ? .liqPurple : .liqSuccessFg
        let fs = size.fontSize
        HStack(spacing: 6) {
            BrandIconView(icon: isNew ? .sparkles : .shieldCheck, size: (fs + 1) * typeScale, stroke: 2.2, tint: ink)
            label(isNew: isNew)
                .foregroundColor(ink)
                .lineLimit(1)
                .fixedSize()
                // `line-height: 1` as CSS lays it out: the pill is the icon and its padding, not
                // Manrope's natural line.
                .cssLineBox(fs * typeScale)
        }
        .padding(.horizontal, size.horizontal)
        .padding(.vertical, size.vertical)
        .background(Capsule().fill(isNew ? Color.liqPurple.opacity(0.10) : Color.liqSuccess.opacity(0.10)))
        // One element for VoiceOver: the sentence, not an icon and two runs of text.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(showUpCountLabel(count)))
    }

    private func label(isNew: Bool) -> Text {
        let fs = size.fontSize
        if isNew { return Text(showUpNew).font(F.manrope(fs, .bold)) }
        let rest = showUpCountLabel(count).dropFirst(String(count).count)
        return Text(String(count)).font(F.manrope(fs, .heavy)) + Text(String(rest)).font(F.manrope(fs, .bold))
    }
}

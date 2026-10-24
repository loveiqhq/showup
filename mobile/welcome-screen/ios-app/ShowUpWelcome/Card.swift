//
//  Card.swift
//  ShowUp · the kit's `Card` (`components/shared.jsx`), the surface every Search section sits on
//
//  The Swift twin of `designsystem/Card.kt`. `background: var(--liq-bg-elevated); border-radius: 20;
//  border: 1px solid var(--liq-border-soft); box-shadow: var(--liq-shadow-md)` -- and the reference
//  overrides fill, border and padding per section (the shared vibes row's 1.5 orange border, the
//  highlight's blush gradient), which is what the parameters are. Built with Date search 01.
//
//  A card FILLS ITS CONTAINER'S WIDTH, as the kit's block `div` does: wrapping its content instead
//  drew the vibe cards as narrow boxes hugging their chips (the 184 evidence review, 24 Oct 2026).
//

import SwiftUI

/// The kit card's own corner, 20. Not `Radius.card` (16), the method-list container's.
private let cardRadius: CGFloat = 20

struct Card<Content: View>: View {
    /// `--liq-bg-elevated` by default; a section may pass a gradient (the highlight's blush).
    var fill: AnyShapeStyle = AnyShapeStyle(Color.liqElevated)
    /// 1px `--liq-border-soft` by default; a width of 0 draws none.
    var borderColor: Color = .liqBorderSoft
    var borderWidth: CGFloat = 1
    /// `--liq-shadow-md` by default; false draws none.
    var lifted: Bool = true
    /// The reference's `padding` for the section, 16 for most.
    var padding: EdgeInsets = EdgeInsets(top: 16, leading: 16, bottom: 16, trailing: 16)
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(surface)
            .overlay(
                RoundedRectangle(cornerRadius: cardRadius)
                    .strokeBorder(borderColor, lineWidth: borderWidth)
                    .opacity(borderWidth > 0 ? 1 : 0)
            )
    }

    /// `--liq-shadow-md` is two layers, `0 4px 12px` and `0 1px 3px` of `rgba(46,1,71)` at .06 and
    /// .04 -- each on its own copy of the shape, so neither is a shadow of the other.
    private var surface: some View {
        let shape = RoundedRectangle(cornerRadius: cardRadius)
        return ZStack {
            shape.fill(fill).shadow(color: lifted ? Color.liqShadowInk.opacity(0.06) : .clear, radius: 6, y: 4)
            shape.fill(fill).shadow(color: lifted ? Color.liqShadowInk.opacity(0.04) : .clear, radius: 1.5, y: 1)
        }
    }
}

//
//  WrapLayout.swift
//  ShowUp · children in rows that wrap — CSS `display: flex; flex-wrap: wrap; gap`
//
//  Interests' chips and Life's pills are laid out this way in the design, and SwiftUI has no
//  built-in equivalent of Compose's `FlowRow`. One layout, in its own file because two screens'
//  components use it (a shared primitive never lives in a screen file).
//
//  EACH CHILD AT ITS IDEAL WIDTH, never squeezed: a chip keeps its width and the row around it
//  wraps. Only a child wider than the whole row — a long label at the largest Dynamic Type on the
//  narrowest phone — is offered the row's width, so its label wraps inside it rather than clipping.
//

import SwiftUI

struct WrapLayout: Layout {
    /// `gap` between children on a row.
    var spacing: CGFloat = Spacing.md
    /// `gap` between rows.
    var lineSpacing: CGFloat = Spacing.md

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        let rows = arrange(subviews, width: width)
        let height = rows.last.map { $0.y + $0.height } ?? 0
        let widest = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? widest, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for row in arrange(subviews, width: bounds.width) {
            for item in row.items {
                subviews[item.index].place(
                    at: CGPoint(x: bounds.minX + item.x, y: bounds.minY + row.y),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(width: item.size.width, height: item.size.height))
            }
        }
    }

    private struct Item {
        let index: Int
        let x: CGFloat
        let size: CGSize
    }

    private struct Row {
        var y: CGFloat
        var items: [Item] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> [Row] {
        var rows: [Row] = []
        var row = Row(y: 0)
        for (index, subview) in subviews.enumerated() {
            let ideal = subview.sizeThatFits(.unspecified)
            let size = ideal.width <= width
                ? ideal
                : subview.sizeThatFits(ProposedViewSize(width: width, height: nil))
            let x = row.items.isEmpty ? 0 : row.width + spacing
            if !row.items.isEmpty, x + size.width > width {
                rows.append(row)
                row = Row(y: row.y + row.height + lineSpacing)
                row.items.append(Item(index: index, x: 0, size: size))
                row.width = size.width
                row.height = size.height
            } else {
                row.items.append(Item(index: index, x: x, size: size))
                row.width = x + size.width
                row.height = max(row.height, size.height)
            }
        }
        if !row.items.isEmpty { rows.append(row) }
        return rows
    }
}

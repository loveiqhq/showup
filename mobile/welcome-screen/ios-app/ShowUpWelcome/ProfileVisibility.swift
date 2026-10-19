//
//  ProfileVisibility.swift
//  ShowUp · "Don't display on my profile" — the ONE profile-visibility control
//
//  The Swift twin of `designsystem/ProfileVisibility.kt`, and `ProfileVisibility` in
//  `components/shared.jsx`. It lived in the date-of-birth screen's file as `ProfileVisibilityRow`
//  while that was its only caller; the seven "Share some details" steps are the second group to use
//  it, and a shared primitive never lives in a screen file.
//
//  CHECKED MEANS NOT DISPLAYED — STILL USED FOR MATCHING. It never touches `isVisible`.
//

import SwiftUI

/// The one wording, on every step that carries the band.
let profileVisibilityLabel = "Don't display on my profile"

struct ProfileVisibility: View {
    @Binding var hidden: Bool
    var label: String = profileVisibilityLabel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 0) {
            // `borderTop: 1px solid var(--liq-border-soft)` — the hairline that makes this a band.
            Rectangle().fill(Color.liqBorderSoft).frame(height: 1)
            Button {
                hidden.toggle()
            } label: {
                HStack(spacing: 14) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(hidden ? Color.liqPurple : Color.liqElevated)
                        RoundedRectangle(cornerRadius: 6)
                            .strokeBorder(hidden ? Color.liqPurple : Color.liqBorder, lineWidth: 1.5)
                        if hidden { CheckGlyph(size: 13, color: .liqElevated, stroke: 3) }
                    }
                    .frame(width: 22, height: 22)
                    // `transition: background 180ms, border-color 180ms`.
                    .animation(reduceMotion ? nil : Motion.cssEase(Motion.fast), value: hidden)

                    HStack(spacing: Spacing.sm) {
                        BrandIconView(icon: .eyeOff, size: 14, stroke: 2.1,
                                      tint: hidden ? .liqPurple : .liqSubtle)
                        Text(label)
                            .font(F.manrope(14, .bold))
                            .foregroundColor(.liqFg)
                            // `whiteSpace: nowrap` in the reference, and one line at the default
                            // size on every phone. NOT FORCED: at large Dynamic Type a forced line
                            // is cut off, and a privacy label nobody can read is not a label.
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                // CAPPED TO THE BOX AND ITS LABEL, NOT THE FULL ROW: an edge-to-edge target above a
                // bottom-right Continue invites a thumb reaching for one to flip the other.
                // `minHeight: 56; padding: 10px 0` in a border-box reference: 56 IN ALL, the padding
                // inside it. The other order made the band 76.
                .padding(.vertical, Spacing.lg)
                .frame(minHeight: ComponentSizes.controlHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(label)
            // A toggle's own words: "checked" / "not checked", as the reference's `role="checkbox"`.
            .accessibilityAddTraits(.isToggle)
            .accessibilityValue(hidden ? "Checked" : "Not checked")
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

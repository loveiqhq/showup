//  CountryPicker.swift
//  ShowUp · the flag mark and the country list sheet (SHOWUP-143)
//
//  The ticket puts the country list itself out of scope, so this is the smallest thing that lets a
//  user actually change their dial code: a sheet, one row per country, the current one marked. No
//  search — at this length the list scrolls fine, and a search field is a second input to design
//  and test for no benefit yet.

import SwiftUI

/// The flag for a country, from the bundled artwork.
///
/// Drawn flags were replaced by real artwork once the list went from 35 countries to every country.
/// Hand-drawing did not scale and was not honest about it: nine were drawn by hand and two came out
/// wrong — Canada as a spiky asterisk, Portugal as a logo. At 250 that rate means dozens wrong, and
/// a wrong national flag is not a cosmetic bug.
///
/// These are the flag-icons set (MIT, see Flags/LICENSE-flag-icons.txt), rasterised to 96x72 —
/// enough for a 22pt mark at three times density. 257 files, 479 KB in total.
///
/// A country with no artwork falls back to its ISO code rather than an empty box, so a gap looks
/// deliberate instead of broken.
struct FlagView: View {
    let country: Country
    var width: CGFloat = 22

    private var height: CGFloat { width * 14 / 22 }

    var body: some View {
        Group {
            if let image = FlagView.image(country.iso) {
                Image(uiImage: image)
                    .resizable()
                    // Decorative: the country's name sits next to it in the list and the dial code
                    // is on the pill, so a screen reader gains nothing from "flag of Germany".
                    .accessibilityHidden(true)
                    .scaledToFill()
            } else {
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

    /// Decoded once per country and kept. The picker scrolls through hundreds of rows, and decoding
    /// a bitmap on every frame of a fling is exactly how a list starts to stutter.
    ///
    /// @MainActor because Swift 6 rejects nonisolated global mutable state outright, and a shared
    /// cache is exactly that. Safe to isolate rather than rework: the only thing that ever touches
    /// it is FlagView's own body, and a SwiftUI View is already on the main actor. So this is an
    /// annotation that tells the compiler where the code already runs -- no behaviour changes and
    /// no call site moves.
    @MainActor private static var cache: [String: UIImage] = [:]

    @MainActor static func image(_ iso: String) -> UIImage? {
        let key = iso.uppercased()
        if let hit = cache[key] { return hit }
        guard let url = Bundle.main.url(forResource: key, withExtension: "png", subdirectory: "Flags"),
              let data = try? Data(contentsOf: url),
              let image = UIImage(data: data)
        else { return nil }
        cache[key] = image
        return image
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
                .padding(.top, Spacing.lg)

            Text("Choose your country")
                .font(F.manrope(17, .bold))
                .foregroundColor(.liqFg)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.screenGutter)
                .padding(.top, Spacing.xxl)
                .padding(.bottom, Spacing.xl)

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
                                .padding(.horizontal, Spacing.screenGutter)
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

            PrimaryButton("Close", variant: .ghost, action: onDismiss)
                .padding(.horizontal, Spacing.screenGutter)
                .padding(.vertical, Spacing.xl)
        }
        .background(Color.liqElevated)
    }
}

import CoreGraphics

/// Icon and badge dimensions that recur across screens. Mirrors `IconSizes.kt`.
///
/// Only two, because most fixed sizes here are illustration geometry rather than icons: 84, 104 and
/// 120 are Connect-screen art, and 120's uses are all references to the same spinner canvas on one
/// screen. Tokenising those would move a drawing's coordinates into the design system and make them
/// look reusable.
///
/// `badge` is 56, the same number as `ComponentSizes.controlHeight`, and they are separate tokens on
/// purpose: one is how tall a control is, the other how wide a circular badge is. They agree today
/// by coincidence, not by rule.
enum IconSizes {
    /// Provider marks inside method buttons, and the small status glyphs.
    static let sm: CGFloat = 20

    /// The round status/eyebrow badge on the tutorial and Connect screens.
    static let badge: CGFloat = 56
}

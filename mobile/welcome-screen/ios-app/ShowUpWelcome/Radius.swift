import CoreGraphics

/// Corner radii, all four with a specific role. Mirrors `Radius.kt`.
///
/// `pill` is 28 and that is not a coincidence: it is exactly half of
/// ``ComponentSizes/controlHeight``, which is what makes a 56-tall button a pill rather than a
/// rounded rectangle. If the control height changes, this changes with it.
///
/// Not here: 2, the drawn flag rectangles in CountryPicker, which is decorative geometry rather
/// than a component corner; and 18, which appears once in TutorialShell on iOS only — an
/// unexplained divergence from Android, recorded rather than tokenised.
enum Radius {
    /// Text inputs, the six code slots, and the provider method buttons.
    static let control: CGFloat = 14

    /// The sunset CTA. Half of `ComponentSizes.controlHeight`, by definition.
    static let pill: CGFloat = 28

    /// The inline error box glued to the code slots.
    static let errorBox: CGFloat = 12

    /// The method-list container.
    static let card: CGFloat = 16
}

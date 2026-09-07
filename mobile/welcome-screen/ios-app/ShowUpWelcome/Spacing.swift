import CoreGraphics

/// The spacing values this design actually uses, named.
///
/// A deliberate mirror of `Spacing.kt`. NOT a canonical 4/8/16/24/32 scale: the audit on
/// 7 September 2026 found nine spacing values in real, repeated use, and they do not sit on any
/// regular step. Imposing a tidy scale would mean changing spacing, which this cleanup is not
/// allowed to do; keeping one alongside two dozen exceptions would be worse, because it would read
/// as authoritative while lying.
///
/// So these describe the design rather than prescribe at it. The names are ordinal because the
/// values genuinely serve unrelated purposes — `md` is the gap between rule rows on tutorial card 01
/// and the gap between a provider mark and its label. `screenGutter` is the one with a single
/// meaning, so it is the one with a semantic name.
///
/// Values left out on purpose: 1, 2, 3, 5, 7, 9, 11, 14, 18, 20, 22, 28, 32 — mostly dot offsets
/// and ring geometry. 14 and 18 are frequent enough to look like tokens and were left out anyway:
/// no single meaning could be found for either, and a token whose name cannot say what it is for is
/// a number with extra steps.
///
/// This file is FLAT in ShowUpWelcome/ rather than in a Tokens/ subfolder, because
/// `gen_pbxproj.py` discovers sources with `os.listdir` and not `os.walk` — a file in a subfolder
/// would compile locally and be silently absent from the target, which is the exact failure that
/// generator exists to prevent.
enum Spacing {
    /// Content inset from both screen edges.
    static let screenGutter: CGFloat = 24

    static let xs: CGFloat = 4
    static let sm: CGFloat = 6
    static let md: CGFloat = 8
    static let lg: CGFloat = 10
    static let xl: CGFloat = 12
    static let xxl: CGFloat = 16
}

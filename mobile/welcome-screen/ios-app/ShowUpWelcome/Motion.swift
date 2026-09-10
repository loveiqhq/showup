import Foundation

/// Animation durations, in seconds. Mirrors `Motion.kt`, which holds the same values in
/// milliseconds.
///
/// These already agreed across platforms before this file existed — 0.18s here and 180ms on
/// Android, 0.32/320, 0.48/480 — which is worth locking down rather than leaving to coincidence.
///
/// `shake` is specified: SHOWUP-143 requires the mismatch row to shake ONCE for 480ms and then hold
/// still, and forbids a looping animation.
///
/// Not here: the 0.5/1.2/1.6s sleeps in ConnectFlowHost, which are the fake provider round trip and
/// go away with the real SDKs.
enum Motion {
    /// Press feedback and fades.
    static let fast: TimeInterval = 0.18

    /// `su-confirm-in` — the age card's entrance (SHOWUP-154). Once, on entering the state.
    ///
    /// Longer than `fast` because it is an arrival rather than a state change: the card is asking
    /// a question, and a 180ms appearance reads as a flicker rather than something to answer.
    static let confirm: TimeInterval = 0.24

    /// Screen-to-screen transition.
    static let screen: TimeInterval = 0.32

    /// The one-shot mismatch shake. SHOWUP-143 specifies 480ms exactly.
    static let shake: TimeInterval = 0.48

    /// The in-flight pulse on a provider button.
    static let pulseSlow: TimeInterval = 0.9

    /// The linking animation on the Connect screen.
    static let pulseLong: TimeInterval = 1.4
}

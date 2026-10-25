import SwiftUI

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

    /// The step progress bar's segment fill — `StepProgress` in shared.jsx, `transition: background
    /// 280ms`, on `cssEase`. Its own number, not `screen`'s: the reference times the bar separately
    /// from the transition between the screens that draw it.
    static let progress: TimeInterval = 0.28

    /// A disclosure chevron turning over — `Show more` / `Show less` on Interests (SHOWUP-174),
    /// `transform 220ms` on the brand curve. Android's `Motion.DISCLOSURE`.
    static let disclosure: TimeInterval = 0.22

    /// Screen-to-screen transition.
    static let screen: TimeInterval = 0.32

    /// The one-shot mismatch shake. SHOWUP-143 specifies 480ms exactly.
    static let shake: TimeInterval = 0.48

    /// `sheet-rise` — a bottom sheet arriving (SHOWUP-158). 28 up and 0.85 -> 1 opacity.
    ///
    /// Longer than `screen` on purpose: a sheet is a new surface taking the bottom of the screen,
    /// and the extra 40ms is what makes it read as rising rather than appearing.
    static let sheet: TimeInterval = 0.36

    /// The in-flight pulse on a provider button.
    static let pulseSlow: TimeInterval = 0.9

    /// The linking animation on the Connect screen.
    static let pulseLong: TimeInterval = 1.4

    /// How long a refusal toast stays up. SHOWUP-156 and SHOWUP-158 both specify 2600ms exactly.
    ///
    /// Not a fade and not a transition: it is a READING duration, which is why it is an order of
    /// magnitude longer than everything above it. The fade in and out is `fast` either side.
    static let toast: TimeInterval = 2.6

    /// How long the "please log in again" notice stays up when nobody taps it.
    ///
    /// A reading duration like `toast`, and longer on purpose: a toast repeats something the user
    /// just did, while this tells them something they did not do and did not expect — the app has
    /// signed them out. Leaving Startup ends it sooner.
    static let notice: TimeInterval = 6

    /// `su-toast-in` and `su-reveal` — a toast, or a panel that opens inline: 200 ms, up 6, on the
    /// brand curve (the 48hrs Check-In, SHOWUP-179). Android's `Motion.REVEAL`.
    static let reveal: TimeInterval = 0.2

    /// `su-checkin-pop` — the No hours today? card arriving: 260 ms, up 10, from .97 (SHOWUP-179).
    static let pop: TimeInterval = 0.26

    /// One turn of the commit spinner, linear (SHOWUP-183). Android's `Motion.SPIN`.
    static let spin: TimeInterval = 0.8

    /// The card fly-out (SHOWUP-185 20, reused by 186), named as the ticket names them: the hold that
    /// shows the filled heart or the label (`GRID_LEAVE_HOLD_MS`), the flight to the bottom centre
    /// (`GRID_LEAVE_MS`), and the rest of the list closing the gap (`GRID_REFLOW_MS`). Android's
    /// `Motion.GRID_LEAVE_HOLD`, `GRID_LEAVE` and `GRID_REFLOW`.
    static let gridLeaveHold: TimeInterval = 0.15
    static let gridLeave: TimeInterval = 0.8
    static let gridReflow: TimeInterval = 0.25

    /// The fly-out with reduced motion: "a 200 ms fade, then the reflow". `reveal`'s length, its own
    /// meaning.
    static let gridLeaveReduced: TimeInterval = 0.2

    /// How long a declined like can be undone (`LIKES_UNDO_MS`, SHOWUP-185 G / 186 D). A decision
    /// window, not a reading time: the decline is sent when it ends.
    static let likesUndo: TimeInterval = 6

    /// The brand curve, `cubic-bezier(.22,1,.36,1)` — Android's `ShowUpEasing`. Written inline at
    /// several older call sites; the Check-In reads it from here.
    static func brand(_ duration: TimeInterval) -> Animation {
        .timingCurve(0.22, 1, 0.36, 1, duration: duration)
    }

    /// CSS's default `ease`, for the transitions the reference writes with a duration and NO curve
    /// — the answer rows' and the visibility band's `transition: background 180ms`. A browser runs
    /// that on `ease`; `.easeOut` is a different curve. Android names the same one `CssEase`.
    static func cssEase(_ duration: TimeInterval) -> Animation {
        .timingCurve(0.25, 0.1, 0.25, 1, duration: duration)
    }
}

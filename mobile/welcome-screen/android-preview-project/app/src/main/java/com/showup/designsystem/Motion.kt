package com.showup.designsystem

/**
 * Animation durations, in milliseconds.
 *
 * These already agreed across platforms before this file existed -- 180ms here and 0.18s on iOS,
 * 320/0.32, 480/0.48 and so on -- which is worth locking down rather than leaving to coincidence.
 * Motion.swift holds the same five values in seconds.
 *
 * [shake] is specified: SHOWUP-143 requires the mismatch row to shake ONCE for 480ms and then hold
 * still, and forbids a looping animation.
 *
 * Not here: the 500/1200/1600ms delays in ConnectFlowHost, which are the fake provider round trip
 * and go away with the real SDKs. 220ms was one fade-in until SHOWUP-174 gave it a meaning of its
 * own -- see [DISCLOSURE].
 */
object Motion {
    /** Press feedback and fades. */
    const val FAST = 180

    /**
     * `su-confirm-in` — the age card's entrance (SHOWUP-154). Once, on entering the state.
     *
     * Longer than [FAST] because it is an arrival rather than a state change: the card is asking a
     * question, and a 180ms appearance reads as a flicker rather than something to answer.
     */
    const val CONFIRM = 240

    /**
     * The step progress bar's segment fill -- `StepProgress` in shared.jsx, `transition: background
     * 280ms`, on [CssEase]. Its own number, not [SCREEN]'s: the bar is drawn by every step screen
     * and the reference times it separately from the transition between them.
     */
    const val PROGRESS = 280

    /**
     * A disclosure chevron turning over -- `Show more` / `Show less` on Interests (SHOWUP-174),
     * `transform 220ms` on [ShowUpEasing]. A touch longer than [FAST] because the chevron travels
     * 180 degrees where a fade travels nowhere.
     */
    const val DISCLOSURE = 220

    /** Screen-to-screen transition. */
    const val SCREEN = 320

    /** The one-shot mismatch shake. SHOWUP-143 specifies 480ms exactly. */
    const val SHAKE = 480

    /**
     * `sheet-rise` — a bottom sheet arriving (SHOWUP-158). 28 up and 0.85 -> 1 opacity.
     *
     * Longer than [SCREEN] on purpose: a sheet is a new surface taking the bottom of the screen,
     * and the extra 40ms is what makes it read as rising rather than appearing.
     */
    const val SHEET = 360

    /** The in-flight pulse on a provider button. */
    const val PULSE_SLOW = 900

    /** The linking animation on the Connect screen. */
    const val PULSE_LONG = 1400

    /**
     * How long a refusal toast stays up. SHOWUP-156 and SHOWUP-158 both specify 2600ms exactly.
     *
     * Not a fade and not a transition: it is a READING duration, which is why it is an order of
     * magnitude longer than everything above it. The fade in and out is [FAST] either side.
     */
    const val TOAST = 2600

    /**
     * How long the "please log in again" notice stays up when nobody taps it.
     *
     * A reading duration like [TOAST], and longer on purpose: a toast repeats something the user
     * just did, while this tells them something they did not do and did not expect -- the app has
     * signed them out. Leaving Startup ends it sooner.
     */
    const val NOTICE = 6000

    /**
     * A small thing arriving: the Check-In toast and How it works' panel (`su-toast-in`), the No
     * hours today? scrim, and How it works' chevron turning over (`transform 200ms`). SHOWUP-179.
     */
    const val REVEAL = 200

    /** The No hours today? card popping in (`su-checkin-pop 260ms`). SHOWUP-179. */
    const val POP = 260

    /** One turn of the commit spinner (`su-checkin-spin 800ms linear infinite`). SHOWUP-183. */
    const val SPIN = 800

    /**
     * The card fly-out (SHOWUP-185 20, reused by 186), named as the ticket names them: the hold
     * that shows the filled heart or the label (`GRID_LEAVE_HOLD_MS`), the flight to the bottom
     * centre (`GRID_LEAVE_MS`), and the rest of the list closing the gap (`GRID_REFLOW_MS`).
     */
    const val GRID_LEAVE_HOLD = 150
    const val GRID_LEAVE = 800
    const val GRID_REFLOW = 250

    /** The fly-out with reduced motion: "a 200 ms fade, then the reflow". [REVEAL]'s length, its own meaning. */
    const val GRID_LEAVE_REDUCED = 200

    /**
     * How long a declined like can be undone (`LIKES_UNDO_MS`, SHOWUP-185 G / 186 D). A decision
     * window, not a reading time: the decline is sent when it ends.
     */
    const val LIKES_UNDO = 6000
}

/**
 * The curve every one of those durations runs on.
 *
 * CLAUDE.md states it as `cubic-bezier(.22,1,.36,1)` and calls it non-negotiable. It lived in
 * WelcomeShell.kt until PrimaryButton moved into the design system: a screen file cannot be the
 * home of the easing that the primitive needs, because designsystem must not depend on a screen.
 */
val ShowUpEasing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * CSS's default `ease`, for the transitions the reference writes with a duration and NO curve.
 *
 * The answer rows and the visibility band say `transition: background 180ms` and nothing more, and
 * a browser runs that on `ease` -- not on [ShowUpEasing], and not on Compose's own default, which is
 * a different curve again. Both platforms name it, so a selection fades the same way on each.
 */
val CssEase = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

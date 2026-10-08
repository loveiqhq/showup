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
 * and go away with the real SDKs, and 220ms, which is one fade-in.
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

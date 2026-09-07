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

    /** Screen-to-screen transition. */
    const val SCREEN = 320

    /** The one-shot mismatch shake. SHOWUP-143 specifies 480ms exactly. */
    const val SHAKE = 480

    /** The in-flight pulse on a provider button. */
    const val PULSE_SLOW = 900

    /** The linking animation on the Connect screen. */
    const val PULSE_LONG = 1400
}

/*
 * FlowScreen.kt
 * ShowUp · where in the app the user is
 *
 * WHAT THIS REPLACED
 *
 * An Int. `var screen by rememberSaveable { mutableIntStateOf(-4) }`, where negative meant the
 * pre-account flow and positive meant the tutorial. Three things were wrong with it and only one
 * was cosmetic:
 *
 *   * -4 was arbitrary. The comment said "negative ids" plural; exactly one was left.
 *   * Card 6 was never written as a branch. It was the `else ->` case, so ANY unexpected value
 *     rendered ShowUpEveryTimeScreen rather than failing or falling back to something sensible.
 *   * Nothing typed stopped `screen = 9` from compiling.
 *
 * WHY THE ORDER OF THESE ENTRIES IS LOAD-BEARING
 *
 * Two things read it:
 *
 *   1. `AnimatedContent` picks its direction by comparing target to initial -- forward if the
 *      destination is later. That was `targetState > initialState` on Ints and is an `ordinal`
 *      comparison now.
 *   2. [hasSystemBack] was `screen in 2..6`.
 *
 * Every reachable transition was checked against the Int scheme before the change: sign-up to
 * tutorial and to home both read forward, each tutorial card forward and back, and "start over"
 * from home back to sign-up still reads BACKWARD, which it did as 7 -> -4.
 *
 * iOS mirrors this as `FlowScreen.swift`, which additionally carries String raw values because
 * `@SceneStorage` has to write the value down. Kotlin enums are Serializable, so `rememberSaveable`
 * persists this one with nothing added -- which is also why Android already survived process death
 * and iOS did not.
 */
package com.showup.welcome

/** Where in the app the user is. One flat list -- this flow is linear and has no side routes. */
enum class FlowScreen {
    /** The whole pre-account flow. [SignUpFlow] owns its own internal step. */
    SignUp,

    TutorialWelcome,
    MeetInRealLife,
    MatchOnAvailability,
    MatchMeansMeet,
    ThirtyMinutes,
    ShowUpEveryTime,

    /** Where the flow ends, for both the tutorial and a returning member. */
    Home,
    ;

    /**
     * Whether the system back gesture walks the tour from here.
     *
     * Cards 2-6, exactly as `screen in 2..6` meant. [TutorialWelcome] is deliberately excluded so
     * back exits the app from the first card rather than doing nothing, and [Home] and [SignUp] are
     * outside the tour.
     */
    val hasSystemBack: Boolean
        get() = ordinal in MeetInRealLife.ordinal..ShowUpEveryTime.ordinal
}

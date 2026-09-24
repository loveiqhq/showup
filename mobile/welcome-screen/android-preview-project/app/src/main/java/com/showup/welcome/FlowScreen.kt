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

    /**
     * Profile creation, step 1 of "The basics".
     *
     * Placed AFTER the tutorial and before [Home] because that is the real order: the user signs
     * up, is shown how the product works, and only then is asked to build a profile. The epic says
     * the same thing -- "entered from the app tutorial, after a completed identity verification".
     */
    ProfileName,

    /** Profile creation, step 2. */
    ProfileEmail,

    /**
     * Profile creation, the code screen.
     *
     * Its own position but NOT its own progress segment: the bar holds at 2 because the user is
     * on the email step until the code is confirmed. See [com.showup.profile.BasicsStep].
     */
    ProfileVerifyEmail,

    /** Profile creation, step 3. The last screen of "The basics". */
    ProfileDob,

    /**
     * The bridge out of "The basics" (SHOWUP-155).
     *
     * A position in the flow, and deliberately NOT a step: it belongs to neither progress bar, has
     * no header and collects nothing. It sits between [ProfileDob] and the first screen of "The
     * real you", which is exactly where the user meets it.
     *
     * Excluded from [hasSystemBack] for the same reason as [ProfileName]: profile creation is
     * mandatory once entered, so back must do NOTHING rather than step anywhere. The screen
     * swallows the gesture with its own handler.
     */
    ProfileEmbrace,

    /**
     * "The real you", step 1 of 3 (SHOWUP-156).
     *
     * A DIFFERENT GROUP from "The basics", with its own header title and its own progress bar.
     * See [com.showup.profile.RealYouStep].
     */
    ProfilePhotos,

    /** "The real you", step 2 of 3 (SHOWUP-158). */
    ProfilePrompts,

    /**
     * "The real you", step 3 of 3 and the last screen of the group (SHOWUP-161).
     *
     * NOT A RESUME POINT, and that is a consequence rather than an oversight. `resumePoint` returns
     * the first GAP in the flow, and this step is optional -- skipping it is a valid ending, and
     * the account holds no fact that tells a skip apart from a step never reached. So a user who
     * force-quits here and relaunches lands on Home.
     *
     * The same reasoning that keeps [ProfileEmbrace] out of the resume table: "there is no fact on
     * the account that says whether it was seen". Closing it needs a server-side "media step
     * decided" flag, which is a product decision rather than a client one, and it is raised with
     * the ticket rather than invented here.
     */
    ProfileMedia,

    /**
     * The notification permission ask (SHOWUP-162), between media and Stay reachable.
     *
     * NOT A STEP and not a resume point. It holds nothing on the account, so like the embrace
     * bridge and the media step there is no fact that says whether it was seen -- `resumePoint`
     * cannot name it and a relaunch never lands here.
     *
     * That is also why this codebase cannot get into the stuck state the ticket guards against.
     * Its first anti-stuck rule -- "advance the saved flow position when the sheet is raised" --
     * assumes a per-screen saved position; resume here is derived from server profile facts, so a
     * kill mid-sheet relaunches to wherever those facts point, never onto a dead button. The other
     * two rules, the status guard before the push and the foreground re-read, are both built.
     */
    ProfileNotifications,

    /** Where the flow ends, for both the tutorial and a returning member. */
    Home,
    ;

    /**
     * Whether the system back gesture steps one position back from here.
     *
     * Cards 2-6, exactly as `screen in 2..6` meant, PLUS [ProfileEmail].
     *
     * [TutorialWelcome] is deliberately excluded so back exits the app from the first card rather
     * than doing nothing, and [Home] and [SignUp] are outside the tour.
     *
     * [ProfileName] is excluded for the opposite reason to everything else here: profile creation
     * is mandatory once entered, so back must do NOTHING rather than step anywhere. The screen
     * swallows the gesture with its own handler, which wins over this one -- listing it here would
     * be harmless but misleading.
     *
     * [ProfileEmail] IS included, and that is a real fix rather than a tidy-up: without it the
     * handler is disabled on step 2, the gesture falls through to the activity, and a user pressing
     * back on the email screen leaves the app instead of returning to their name. The screen draws
     * a back chevron; the gesture has to agree with it.
     */
    val hasSystemBack: Boolean
        get() = ordinal in MeetInRealLife.ordinal..ShowUpEveryTime.ordinal ||
            this == ProfileEmail || this == ProfileVerifyEmail || this == ProfileDob ||
            // Both screens in "The real you" draw a back chevron, so the gesture has to agree
            // with it -- the same fix, and the same reason, as ProfileEmail above.
            this == ProfilePhotos || this == ProfilePrompts
}

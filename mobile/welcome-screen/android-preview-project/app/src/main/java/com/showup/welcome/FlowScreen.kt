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
     * A RESUME POINT SINCE SHOWUP-165. It was not one: the step is optional, and no fact on the
     * account told a skip from a step never reached, so a relaunch here landed on Home. The server
     * now keeps the saved flow position -- the furthest step completed or skipped after prompts --
     * and Continue and Skip both report `media_video`. See `profile/ProfileFlow.kt`.
     */
    ProfileMedia,

    /**
     * The notification permission ask (SHOWUP-162), between media and Stay reachable.
     *
     * NOT A STEP, and a resume point since SHOWUP-165 gave the account a saved flow position:
     * Continue reports `notifications`. A resume onto it still goes through `afterMedia`, so an
     * already-determined status skips it exactly as the forward walk does. It raises no dialog
     * any more (SHOWUP-163), so there is no dead button to relaunch onto.
     */
    ProfileNotifications,

    /**
     * Stay reachable (SHOWUP-163), between the notifications explainer and Location (12).
     *
     * NOT A STEP; a resume point since SHOWUP-165 -- Save preferences reports `reachability`.
     *
     * IT IS NEVER SKIPPED. 09 still is, on an already-determined status -- but 10 is where the
     * toggle and the consent live, so every path out of media reaches it.
     */
    ProfileReachability,

    /**
     * The location ask (SHOWUP-165): three states, one position. A, B and C are states of this
     * screen, never separate positions -- "do not build B or C as a separate screen". Reached
     * through the arrival matrix, which can skip it silently on a grant.
     */
    ProfileLocation,

    /**
     * The second bridge (SHOWUP-166). Never a resume point, like the first: it holds nothing and no
     * fact says it was seen. Past location, a relaunch lands on height.
     */
    ProfileEmbraceDetails,

    // ── "Share some details", steps 1 to 7 (SHOWUP-167 to SHOWUP-173) ─────────
    //
    // In walk order, which is also back order: each one's back pops to the one above it.
    ProfileHeight,
    ProfileGender,
    ProfileOrientation,
    ProfileDatingLanguage,
    ProfileEducation,
    ProfileReligion,
    ProfilePolitics,

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

/*
 * BasicsStep.kt
 * ShowUp · where in "The basics" the user is
 *
 * The same shape as `welcome/FlowScreen.kt`, and for the same reasons: a typed position with no
 * magic values, an exhaustive `when` at the host, and a Kotlin enum so `rememberSaveable` persists
 * it with nothing added.
 *
 * WHY THIS IS A SECOND ENUM AND NOT MORE CASES ON FlowScreen
 *
 * FlowScreen is the app's outer position -- sign-up, the tutorial cards, home. "The basics" is a
 * flow WITHIN one of those positions, with its own progress bar, its own back rules and its own
 * persistence. Folding four more cases into FlowScreen would put the tutorial's cards and a profile
 * step in one list whose ordinal drives a screen transition, and the two do not share a transition.
 *
 * That is also the answer to the navigation question this raises: nothing here needs a framework.
 * One linear flow, one enum, exhaustive. The trigger for revisiting that is written down in
 * docs/mobile-client-architecture-spike.md 1.5.1 and this is not it.
 */
package com.showup.profile

/** A step of "The basics". */
enum class BasicsStep {
    Name,
    Email,

    /**
     * The code screen. NOT a fourth segment.
     *
     * It carries [progressSegment] 2, the same as [Email]: the user is on the email step until the
     * code is confirmed, and the bar advancing before that would claim progress they have not made.
     * The flow README states it twice and the design's own §2 registry gives it `step_index` 2.
     */
    EmailVerify,

    Dob,
    ;

    /** Which of the three segments is filled. [EmailVerify] deliberately holds at 2. */
    val progressSegment: Int
        get() = when (this) {
            Name -> 1
            Email, EmailVerify -> 2
            Dob -> 3
        }

    /**
     * Whether the header shows a back chevron.
     *
     * False on [Name] only. Profile creation is mandatory once entered -- the user arrives from the
     * tutorial and there is nothing behind step 1 -- so the chevron is absent AND the platform back
     * is suppressed. A header with no chevron that the OS can still dismiss is worse than no rule.
     */
    val hasBack: Boolean get() = this != Name

    /** `step_id` from the taxonomy's §2 vocabulary. */
    val stepId: String
        get() = when (this) {
            Name -> "name"
            Email -> "email"
            EmailVerify -> "email_verify"
            Dob -> "dob"
        }

    /**
     * `step_index` for the funnel.
     *
     * Taken from the tickets, NOT from the registry: `enums.json` §2 lists step_index as "—" for
     * all four of these, so there is nothing to read. Recorded as a mismatch rather than silently
     * invented -- see the audit note in ProfileAnalytics.
     */
    val stepIndex: Int get() = progressSegment
}

/**
 * Everything "The basics" collects, plus where the user got to.
 *
 * Held as one value so persistence is one write. The flow resumes at [step], and resuming is
 * silent: no prompt, no toast, and no error state on arrival, which is why no error flag lives
 * here -- an error belongs to a press, not to the saved position.
 */
data class BasicsState(
    val step: BasicsStep = BasicsStep.Name,
    val firstName: String = "",
    val email: String = "",
    val marketingConsent: Boolean = false,
)

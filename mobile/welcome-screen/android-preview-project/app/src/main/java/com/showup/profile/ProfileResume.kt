/*
 * ProfileResume.kt
 * ShowUp · where a half-finished profile picks up (flow README rule 4a)
 *
 * The rule, in its own words:
 *
 *     "The flow resumes where the user left off. Progress is persisted per completed step --
 *     values plus the position. On launch, an account with an incomplete profile routes straight
 *     to its LAST INCOMPLETE STEP, with everything already entered still present. Resuming is
 *     SILENT: no prompt, no toast, no 'welcome back'... A resumed step behaves like a freshly
 *     reached one: no error state on arrival."
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE PROGRESS IS DERIVED FROM THE SERVER, NOT MIRRORED ON THE PHONE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The rule says "persisted", and the obvious reading is a local store written after each step.
 * That reading is worse than what it asks for:
 *
 *   · a local mirror is lost on reinstall, and a user who deletes the app mid-profile comes back
 *     to step one with three photos already on their account
 *   · it is wrong on a second device
 *   · it can disagree with the server, and then the app shows a step the account has already
 *     completed -- which is how somebody is asked for a name they have given
 *
 * The server already holds every fact: the display name and the date of birth on the profile, the
 * address and its verified-at stamp on the user, the photos and the prompts in their own tables.
 * `GET /me/profile/progress` returns them in one call, and [resumePoint] turns them into a
 * position. Nothing is stored on the phone, so there is nothing to fall out of step.
 *
 * WHAT IS STILL LOCAL: the half-typed input on the step the user is ON. That is not progress -- it
 * has not been submitted -- and it lives where it always did, in `rememberSaveable` and the
 * prompts ViewModel's `SavedStateHandle`.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * A DECISION, NOT A ROUTE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * [resumePoint] returns a [ResumePoint] and not a `FlowScreen`, for the same reason `BasicsStep`
 * is not `FlowScreen`: this file describes the PROFILE's own positions, and the app's outer router
 * maps them. It is also why this is a plain function over a data class -- it is unit-tested with
 * no network, no device and no Compose, and the iOS half is the same function in Swift.
 */
package com.showup.profile

/**
 * The facts `GET /me/profile/progress` returns.
 *
 * Deliberately not "which step" -- see the file header. The server supplies what the decision is
 * made from and the client makes it, exactly as `TutorialRouting` already does.
 */
data class ProfileProgress(
    val displayName: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val hasDateOfBirth: Boolean = false,
    val photoCount: Int = 0,
    val promptCount: Int = 0,
)

/** Where in profile creation an account has got to. */
enum class ResumePoint {
    Name,
    Email,
    VerifyEmail,
    Dob,
    Photos,
    Prompts,

    /** Everything the flow asks for is on the account. */
    Done,
}

/**
 * The last incomplete step.
 *
 * IN FLOW ORDER, first gap wins. Written as a chain rather than a table because the order IS the
 * rule: each step is only reachable once the one before it is satisfied, which is what makes
 * "resume" and "walk forwards" land in the same place.
 *
 * THE BRIDGE IS NEVER A RESUME POINT. SHOWUP-155's acceptance criteria say so directly --
 * "relaunching lands on photos and not on this bridge" -- and the reason is that the bridge is not
 * a step and holds nothing: there is no fact on the account that says whether it was seen. It is a
 * beat on the forward walk, and a beat is not somewhere to be returned to.
 *
 * A BLANK NAME IS NO NAME. The server stores whatever it was given and the screen refuses
 * whitespace, but a value that arrived some other way must not skip the step.
 */
fun resumePoint(progress: ProfileProgress): ResumePoint = when {
    progress.displayName.isNullOrBlank() -> ResumePoint.Name
    progress.email.isNullOrBlank() -> ResumePoint.Email
    // An address that has not been confirmed holds at the code screen, not at the email screen --
    // the address is already stored, and sending the user back to re-type it would lose the
    // challenge that is already in flight. The progress bar holds at 2 for the same reason.
    !progress.emailVerified -> ResumePoint.VerifyEmail
    !progress.hasDateOfBirth -> ResumePoint.Dob
    progress.photoCount < PHOTOS_REQUIRED -> ResumePoint.Photos
    progress.promptCount < PROMPTS_REQUIRED -> ResumePoint.Prompts
    else -> ResumePoint.Done
}

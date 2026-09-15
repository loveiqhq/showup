/*
 * ProfileResumeTest.kt
 * ShowUp · where a half-finished profile picks up (flow README rule 4a)
 *
 * The rule is "route straight to the LAST INCOMPLETE STEP", which is one sentence and six ways to
 * get wrong. Every gap is checked here, plus the two cases that are not gaps at all: the bridge,
 * which must never be resumed onto, and a finished profile, which must not re-enter the flow.
 *
 * No network, no device, no Compose -- which is the point of the decision being a plain function
 * over a data class rather than something the router works out inline.
 */
package com.showup.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileResumeTest {

    private val complete = ProfileProgress(
        displayName = "Leo",
        email = "leo@hey.com",
        emailVerified = true,
        hasDateOfBirth = true,
        photoCount = PHOTOS_REQUIRED,
        promptCount = PROMPTS_REQUIRED,
    )

    @Test
    fun `a brand new account starts at the name`() {
        assertEquals(ResumePoint.Name, resumePoint(ProfileProgress()))
    }

    @Test
    fun `a name but no address holds at the email step`() {
        assertEquals(
            ResumePoint.Email,
            resumePoint(complete.copy(email = null, emailVerified = false)),
        )
    }

    @Test
    fun `an unconfirmed address holds at the CODE screen, not the email screen`() {
        // The address is already stored; sending the user back to re-type it would lose the
        // challenge that is in flight. The progress bar holds at 2 for the same reason.
        assertEquals(ResumePoint.VerifyEmail, resumePoint(complete.copy(emailVerified = false)))
    }

    @Test
    fun `a verified address with no date of birth holds at the date`() {
        assertEquals(ResumePoint.Dob, resumePoint(complete.copy(hasDateOfBirth = false)))
    }

    @Test
    fun `a finished basics section goes to photos and NEVER to the bridge`() {
        // SHOWUP-155's acceptance criteria say it directly: "relaunching lands on photos and not
        // on this bridge". The bridge is a beat on the forward walk and holds no state, so there
        // is no fact that could say it had been seen.
        assertEquals(ResumePoint.Photos, resumePoint(complete.copy(photoCount = 0, promptCount = 0)))
    }

    @Test
    fun `three photos is not four`() {
        assertEquals(
            ResumePoint.Photos,
            resumePoint(complete.copy(photoCount = PHOTOS_REQUIRED - 1, promptCount = 0)),
        )
    }

    @Test
    fun `four photos and no prompt holds at prompts`() {
        assertEquals(ResumePoint.Prompts, resumePoint(complete.copy(promptCount = 0)))
    }

    @Test
    fun `one prompt is enough to be finished`() {
        assertEquals(ResumePoint.Done, resumePoint(complete))
    }

    @Test
    fun `more than the minimum is still finished`() {
        assertEquals(
            ResumePoint.Done,
            resumePoint(complete.copy(photoCount = PHOTOS_MAX, promptCount = PROMPTS_MAX)),
        )
    }

    // ── the cases that would silently skip a step ───────────────────────────

    @Test
    fun `a blank name is no name`() {
        // The screen refuses whitespace, but a value that arrived some other way must not skip
        // the step -- otherwise the user lands on email with an empty profile behind them.
        assertEquals(ResumePoint.Name, resumePoint(complete.copy(displayName = "   ")))
        assertEquals(ResumePoint.Name, resumePoint(complete.copy(displayName = "")))
    }

    @Test
    fun `a blank address is no address`() {
        assertEquals(ResumePoint.Email, resumePoint(complete.copy(email = "  ")))
    }

    @Test
    fun `the order is the rule, so an early gap wins over a later one`() {
        // Photos and prompts are both done and there is no name. The name is what the user sees.
        val odd = complete.copy(displayName = null)
        assertEquals(ResumePoint.Name, resumePoint(odd))
    }

    @Test
    fun `every step in the flow is reachable as a resume point`() {
        // A guard against a future reorder quietly making one unreachable -- which would mean a
        // user could get stuck on a step the resume never returns them to.
        val reached = listOf(
            resumePoint(ProfileProgress()),
            resumePoint(complete.copy(email = null)),
            resumePoint(complete.copy(emailVerified = false)),
            resumePoint(complete.copy(hasDateOfBirth = false)),
            resumePoint(complete.copy(photoCount = 0)),
            resumePoint(complete.copy(promptCount = 0)),
            resumePoint(complete),
        ).toSet()
        assertEquals(ResumePoint.entries.toSet(), reached)
    }
}

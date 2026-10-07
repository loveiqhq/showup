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
    fun `one prompt finishes the real-you facts and the walk goes on to media`() {
        // Until SHOWUP-165 this was Done, and a relaunch here skipped media, both asks and every
        // detail step. With no saved position yet, the next step is media.
        assertEquals(ResumePoint.Media, resumePoint(complete))
    }

    @Test
    fun `more than the minimum is the same`() {
        assertEquals(
            ResumePoint.Media,
            resumePoint(complete.copy(photoCount = PHOTOS_MAX, promptCount = PROMPTS_MAX)),
        )
    }

    // ── past prompts: the saved flow position (SHOWUP-165 to SHOWUP-173) ──────

    /** Everything a finished profile holds, at [position]. */
    private fun at(position: String?) =
        complete.copy(flowPosition = position, hasGender = true, hasOrientation = true)

    @Test
    fun `each reached step resumes onto the one after it`() {
        val walk = listOf(
            null to ResumePoint.Media,
            "media_video" to ResumePoint.Notifications,
            "notifications" to ResumePoint.Reachability,
            "reachability" to ResumePoint.Location,
            "location" to ResumePoint.Height,
            "height" to ResumePoint.Gender,
            "gender" to ResumePoint.Orientation,
            "orientation" to ResumePoint.DatingLanguage,
            "dating_language" to ResumePoint.Education,
            "education" to ResumePoint.Religion,
            "religion" to ResumePoint.Politics,
            "politics" to ResumePoint.Done,
        )
        walk.forEach { (position, expected) ->
            assertEquals("at $position", expected, resumePoint(at(position)))
        }
    }

    @Test
    fun `the media screen's other id is the same step`() {
        assertEquals(ResumePoint.Notifications, resumePoint(at("media_voice")))
    }

    @Test
    fun `past location the relaunch lands on height and NEVER on Embrace 2`() {
        // The second bridge holds nothing and no fact says it was seen -- the first bridge's rule.
        assertEquals(ResumePoint.Height, resumePoint(at("location")))
    }

    @Test
    fun `a position past gender with no gender stored comes back for it`() {
        // An old free-text value the migration could not map is NULL now. Gender is mandatory,
        // so the account returns to it rather than walking on with no answer.
        val missing = at("politics").copy(hasGender = false)
        assertEquals(ResumePoint.Gender, resumePoint(missing))
    }

    @Test
    fun `the same for orientation`() {
        val missing = at("religion").copy(hasOrientation = false)
        assertEquals(ResumePoint.Orientation, resumePoint(missing))
    }

    @Test
    fun `a skipped step leaves no answer and still counts as passed`() {
        // Skip "saves nothing for that step" -- the position is the only evidence, and it is
        // enough: an account past education with no education resumes on religion.
        assertEquals(ResumePoint.Religion, resumePoint(at("education")))
    }

    @Test
    fun `a position this build does not know resumes early, never late`() {
        // A newer server may store a step this app has no screen for. Showing a screen twice is
        // recoverable; skipping one silently is the bug the position exists to prevent.
        assertEquals(ResumePoint.Media, resumePoint(at("interests")))
        assertEquals(ResumePoint.Media, resumePoint(at("garbage")))
    }

    @Test
    fun `an early gap still wins over the saved position`() {
        // The facts before prompts are stronger evidence than any position.
        assertEquals(ResumePoint.Photos, resumePoint(at("politics").copy(photoCount = 0)))
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
        val positions = listOf(
            null, "media_video", "notifications", "reachability", "location", "height", "gender",
            "orientation", "dating_language", "education", "religion", "politics",
        )
        val reached = (
            listOf(
                resumePoint(ProfileProgress()),
                resumePoint(complete.copy(email = null)),
                resumePoint(complete.copy(emailVerified = false)),
                resumePoint(complete.copy(hasDateOfBirth = false)),
                resumePoint(complete.copy(photoCount = 0)),
                resumePoint(complete.copy(promptCount = 0)),
            ) + positions.map { resumePoint(at(it)) }
            ).toSet()
        assertEquals(ResumePoint.entries.toSet(), reached)
    }
}

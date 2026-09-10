/*
 * ScreenFitTest.kt
 * ShowUp · every screen, every state, on every phone the app has to run on
 *
 * This is the answer to "does it actually fit". Seventeen device sizes from the 320x686 Galaxy Fold
 * cover screen up to the 440x956 iPhone 16 Pro Max, against every screen and every state either
 * flow can be in --
 * including the states a person only reaches by getting something wrong, which is exactly where a
 * layout is least likely to have been looked at.
 *
 * The measurements come from FitHarness, which is itself checked by HarnessSelfTest. Read that
 * first if you doubt a result here: a green run means nothing unless the instrument fires.
 *
 * Each test prints its full findings before asserting, so a failure tells you the device, the
 * element and the overshoot rather than only that something, somewhere, is wrong.
 */
package com.showup.fit

import com.showup.HomePlaceholderScreen
import com.showup.tutorial.MatchMeansMeetScreen
import com.showup.tutorial.MatchOnAvailabilityScreen
import com.showup.tutorial.MeetInRealLifeScreen
import com.showup.tutorial.ShowUpEveryTimeScreen
import com.showup.tutorial.ThirtyMinutesScreen
import com.showup.tutorial.WelcomeScreen
import com.showup.welcome.AuthMethod
import com.showup.welcome.ConnectAccountScreen
import com.showup.welcome.ConnectState
import com.showup.welcome.ErrorKind
import com.showup.welcome.PhoneError
import com.showup.welcome.PhoneNumberScreen
import com.showup.welcome.SignUpOutcome
import com.showup.profile.DateOrder
import com.showup.profile.ProfileDobScreen
import com.showup.profile.ProfileEmailScreen
import com.showup.profile.ProfileNameScreen
import com.showup.profile.ProfileVerifyEmailScreen
import com.showup.profile.VerifyState
import com.showup.welcome.StartupScreen
import com.showup.welcome.VerifyCodeScreen
import com.showup.welcome.WelcomeBackScreen
import com.showup.welcome.countryForRegion
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class ScreenFitTest {

    /** Runs one screen state across the whole device matrix and reports everything it found. */
    private fun sweep(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val found = DEVICES.flatMap { measureFit(it, name, content) }
        report(name, found)
    }

    /**
     * EMPTY, since 10 September 2026, and that is the headline.
     *
     * This held eight element labels -- the findings recorded in audit/FIT-2026-08-31.md, all of
     * them the bottom band of a screen squeezed on a short phone. The comment here said the list
     * was meant to shrink and that adding to it was a defeat. It has shrunk to nothing: 117
     * findings across Connect and Welcome back are gone, fixed rather than accepted.
     *
     * Two changes did it. `WelcomeScaffold(scrollWhenTight = true)` on both screens lets the
     * layout ask for the height it needs instead of being clamped, and `PrimaryButton` takes a
     * minimum height rather than a fixed one so a label too wide for a 320dp phone wraps instead
     * of being cut. Neither changes anything on a device where the content already fitted.
     *
     * Keep it empty. A finding that appears here again is a real regression on a real phone, and
     * the honest response is to fix the screen rather than to write its name down.
     */
    private val known = emptyList<String>()

    /**
     * Nothing is known any more, so nothing is excused.
     *
     * The blanket passes for "Next" and "Back" went with the list. They were there because those
     * two words are short enough to appear inside an unrelated label, which mattered when the
     * list was doing real suppression; with nothing to suppress they only stood between this
     * suite and a genuine finding on the two most important controls in the flow.
     */
    private fun Violation.isKnown(): Boolean = known.any { element.contains(it) }

    private val collected = mutableListOf<Violation>()

    private fun report(name: String, found: List<Violation>) {
        collected += found
        val real = found.filterNot { it.advisory }
        val fresh = real.filterNot { it.isKnown() }
        val backlog = real.count { it.isKnown() }
        when {
            real.isEmpty() ->
                println("OK   $name -- clean on all ${DEVICES.size} devices")
            fresh.isEmpty() ->
                println("OK   $name -- clean apart from $backlog known finding(s); " +
                    "see audit/FIT-2026-08-31.md")
            else -> {
                println("FAIL $name -- ${fresh.size} NEW problem(s):")
                fresh.forEach { println("       $it") }
            }
        }
    }

    private fun assertClean() {
        val fresh = collected.filterNot { it.advisory }.filterNot { it.isKnown() }
        assertTrue(
            "NEW layout problems, not in audit/FIT-2026-08-31.md:\n" +
                fresh.joinToString("\n") { "  $it" },
            fresh.isEmpty(),
        )
    }

    // ── welcome and sign-up ─────────────────────────────────────────────────

    @Test
    fun `startup`() {
        sweep("Startup") { StartupScreen() }
        // The dates figure is off today but is a product decision, not a deleted feature, so the
        // layout has to hold with it on too.
        sweep("Startup + social proof") { StartupScreen(showSocialProof = true) }
        assertClean()
    }

    @Test
    fun `welcome back, every last-used value and every name length`() {
        // SHOWUP-142 names these cases outright: four methods, the unknown fallback, and headline
        // behaviour at 2 characters, 24 characters and no name at all.
        listOf(
            AuthMethod.Phone, AuthMethod.Apple, AuthMethod.Google,
            AuthMethod.Facebook, AuthMethod.Unknown,
        ).forEach { m ->
            sweep("WelcomeBack/$m") { WelcomeBackScreen(name = "Leo", lastUsed = m) }
        }
        sweep("WelcomeBack/no name") { WelcomeBackScreen(name = "", lastUsed = AuthMethod.Unknown) }
        sweep("WelcomeBack/2 chars") { WelcomeBackScreen(name = "Jo", lastUsed = AuthMethod.Phone) }
        sweep("WelcomeBack/24 chars") {
            WelcomeBackScreen(name = "Maximiliane Fürstenberg", lastUsed = AuthMethod.Phone)
        }
        assertClean()
    }

    @Test
    fun `phone number entry, empty and every error`() {
        sweep("Phone/empty") { PhoneNumberScreen() }
        sweep("Phone/typed") { PhoneNumberScreen(value = "17612345678") }
        // Every error string has to fit the reserved space, and the longest one is the test.
        PhoneError.values().forEach { e ->
            sweep("Phone/$e") { PhoneNumberScreen(value = "1761", error = e) }
        }
        // A long country name in the pill, and a long dial code.
        sweep("Phone/long country") {
            PhoneNumberScreen(value = "5551234", country = countryForRegion("GB"))
        }
        assertClean()
    }

    @Test
    fun `code entry, idle mismatch and cooldown`() {
        sweep("Code/empty") { VerifyCodeScreen() }
        sweep("Code/typed") { VerifyCodeScreen(digits = "4807") }
        sweep("Code/mismatch") { VerifyCodeScreen(digits = "480000", mismatch = true) }
        sweep("Code/resend ready") { VerifyCodeScreen(cooldownSeconds = 0) }
        // The attempt cap's own state. Its card carries a DIFFERENT string from the mismatch one,
        // and the region under the slots is a 42 floor sized for one line -- so a message that
        // wrapped would grow the region and take the CTA with it. This is the sweep that would
        // catch that, and the reason the copy was kept to the mismatch line's length.
        sweep("Code/locked out") {
            VerifyCodeScreen(digits = "480000", mismatch = true, lockedOut = true)
        }
        // The number is echoed back, so a long international one is the widest this can get.
        sweep("Code/long number") { VerifyCodeScreen(phone = "+880 1712 345678") }
        assertClean()
    }

    @Test
    fun `connect account, all ten states`() {
        sweep("Connect/idle") { ConnectAccountScreen(state = ConnectState.Idle) }
        sweep("Connect/tapped") { ConnectAccountScreen(state = ConnectState.Tapped) }
        sweep("Connect/handoff") { ConnectAccountScreen(state = ConnectState.Handoff) }
        sweep("Connect/linking") { ConnectAccountScreen(state = ConnectState.Linking) }
        sweep("Connect/success") { ConnectAccountScreen(state = ConnectState.Success) }
        sweep("Connect/success no name") {
            ConnectAccountScreen(state = ConnectState.Success, firstName = null)
        }
        sweep("Connect/success long name") {
            ConnectAccountScreen(state = ConnectState.Success, firstName = "Maximiliane")
        }
        sweep("Connect/cancelled") { ConnectAccountScreen(state = ConnectState.Cancelled) }
        sweep("Connect/error network") {
            ConnectAccountScreen(state = ConnectState.Error, kind = ErrorKind.Network)
        }
        sweep("Connect/error declined") {
            ConnectAccountScreen(state = ConnectState.Error, kind = ErrorKind.Declined)
        }
        sweep("Connect/conflict") { ConnectAccountScreen(state = ConnectState.Conflict) }
        sweep("Connect/conflict no email") {
            ConnectAccountScreen(state = ConnectState.Conflict, conflictEmail = null)
        }
        sweep("Connect/conflict long email") {
            ConnectAccountScreen(
                state = ConnectState.Conflict,
                conflictEmail = "leonhard.schwarzkopf@studio-mantis.example.com",
            )
        }
        assertClean()
    }

    @Test
    fun `the home placeholder, both outcomes`() {
        sweep("Home/new") { HomePlaceholderScreen(SignUpOutcome.NewAccount, {}) }
        sweep("Home/returning") { HomePlaceholderScreen(SignUpOutcome.ReturningMember, {}) }
        assertClean()
    }

    // ── profile creation · "The basics" ─────────────────────────────────────

    /**
     * SHOWUP-150 and SHOWUP-152, every state.
     *
     * The error states are the ones worth sweeping: on name the status region is RESERVED so
     * nothing should move, and on email it deliberately is not, so the consent row and CTA shift
     * ~18 lower. Both claims are about layout at every width, which is what this harness measures.
     */
    @Test
    fun `profile basics, all states`() {
        sweep("Profile/name empty") { ProfileNameScreen() }
        sweep("Profile/name typed") { ProfileNameScreen(value = "Leo") }
        sweep("Profile/name long") { ProfileNameScreen(value = "Maximiliane") }

        sweep("Profile/email valid") { ProfileEmailScreen(value = "leo@hey.com") }
        sweep("Profile/email invalid") { ProfileEmailScreen(value = "leo@hey") }
        sweep("Profile/email empty") { ProfileEmailScreen() }
        sweep("Profile/email consent on") {
            ProfileEmailScreen(value = "leo@hey.com", consent = true)
        }
        assertClean()
    }

    /**
     * SHOWUP-153 and SHOWUP-154, every state.
     *
     * The two reserved regions are what these are really measuring: 30 on the code screen and 84
     * on the date screen, both held in EVERY state so the CTA and the secondary rows do not move
     * between them. A failure here is almost always a region that was sized to its content.
     */
    @Test
    fun `profile verify email and date of birth, all states`() {
        sweep("Verify/arrival") { ProfileVerifyEmailScreen() }
        sweep("Verify/typed") { ProfileVerifyEmailScreen(digits = "4821") }
        sweep("Verify/mismatch") {
            ProfileVerifyEmailScreen(digits = "482170", state = VerifyState.Mismatch)
        }
        sweep("Verify/expired") {
            ProfileVerifyEmailScreen(digits = "482170", state = VerifyState.Expired)
        }
        sweep("Verify/locked out") {
            ProfileVerifyEmailScreen(digits = "482170", state = VerifyState.LockedOut)
        }
        // Never truncated, wraps if long -- so the longest plausible address is swept too.
        sweep("Verify/long address") {
            ProfileVerifyEmailScreen(email = "leonardo.buonarroti@a-very-long-domain.example")
        }

        sweep("DoB/empty") { ProfileDobScreen() }
        sweep("DoB/confirm") { ProfileDobScreen(value = "03/22/1998") }
        sweep("DoB/impossible") { ProfileDobScreen(value = "02/30/1990") }
        sweep("DoB/under 18") { ProfileDobScreen(value = "05/19/2015") }
        sweep("DoB/incomplete after press") {
            ProfileDobScreen(value = "03/22", attempted = true)
        }
        sweep("DoB/age hidden") { ProfileDobScreen(value = "03/22/1998", hideAge = true) }
        sweep("DoB/day-first locale") {
            ProfileDobScreen(value = "22/03/1998", order = DateOrder.DayFirst)
        }
        assertClean()
    }

    // ── the tutorial ────────────────────────────────────────────────────────

    @Test
    fun `all six tutorial cards`() {
        sweep("Tutorial/1 welcome") { WelcomeScreen() }
        sweep("Tutorial/2 meet in real life") { MeetInRealLifeScreen() }
        sweep("Tutorial/3 availability") { MatchOnAvailabilityScreen() }
        sweep("Tutorial/4 binding date") { MatchMeansMeetScreen() }
        sweep("Tutorial/5 thirty minutes") { ThirtyMinutesScreen() }
        sweep("Tutorial/6 show up every time") { ShowUpEveryTimeScreen() }
        assertClean()
    }
}

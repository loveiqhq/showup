package com.showup.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the event catalogue itself: names, uniqueness, and the exact properties each ticket asks
 * for.
 *
 * A plain JVM test. The catalogue is data, and data that is only exercised through a rendered
 * screen is data whose typos surface in the warehouse three weeks later, when the funnel has a hole
 * in it and nobody can say when it started.
 */
class SignUpAnalyticsTest {

    // ── the catalogue ───────────────────────────────────────────────────────

    @Test
    fun `every event name is unique`() {
        val names = SignUpAnalytics.allEventNames()
        // A duplicate would silently merge two different things into one funnel step.
        assertEquals("duplicate event names", names.size, names.toSet().size)
    }

    @Test
    fun `every event name follows the backend's snake_case convention`() {
        // The backend emits date_confirmed, like_sent, account_created. One taxonomy across client
        // and server, or the funnel has to be reassembled by hand in the warehouse.
        val convention = Regex("^[a-z][a-z0-9_]*[a-z0-9]$")
        for (name in SignUpAnalytics.allEventNames()) {
            assertTrue("'$name' is not snake_case", convention.matches(name))
        }
    }

    @Test
    fun `the catalogue covers every event the five tickets name`() {
        // 23 = 1 screenview + 3 from SHOWUP-140 + 3 from 142/145 + 6 from 143 + 10 from 144.
        // If this number changes, a ticket's tracking section changed with it -- go and read it.
        assertEquals(23, SignUpAnalytics.allEventNames().size)
    }

    // ── screen names are verbatim from the tickets ──────────────────────────

    @Test
    fun `screen names match the tickets exactly, including their inconsistent casing`() {
        // These look wrong because they ARE inconsistent in the tickets. Normalising them would
        // make this file disagree with the specification it implements.
        assertEquals("Signup - CreateAccount", SignUpAnalytics.Screen.CREATE_ACCOUNT)
        assertEquals("Signup - welcomeback", SignUpAnalytics.Screen.WELCOME_BACK)
        assertEquals("SSOLogin", SignUpAnalytics.Screen.SSO_LOGIN)
        assertEquals("Signup - Phonenumber", SignUpAnalytics.Screen.PHONE_NUMBER)
        assertEquals("Signup - Codeentry", SignUpAnalytics.Screen.CODE_ENTRY)
        assertEquals("ConnectSSO", SignUpAnalytics.Screen.CONNECT_SSO)
    }

    @Test
    fun `a screenview always carries its screen name and merges extra properties`() {
        val (event, props) = SignUpAnalytics.screenViewed(
            SignUpAnalytics.Screen.WELCOME_BACK,
            mapOf("last_used" to "apple"),
        )

        assertEquals("screen_viewed", event)
        assertEquals("Signup - welcomeback", props["screen_name"])
        assertEquals("apple", props["last_used"])
    }

    // ── the property-bearing events each ticket specifies ───────────────────

    @Test
    fun `auth method tap carries method and is_last_used, as SHOWUP-142 asks`() {
        val (event, props) = SignUpAnalytics.authMethodTapped("apple", isLastUsed = true)

        assertEquals("signup_auth_method_tapped", event)
        assertEquals("apple", props["method"])
        assertEquals(true, props["is_last_used"])
    }

    @Test
    fun `code verify failure carries the attempt number, as SHOWUP-143 asks`() {
        val (_, props) = SignUpAnalytics.codeVerifyFailed(attempt = 3)
        assertEquals(3, props["attempt"])
    }

    @Test
    fun `resend carries seconds waited and whether it followed a mismatch`() {
        val (_, props) = SignUpAnalytics.resendRequested(secondsWaited = 12, afterMismatch = true)

        assertEquals(12, props["seconds_waited"])
        assertEquals(true, props["after_mismatch"])
    }

    @Test
    fun `a repeat conflict carries its count, so the second is distinguishable from the first`() {
        val (event, props) = SignUpAnalytics.conflictRepeated(count = 2, provider = "google")

        assertEquals("connect_conflict_repeated", event)
        assertEquals(2, props["count"])
        assertEquals("google", props["provider"])
    }

    @Test
    fun `a link failure names both the provider and the kind`() {
        val (_, props) = SignUpAnalytics.linkFailed("apple", "network")

        // The ticket has one "link succeeded/failed" event; network and declined are different
        // problems and collapsing them would make the event useless for deciding what to fix.
        assertEquals("apple", props["provider"])
        assertEquals("network", props["kind"])
    }

    @Test
    fun `a legal link tap names which link and which screen`() {
        val (event, props) = SignUpAnalytics.legalLinkTapped(
            SignUpAnalytics.Legal.PRIVACY,
            SignUpAnalytics.Screen.CREATE_ACCOUNT,
        )

        assertEquals("legal_link_tapped", event)
        assertEquals("privacy_policy", props["link"])
        // The same three links appear on four screens; without this the taps are indistinguishable.
        assertEquals("Signup - CreateAccount", props["screen_name"])
    }

    @Test
    fun `every property name is snake_case too`() {
        val builders = listOf(
            SignUpAnalytics.screenViewed(SignUpAnalytics.Screen.CODE_ENTRY),
            SignUpAnalytics.authMethodTapped("phone", false),
            SignUpAnalytics.phoneValidationFailed("tooshort", "DE"),
            SignUpAnalytics.codeVerifyFailed(1),
            SignUpAnalytics.resendRequested(0, false),
            SignUpAnalytics.linkFailed("apple", "declined"),
            SignUpAnalytics.conflictRepeated(2, "google"),
            SignUpAnalytics.legalLinkTapped(SignUpAnalytics.Legal.TERMS, "x"),
        )
        val convention = Regex("^[a-z][a-z0-9_]*$")

        for ((event, props) in builders) {
            for (key in props.keys) {
                assertTrue("$event property '$key' is not snake_case", convention.matches(key))
            }
        }
    }

    // ── the recorder the flow tests rely on ─────────────────────────────────

    @Test
    fun `the no-op tracker records nothing and does not throw`() {
        NoOpAnalytics.track("anything", mapOf("a" to 1))
    }

    @Test
    fun `the recording tracker keeps events in order and can be queried by name`() {
        val recorder = RecordingAnalytics()
        recorder.track("first", mapOf("n" to 1))
        recorder.track("second", emptyMap())
        recorder.track("first", mapOf("n" to 2))

        assertEquals(listOf("first", "second", "first"), recorder.names())
        assertEquals(2, recorder.of("first").size)
        assertEquals(2, recorder.of("first")[1].properties["n"])
    }
}

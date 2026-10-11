package com.showup.profile

import com.showup.analytics.RecordingAnalytics
import com.showup.api.InMemoryTokenStore
import com.showup.api.ShowUpApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime

/**
 * "The basics" reports what its tickets specify (SHOWUP-150 to 155, events.json family D).
 *
 * Until 8 October 2026 none of this fired: the builders existed and no call site did. These drive
 * the view model through each decision -- arrival, accepted and refused presses, the code's verdict,
 * the date's states -- and check the event, its payload, and what must NOT be sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BasicsTrackingTest {

    /** The server, scripted. The resend window is already open, so the countdown ends at once. */
    private class FakeRepo : BasicsRepository(
        ShowUpApi(baseUrl = "http://127.0.0.1:1/", tokens = InMemoryTokenStore()),
        offline = null,
    ) {
        val verdicts = ArrayDeque<VerifyCodeResult>()
        var send: SendCodeResult = SendCodeResult.Sent(
            expiresAt = OffsetDateTime.now().plusMinutes(10),
            resendAvailableAt = OffsetDateTime.now().minusSeconds(1),
            devCode = null,
        )
        var save: SaveBasicsResult = SaveBasicsResult.Saved(28)

        override suspend fun sendCode(email: String) = send
        override suspend fun verifyCode(code: String) = verdicts.removeFirst()
        override suspend fun saveDateOfBirth(iso: String, hideAge: Boolean) = save
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepo
    private lateinit var analytics: RecordingAnalytics
    private var clock = 0L
    private lateinit var model: BasicsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepo()
        analytics = RecordingAnalytics()
        model = BasicsViewModel(repo, analytics) { clock }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── name (SHOWUP-150) ────────────────────────────────────────────────────────

    @Test
    fun `arriving at the name step reports the screen, the step, and starts the build once`() {
        model.arrived(BasicsStep.Name, referrer = null)

        assertEquals(listOf("screen_viewed", "profile_step_viewed", "profile_build_started"), analytics.names())
        val view = analytics.of("screen_viewed").single().properties
        assertEquals("profile_name", view["screen_id"])
        assertEquals("ProfileName", view["screen_name"])
        assertEquals("name", analytics.of("profile_step_viewed").single().properties["step_id"])
        assertEquals(1, analytics.of("profile_step_viewed").single().properties["step_index"])
        assertEquals("cold", analytics.of("profile_build_started").single().properties["entry_point"])

        // A back to the name step is a new showing -- but the build started once.
        model.arrived(BasicsStep.Name, referrer = ProfileScreen.Email)
        assertEquals(1, analytics.of("profile_build_started").size)
        assertEquals("profile_email", analytics.of("screen_viewed").last().properties["referrer_screen_id"])
    }

    @Test
    fun `a valid name sends its length and the step's time, never the name`() {
        model.arrived(BasicsStep.Name, referrer = null)
        clock = 7_400
        model.nameAccepted("  Leonora ")

        val submitted = analytics.of("name_submitted").single().properties
        assertEquals(7, submitted["char_count"])
        assertFalse("the name itself is never sent", submitted.values.any { it == "Leonora" || it == "  Leonora " })
        val completed = analytics.of("profile_step_completed").single().properties
        assertEquals("name", completed["step_id"])
        assertEquals(7, completed["time_on_step_s"])
    }

    @Test
    fun `an empty name reports the refused press`() {
        model.nameRefused()
        val failed = analytics.of("form_validation_failed").single().properties
        assertEquals("first_name", failed["field_id"])
        assertEquals("required_missing", failed["rule"])
        assertEquals("profile_name", failed["screen_id"])
        assertEquals("name", failed["step_id"])
    }

    // ── email (SHOWUP-152) ───────────────────────────────────────────────────────

    @Test
    fun `a delivered code reports the domain only, and completes the email step`() = runTest(dispatcher) {
        model.arrived(BasicsStep.Email, referrer = ProfileScreen.Name)
        model.setEmail("Leo@Hey.COM")
        clock = 3_000
        var sent = false
        model.sendCode(onSent = { sent = true })
        advanceUntilIdle()

        assertTrue(sent)
        val submitted = analytics.of("email_submitted").single().properties
        assertEquals("hey.com", submitted["domain"])
        assertFalse("the address is never sent", submitted.values.any { it.toString().contains("@") })
        assertEquals("email", analytics.of("profile_step_completed").single().properties["step_id"])
        assertEquals(3, analytics.of("profile_step_completed").single().properties["time_on_step_s"])
    }

    @Test
    fun `a send that failed reports nothing, so a retry does not count twice`() = runTest(dispatcher) {
        repo.send = SendCodeResult.Failed(null)
        model.setEmail("leo@hey.com")
        model.sendCode()
        advanceUntilIdle()
        assertTrue(analytics.of("email_submitted").isEmpty())
    }

    @Test
    fun `empty is required_missing, malformed is format`() {
        model.emailRefused(empty = true)
        model.emailRefused(empty = false)

        assertEquals("email", analytics.of("form_validation_failed").single().properties["field_id"])
        assertEquals("format", analytics.of("email_validation_failed").single().properties["rule"])
    }

    @Test
    fun `the marketing row reports both directions`() {
        model.marketingConsentChanged(true)
        model.marketingConsentChanged(false)

        val changes = analytics.of("consent_changed").map { it.properties }
        assertEquals(listOf(true, false), changes.map { it["on"] })
        assertTrue(changes.all { it["channel"] == "marketing_email" && it["surface"] == "profile_creation" })
    }

    // ── the code (SHOWUP-153) ────────────────────────────────────────────────────

    private suspend fun kotlinx.coroutines.test.TestScope.sendFirstCode() {
        model.setEmail("leo@hey.com")
        model.sendCode()
        advanceUntilIdle()
        analytics.clear()
    }

    @Test
    fun `each attempt reports its number and verdict, and a pass completes the step`() = runTest(dispatcher) {
        sendFirstCode()
        model.arrived(BasicsStep.EmailVerify, referrer = ProfileScreen.Email)
        repo.verdicts += VerifyCodeResult.Refused
        repo.verdicts += VerifyCodeResult.Verified

        model.setDigits("111111")
        model.verify(onVerified = {})
        advanceUntilIdle()
        model.setDigits("482913")
        var verified = false
        model.verify(onVerified = { verified = true })
        advanceUntilIdle()

        assertTrue(verified)
        val attempts = analytics.of("email_code_submitted").map { it.properties }
        assertEquals(listOf(1, 2), attempts.map { it["attempt_no"] })
        assertEquals(listOf("fail", "pass"), attempts.map { it["result"] })
        assertFalse("the code is never sent", attempts.any { p -> p.values.any { it == "482913" || it == "111111" } })
        val step = analytics.of("profile_step_viewed").single().properties
        assertEquals("email_verify", step["step_id"])
        assertEquals(2, step["step_index"])
        assertEquals("email_verify", analytics.of("profile_step_completed").single().properties["step_id"])
    }

    @Test
    fun `a resend reports which resend it is`() = runTest(dispatcher) {
        sendFirstCode()
        model.resendCode()
        advanceUntilIdle()
        model.resendCode()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), analytics.of("email_code_resent").map { it.properties["attempt_no"] })
        assertTrue("a resend is not a second email_submitted", analytics.of("email_submitted").isEmpty())
    }

    @Test
    fun `a lost verify request is no verdict and reports nothing`() = runTest(dispatcher) {
        sendFirstCode()
        repo.verdicts += VerifyCodeResult.Failed(null)
        model.setDigits("482913")
        model.verify(onVerified = {})
        advanceUntilIdle()
        assertTrue(analytics.of("email_code_submitted").isEmpty())
    }

    // ── date of birth (SHOWUP-154) ───────────────────────────────────────────────

    @Test
    fun `an impossible date and an under-18 date each report once, on the way in`() {
        model.arrived(BasicsStep.Dob, referrer = ProfileScreen.EmailVerification)
        val order = DateOrder.DayFirst
        model.setDob("3102", order)
        model.setDob("310219", order)
        model.setDob("31021998", order) // 31 February: state C
        model.setDob("31021998", order) // still C -- a redraw, not a transition
        model.setDob("3102199", order) // back to incomplete
        model.setDob("01012015", order) // a real date, under 18: state D

        assertEquals(1, analytics.of("dob_validation_failed").size)
        assertEquals("impossible", analytics.of("dob_validation_failed").single().properties["rule"])
        val gate = analytics.of("age_gate_failed").single().properties
        assertTrue("an age, not a date", (gate["age"] as Int) in 10..12)
    }

    @Test
    fun `an incomplete date refused reports required_missing, an impossible one adds nothing`() {
        model.dobRefused(DobResult.Incomplete)
        model.dobRefused(DobResult.Impossible)

        val failed = analytics.of("form_validation_failed").single().properties
        assertEquals("age", failed["field_id"])
        assertEquals("profile_dob", failed["screen_id"])
        assertEquals("dob", failed["step_id"])
    }

    @Test
    fun `hiding the age reports on the tick only`() {
        model.setHideAge(true)
        model.setHideAge(true)
        model.setHideAge(false)

        assertEquals(1, analytics.of("field_display_opted_out").size)
        assertEquals("age", analytics.of("field_display_opted_out").single().properties["field_id"])
    }

    @Test
    fun `a stored date reports the age and completes the step, never the date`() = runTest(dispatcher) {
        model.arrived(BasicsStep.Dob, referrer = null)
        clock = 12_000
        var saved = false
        model.saveDateOfBirth("1998-03-22", age = 28, onSaved = { saved = true })
        advanceUntilIdle()

        assertTrue(saved)
        val submitted = analytics.of("dob_submitted").single().properties
        assertEquals(28, submitted["age"])
        assertFalse("never the date", submitted.values.any { it.toString().contains("1998") })
        assertEquals(12, analytics.of("profile_step_completed").single().properties["time_on_step_s"])
    }

    @Test
    fun `the server's age wins, and the screen's covers an answer without one`() = runTest(dispatcher) {
        repo.save = SaveBasicsResult.Saved(null)
        model.saveDateOfBirth("1998-03-22", age = 27, onSaved = {})
        advanceUntilIdle()
        assertEquals(27, analytics.of("dob_submitted").single().properties["age"])
    }

    // ── the bridge (SHOWUP-155) ──────────────────────────────────────────────────

    @Test
    fun `the bridge reports the screen and the bridge, and is not a step`() {
        model.embraceArrived(referrer = ProfileScreen.Dob)

        assertEquals(listOf("screen_viewed", "embrace_bridge_viewed"), analytics.names())
        assertEquals("profile_embrace_build", analytics.of("screen_viewed").single().properties["screen_id"])
        assertEquals("build_profile", analytics.of("embrace_bridge_viewed").single().properties["variant"])
    }

    @Test
    fun `every basics event carries the registry stamp`() = runTest(dispatcher) {
        model.arrived(BasicsStep.Name, referrer = null)
        model.nameAccepted("Leo")
        model.setHideAge(true)
        model.embraceArrived(referrer = null)
        assertTrue(analytics.all().all { "sensitivity_class" in it.properties && "field_registry_version" in it.properties })
    }
}

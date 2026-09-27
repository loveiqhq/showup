/*
 * ReachabilityRulesTest.kt
 * ShowUp · every rule Stay reachable has, with no device and no OS dialog (SHOWUP-163)
 *
 * WHAT THIS CAN PROVE: the save order, that the dialog is raised only when it should be, that a
 * user-initiated toggle-off and an OS denial produce the same dialog with different primaries,
 * that the toggle does not move until deactivation is confirmed, that an interest box records and
 * does nothing else, that nothing advances until the server confirms, and that the events the
 * ticket forbids never fire.
 *
 * WHAT IT CANNOT: that the platform actually raises a dialog, that a token reaches the backend,
 * or that Settings opens. `FakeHost` asks no OS and `NoConsentBackend` has no endpoint behind it.
 */
package com.showup.profile

import com.showup.analytics.AnalyticsTracker
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReachabilityRulesTest {

    private class Recorder : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun track(event: String, properties: Map<String, Any>) {
            events += event to properties
        }

        fun names() = events.map { it.first }
        fun count(name: String) = events.count { it.first == name }
        fun only(name: String) = events.single { it.first == name }.second
        fun clear() = events.clear()
    }

    /** Counts registrations without touching a network or a token. */
    private class FakePush : PushRegistration(
        api = ShowUpApi(baseUrl = "http://127.0.0.1:1/", tokens = InMemoryTokenStore()),
    ) {
        var calls = 0
        override suspend fun register(): PushRegistrationResult {
            calls++
            return PushRegistrationResult.NoTokenSource
        }
    }

    /** A consent write that can be made to fail, which is the only way to test "not optimistic". */
    private class FakeConsent(var succeeds: Boolean = true) : ReachabilityRepository {
        var calls = 0
        var lastValue: Boolean? = null
        override suspend fun savePushConsent(on: Boolean): Boolean {
            calls++
            lastValue = on
            return succeeds
        }
    }

    private class FakeHost(var grants: Boolean = true) : ReachabilityHost {
        var dialogs = 0
        var settingsOpened = 0
        override suspend fun requestNotificationPermission(): Boolean {
            dialogs++
            return grants
        }

        override fun openSettingsOrReprompt() {
            settingsOpened++
        }
    }

    /**
     * A status that MOVES, which `FixedNotificationAccess` cannot express.
     *
     * Answering the OS dialog changes what the next `read()` returns, and two rules on this screen
     * depend on that rather than on the answer we were handed: the toggle a scrim-dismiss leaves
     * behind, and the permission `reachability_saved` carries.
     */
    private class MovingAccess(
        var status: NotificationPermission = NotificationPermission.NotDetermined,
    ) : NotificationAccessReader {
        override fun read(): NotificationPermission = status
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var analytics: Recorder
    private lateinit var push: FakePush
    private lateinit var consent: FakeConsent
    private lateinit var host: FakeHost

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        analytics = Recorder()
        push = FakePush()
        consent = FakeConsent()
        host = FakeHost()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun build(
        status: NotificationPermission = NotificationPermission.NotDetermined,
    ) = ReachabilityViewModel(
        access = FixedNotificationAccess(status),
        push = push,
        consent = consent,
        analytics = analytics,
    ).also { it.attach(host) }

    // ── the defaults ────────────────────────────────────────────────────────

    @Test
    fun `push is on by default and nothing is checked`() {
        val vm = build()
        vm.arrived()
        val state = vm.state.value
        // AN OPT-OUT, NOT AN OPT-IN. The ticket decided this on 25 September: "the user switches
        // it off actively, through the confirm dialog".
        assertTrue("push defaults on", state.pushOn)
        assertEquals("the demand test starts empty", emptySet<InterestChannel>(), state.interest)
        assertNull(state.prompt)
    }

    @Test
    fun `a denied status shows the toggle off, because an opt-out cannot promise what the OS refused`() {
        val vm = build(NotificationPermission.Denied)
        vm.arrived()
        assertFalse(vm.state.value.pushOn)
    }

    // ── what fires on arrival ───────────────────────────────────────────────

    @Test
    fun `arriving reports the screen, and the pre-permission surface only when it is one`() {
        val vm = build(NotificationPermission.NotDetermined)
        vm.arrived()
        assertEquals(
            listOf(ProfileAnalytics.SCREEN_VIEWED, ProfileAnalytics.PERMISSION_PROMPTED),
            analytics.names(),
        )
        val viewed = analytics.only(ProfileAnalytics.SCREEN_VIEWED)
        assertEquals("profile_reachability", viewed["screen_id"])
        assertEquals("ProfileReachability", viewed["screen_name"])
        assertEquals("profile_notifications", viewed["referrer_screen_id"])
    }

    @Test
    fun `an already-answered status is not a pre-permission surface`() {
        // The ticket qualifies it: `permission_prompted` fires "only when the status is not
        // determined". Somebody whose answer is already on file is not being pre-permissioned.
        val vm = build(NotificationPermission.Granted)
        vm.arrived()
        assertEquals(0, analytics.count(ProfileAnalytics.PERMISSION_PROMPTED))
        assertEquals(1, analytics.count(ProfileAnalytics.SCREEN_VIEWED))
    }

    @Test
    fun `no step event fires here, in any state`() {
        val vm = build()
        vm.arrived()
        vm.pushChanged(false)
        vm.confirmDeactivation()
        vm.interestToggled(InterestChannel.Sms)
        assertTrue(
            "this screen is not a step and has no skip: ${analytics.names()}",
            analytics.names().none { it.startsWith("profile_step_") },
        )
    }

    // ── the demand test ─────────────────────────────────────────────────────

    @Test
    fun `checking a box reports the interest and does nothing else`() {
        val vm = build()
        vm.arrived()
        analytics.clear()

        vm.interestToggled(InterestChannel.AiCall)

        // ONE EVENT, and it is not a consent. No dialog, no navigation, no network.
        assertEquals(listOf(ProfileAnalytics.CHANNEL_INTEREST_CHANGED), analytics.names())
        val e = analytics.only(ProfileAnalytics.CHANNEL_INTEREST_CHANGED)
        assertEquals("ai_call", e["channel"])
        assertEquals(true, e["on"])
        assertEquals("profile_reachability", e["screen_id"])
        assertTrue(vm.state.value.isInterested(InterestChannel.AiCall))
        assertNull("no dialog", vm.state.value.prompt)
        assertEquals("no consent write", 0, consent.calls)
    }

    @Test
    fun `an interest is never a consent`() {
        // THE TICKET'S HARDEST TRACKING RULE: "no consent_changed for a checkbox. Ever."
        val vm = build()
        vm.arrived()
        analytics.clear()
        InterestChannel.ORDER.forEach { vm.interestToggled(it) }
        InterestChannel.ORDER.forEach { vm.interestToggled(it) }
        assertEquals(0, analytics.count(ProfileAnalytics.CONSENT_CHANGED))
    }

    @Test
    fun `every flip is reported, so a check and an uncheck is not interest`() {
        val vm = build()
        vm.arrived()
        analytics.clear()
        vm.interestToggled(InterestChannel.WhatsApp)
        vm.interestToggled(InterestChannel.WhatsApp)
        assertEquals(2, analytics.count(ProfileAnalytics.CHANNEL_INTEREST_CHANGED))
        assertEquals(false, analytics.events.last().second["on"])
        assertEquals(emptySet<InterestChannel>(), vm.state.value.interest)
    }

    // ── switching push off ──────────────────────────────────────────────────

    @Test
    fun `switching off opens the confirm and does NOT move the toggle`() {
        val vm = build()
        vm.arrived()
        analytics.clear()

        vm.pushChanged(false)

        assertEquals(DeactivationPrompt.UserTurnedItOff, vm.state.value.prompt)
        // THE ACCEPTANCE CRITERION, in one assertion: "the toggle stays on until Confirm
        // deactivation". A confirmation that has already happened is a notification.
        assertTrue("the toggle has not moved", vm.state.value.pushOn)
        assertEquals(
            listOf(ProfileAnalytics.CONSENT_DEACTIVATION_CONFIRM_SHOWN),
            analytics.names(),
        )
        assertEquals("push", analytics.only(ProfileAnalytics.CONSENT_DEACTIVATION_CONFIRM_SHOWN)["channel"])
    }

    @Test
    fun `keep active leaves it on and reports an abandonment`() {
        val vm = build()
        vm.arrived()
        vm.pushChanged(false)
        analytics.clear()

        vm.keepActive()

        assertTrue(vm.state.value.pushOn)
        assertNull(vm.state.value.prompt)
        assertEquals(listOf(ProfileAnalytics.CONSENT_DEACTIVATION_ABANDONED), analytics.names())
    }

    @Test
    fun `confirming turns it off and records the consent change`() {
        val vm = build()
        vm.arrived()
        vm.pushChanged(false)
        analytics.clear()

        vm.confirmDeactivation()

        assertFalse(vm.state.value.pushOn)
        assertNull(vm.state.value.prompt)
        assertEquals(
            listOf(
                ProfileAnalytics.CONSENT_DEACTIVATION_CONFIRMED,
                ProfileAnalytics.CONSENT_CHANGED,
            ),
            analytics.names(),
        )
        val consentEvent = analytics.only(ProfileAnalytics.CONSENT_CHANGED)
        assertEquals("push", consentEvent["channel"])
        assertEquals(false, consentEvent["on"])
        assertEquals("profile_creation", consentEvent["surface"])
    }

    @Test
    fun `switching back on raises no dialog and is immediate`() {
        val vm = build()
        vm.arrived()
        vm.pushChanged(false)
        vm.confirmDeactivation()
        analytics.clear()

        vm.pushChanged(true)

        assertTrue(vm.state.value.pushOn)
        assertNull("no dialog appears when push is switched back on", vm.state.value.prompt)
        assertEquals(listOf(ProfileAnalytics.CONSENT_CHANGED), analytics.names())
        assertEquals(true, analytics.only(ProfileAnalytics.CONSENT_CHANGED)["on"])
    }

    @Test
    fun `switching on against a refusal opens Settings rather than setting a dead toggle`() {
        val vm = build(NotificationPermission.Denied)
        vm.arrived()
        analytics.clear()

        vm.pushChanged(true)

        assertEquals(1, host.settingsOpened)
        assertFalse("the toggle cannot promise what the OS refused", vm.state.value.pushOn)
        assertEquals(listOf(ProfileAnalytics.PERMISSION_SETTINGS_OPENED), analytics.names())
    }

    // ── Save preferences ────────────────────────────────────────────────────

    @Test
    fun `saving with push on and nothing answered raises the dialog, registers, then advances`() =
        runTest(dispatcher) {
            val vm = build(NotificationPermission.NotDetermined)
            vm.arrived()
            analytics.clear()
            host.grants = true

            var advanced = false
            vm.savePressed { advanced = true }
            advanceUntilIdle()

            // THE TICKET'S ORDER, in the order it states it.
            assertEquals(1, host.dialogs)
            assertEquals(1, push.calls)
            assertEquals(1, consent.calls)
            assertEquals(true, consent.lastValue)
            assertTrue(advanced)
            assertEquals(
                listOf(
                    ProfileAnalytics.PERMISSION_OS_SHEET_SHOWN,
                    ProfileAnalytics.PERMISSION_RESULT,
                    ProfileAnalytics.REACHABILITY_SAVED,
                ),
                analytics.names(),
            )
            assertEquals("granted", analytics.only(ProfileAnalytics.PERMISSION_RESULT)["result"])
        }

    @Test
    fun `no dialog when push is off`() = runTest(dispatcher) {
        val vm = build(NotificationPermission.NotDetermined)
        vm.arrived()
        vm.pushChanged(false)
        vm.confirmDeactivation()
        analytics.clear()

        var advanced = false
        vm.savePressed { advanced = true }
        advanceUntilIdle()

        assertEquals("with push off, no dialog appears", 0, host.dialogs)
        assertEquals("and nothing registers", 0, push.calls)
        assertEquals(false, consent.lastValue)
        assertTrue(advanced)
    }

    @Test
    fun `no dialog when the status is already determined`() = runTest(dispatcher) {
        val vm = build(NotificationPermission.Granted)
        vm.arrived()
        analytics.clear()

        var advanced = false
        vm.savePressed { advanced = true }
        advanceUntilIdle()

        assertEquals(0, host.dialogs)
        // Android <= 12 lands here too: no runtime permission, the status reads granted, and the
        // device still needs a token.
        assertEquals(1, push.calls)
        assertTrue(advanced)
    }

    @Test
    fun `a denial shows the confirm with Open Settings and does not advance`() =
        runTest(dispatcher) {
            val vm = build(NotificationPermission.NotDetermined)
            vm.arrived()
            analytics.clear()
            host.grants = false

            var advanced = false
            vm.savePressed { advanced = true }
            advanceUntilIdle()

            assertEquals(DeactivationPrompt.AfterOsDenial, vm.state.value.prompt)
            assertFalse("the save stops: the user has a decision to make", advanced)
            assertEquals(0, consent.calls)
            assertEquals("denied", analytics.only(ProfileAnalytics.PERMISSION_RESULT)["result"])
            assertEquals(1, analytics.count(ProfileAnalytics.CONSENT_DEACTIVATION_CONFIRM_SHOWN))
            // The CTA works again afterwards -- it is never a dead end.
            assertFalse(vm.state.value.saving)
        }

    /**
     * THE HALF OF STATE D THAT THE FIRST BUILD DROPPED.
     *
     * The ticket says it under *What happens after the OS dialog*: "Confirm deactivation -> push
     * off -> **save** -> Location (12)". Confirming in state D is the user finishing the save the
     * OS dialog interrupted, not starting a new one -- and the build that treated both dialogs
     * alike left them on the screen having pressed Save, answered the OS and confirmed the
     * consequence, with nothing saved and no way on but to press Save again.
     */
    @Test
    fun `confirming after a denial finishes the save and advances`() = runTest(dispatcher) {
        val access = MovingAccess(NotificationPermission.NotDetermined)
        val vm = ReachabilityViewModel(
            access = access, push = push, consent = consent, analytics = analytics,
        ).also { it.attach(host) }
        vm.arrived()
        vm.interestToggled(InterestChannel.Sms)
        host.grants = false

        var advanced = false
        vm.savePressed { advanced = true }
        advanceUntilIdle()
        assertEquals(DeactivationPrompt.AfterOsDenial, vm.state.value.prompt)
        assertFalse("not yet -- the dialog is the decision", advanced)
        analytics.clear()

        // The OS answer is on file now, which is what the device would report from here on.
        access.status = NotificationPermission.Denied

        vm.confirmDeactivation { advanced = true }
        advanceUntilIdle()

        assertTrue("state D's confirm IS the save finishing", advanced)
        assertFalse(vm.state.value.pushOn)
        assertEquals("the consent is written once, with push off", 1, consent.calls)
        assertEquals(false, consent.lastValue)

        val saved = analytics.only(ProfileAnalytics.REACHABILITY_SAVED)
        assertEquals(false, saved["push_on"])
        // READ AFTER THE ANSWER, not captured at the tap: the permission the user leaves with is
        // `denied`, and a payload saying `not_determined` would describe a moment that is over.
        assertEquals("denied", saved["permission"])
        assertEquals(listOf("sms"), saved["interest"])
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `confirming a user-initiated switch-off saves nothing and goes nowhere`() =
        runTest(dispatcher) {
            // STATE C, the other half: "Confirm deactivation sets push off, closes the dialog,
            // and the user stays on this screen." They reached for the switch themselves and may
            // well have interest boxes still to tick.
            val vm = build()
            vm.arrived()
            vm.pushChanged(false)
            analytics.clear()

            var advanced = false
            vm.confirmDeactivation { advanced = true }
            advanceUntilIdle()

            assertFalse("nothing was being saved, so nothing finishes", advanced)
            assertEquals("no consent write until Save preferences", 0, consent.calls)
            assertEquals(0, analytics.count(ProfileAnalytics.REACHABILITY_SAVED))
            assertFalse(vm.state.value.pushOn)
            assertNull(vm.state.value.prompt)
        }

    @Test
    fun `dismissing the denial dialog leaves a toggle that tells the truth`() =
        runTest(dispatcher) {
            // A scrim tap on state D is the one path that can leave a switch claiming a permission
            // the DEVICE has refused -- `Keep active` is not even drawn there. The arrival matrix
            // already says denied shows off; this is the same mapping one moment earlier.
            val access = MovingAccess(NotificationPermission.NotDetermined)
            val vm = ReachabilityViewModel(
                access = access, push = push, consent = consent, analytics = analytics,
            ).also { it.attach(host) }
            vm.arrived()
            host.grants = false
            vm.savePressed { }
            advanceUntilIdle()
            access.status = NotificationPermission.Denied

            vm.keepActive()

            assertFalse("the OS refused it, so the switch must not claim it", vm.state.value.pushOn)
            assertNull(vm.state.value.prompt)
            // Still an abandonment, and still only that one event.
            assertEquals(1, analytics.count(ProfileAnalytics.CONSENT_DEACTIVATION_ABANDONED))
        }

    @Test
    fun `Open Settings is not an abandonment`() = runTest(dispatcher) {
        val vm = build(NotificationPermission.NotDetermined)
        vm.arrived()
        host.grants = false
        vm.savePressed { }
        advanceUntilIdle()
        analytics.clear()

        vm.openSettings()

        // THE TICKET NAMES THE DISTINCTION: this is `permission_settings_opened`, and NOT
        // `consent_deactivation_abandoned` -- they answer different questions.
        assertEquals(listOf(ProfileAnalytics.PERMISSION_SETTINGS_OPENED), analytics.names())
        assertEquals(1, host.settingsOpened)
        assertNull(vm.state.value.prompt)
    }

    @Test
    fun `nothing advances until the server confirms`() = runTest(dispatcher) {
        // NOT OPTIMISTIC. On a failure the user stays on 10 with their choices kept, and the CTA
        // works again. The alternative produces accounts past a consent screen with no consent.
        val vm = build(NotificationPermission.Granted)
        vm.arrived()
        vm.interestToggled(InterestChannel.Sms)
        consent.succeeds = false

        var advanced = false
        vm.savePressed { advanced = true }
        advanceUntilIdle()

        assertFalse("the flow must not advance on a failed write", advanced)
        assertTrue("the error is surfaced", vm.state.value.saveFailed)
        assertFalse("and the CTA works again", vm.state.value.saving)
        assertTrue("the choices are kept", vm.state.value.isInterested(InterestChannel.Sms))
        assertEquals(
            "reachability_saved is after the confirmation, never on the tap",
            0, analytics.count(ProfileAnalytics.REACHABILITY_SAVED),
        )
    }

    @Test
    fun `the save carries the final interest, in the drawn order`() = runTest(dispatcher) {
        val vm = build(NotificationPermission.Granted)
        vm.arrived()
        vm.interestToggled(InterestChannel.Sms)
        vm.interestToggled(InterestChannel.AiCall)
        vm.interestToggled(InterestChannel.WhatsApp)
        vm.interestToggled(InterestChannel.WhatsApp)   // and off again
        analytics.clear()

        vm.savePressed { }
        advanceUntilIdle()

        val saved = analytics.only(ProfileAnalytics.REACHABILITY_SAVED)
        assertEquals(true, saved["push_on"])
        assertEquals("granted", saved["permission"])
        // Drawn order, not tap order, so two identical states never serialise two ways.
        assertEquals(listOf("ai_call", "sms"), saved["interest"])
    }

    @Test
    fun `the CTA cannot be tapped twice`() = runTest(dispatcher) {
        val vm = build(NotificationPermission.Granted)
        vm.arrived()

        var advanced = 0
        vm.savePressed { advanced++ }
        vm.savePressed { advanced++ }
        advanceUntilIdle()

        assertEquals("input is locked from the tap until navigation or failure", 1, consent.calls)
        assertEquals(1, advanced)
    }

    // ── the foreground re-read ──────────────────────────────────────────────

    @Test
    fun `coming back from Settings updates the toggle and advances nothing`() {
        val access = MutableAccess(NotificationPermission.Denied)
        val vm = ReachabilityViewModel(access, push, consent, analytics).also { it.attach(host) }
        vm.arrived()
        assertFalse(vm.state.value.pushOn)

        access.status = NotificationPermission.Granted
        vm.foregrounded()

        assertTrue("returning with notifications allowed shows the toggle on", vm.state.value.pushOn)
    }

    @Test
    fun `the reconciler's event is not this screen's to fire`() = runTest(dispatcher) {
        // `permission_status_changed` is for changes made OUTSIDE the app. The answer to our own
        // dialog is `permission_result`, and the two must never both fire for one act.
        val vm = build(NotificationPermission.NotDetermined)
        vm.arrived()
        vm.savePressed { }
        advanceUntilIdle()
        vm.foregrounded()
        assertEquals(0, analytics.count(ProfileAnalytics.PERMISSION_STATUS_CHANGED))
    }

    /** A reader whose answer can change between reads, which is what a foreground re-read is. */
    private class MutableAccess(var status: NotificationPermission) : NotificationAccessReader {
        override fun read(): NotificationPermission = status
    }
}

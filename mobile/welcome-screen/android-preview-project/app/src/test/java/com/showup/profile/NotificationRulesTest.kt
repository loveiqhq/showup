/*
 * NotificationRulesTest.kt
 * ShowUp · the notification ask's rules, with no device and no OS sheet (SHOWUP-162)
 *
 * WHAT THIS CAN PROVE: which statuses skip the screen, what fires and in what order, that a second
 * press does nothing, that both outcomes register or do not, and that the foreground re-read
 * answers correctly.
 *
 * WHAT IT CANNOT: that the platform actually raises a sheet, that the sheet's answer comes back,
 * or that a real FCM token exists. `FixedNotificationAccess` asks no OS and `PushRegistration`'s
 * token source is a stub in this build -- see that file for why.
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRulesTest {

    private class Recorder : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun track(name: String, properties: Map<String, Any>) {
            events += name to properties
        }

        fun names() = events.map { it.first }
        fun count(name: String) = events.count { it.first == name }
        fun only(name: String) = events.single { it.first == name }.second
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

    private val dispatcher = StandardTestDispatcher()
    private lateinit var events: Recorder
    private lateinit var push: FakePush

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        events = Recorder()
        push = FakePush()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun build(status: NotificationPermission = NotificationPermission.NotDetermined) =
        NotificationsViewModel(FixedNotificationAccess(status), push, events)

    // ── which statuses show the ask ─────────────────────────────────────────

    @Test
    fun `only a status with no answer shows the ask`() {
        // The real path, every time, because profile creation runs once on a fresh install.
        assertTrue(shouldShowAsk(NotificationPermission.NotDetermined))

        // The guard: restored from a backup, or killed while the sheet was up. The OS dialog is
        // shown once per install, so the button would be dead.
        assertFalse(shouldShowAsk(NotificationPermission.Granted))
        assertFalse(shouldShowAsk(NotificationPermission.Denied))

        // Parental controls or MDM. Treated as denied everywhere in the product (enums.json §24),
        // which here means determined: there is nothing a sheet could change.
        assertFalse(shouldShowAsk(NotificationPermission.Restricted))
    }

    @Test
    fun `android 12 and below reports granted, which is what skips the screen`() {
        // §24: "Android <= 12 notifications have no runtime permission at all and are reported as
        // granted." NOT not_determined -- that would be a lie that raises a sheet which cannot
        // exist. The API-level branch is the one the ticket says actually fires in production.
        val below = notificationPermissionFor(sdkInt = 32, granted = false, hasAsked = false)
        assertEquals(NotificationPermission.Granted, below)
        assertFalse("an ask with nothing to ask for is the bug", shouldShowAsk(below))

        // And from 33 the three facts decide it, which is the rest of the table.
        assertEquals(
            NotificationPermission.NotDetermined,
            notificationPermissionFor(33, granted = false, hasAsked = false),
        )
        assertEquals(
            NotificationPermission.Denied,
            notificationPermissionFor(33, granted = false, hasAsked = true),
        )
        assertEquals(
            NotificationPermission.Granted,
            notificationPermissionFor(33, granted = true, hasAsked = true),
        )
    }

    // ── what fires, and what must not ───────────────────────────────────────

    @Test
    fun `arriving reports the screen and our own pre-permission surface`() {
        val vm = build()
        vm.arrived()

        assertEquals(
            listOf(ProfileAnalytics.SCREEN_VIEWED, ProfileAnalytics.PERMISSION_PROMPTED),
            events.names(),
        )
        val viewed = events.only(ProfileAnalytics.SCREEN_VIEWED)
        assertEquals("profile_notifications", viewed["screen_id"])
        assertEquals("ProfileNotifications", viewed["screen_name"])
        assertEquals("profile_media", viewed["referrer_screen_id"])
        assertEquals("notifications", events.only(ProfileAnalytics.PERMISSION_PROMPTED)["type"])
    }

    @Test
    fun `arriving twice reports once`() {
        val vm = build()
        vm.arrived()
        vm.arrived()
        assertEquals(1, events.count(ProfileAnalytics.PERMISSION_PROMPTED))
    }

    @Test
    fun `no step event fires on this screen, in any state`() {
        // §2's note, added 21 Sep 2026: a step_id row with a dash index is NOT a licence to fire
        // profile_step_viewed, and this screen has no skip CTA so it fires no step event at all.
        // A phantom step here is invisible until someone reads the completion funnel.
        val vm = build()
        vm.arrived()
        vm.enablePressed()
        vm.answered(granted = true)

        assertTrue(
            "no profile_step_* may fire here: ${events.names()}",
            events.names().none { it.startsWith("profile_step_") },
        )
    }

    @Test
    fun `no recovery, settings or consent event fires here`() {
        // All three belong to Stay reachable (10). `consent_changed` in particular would
        // double-count the same consent from two surfaces -- the OS grant is not our consent.
        val vm = build()
        vm.arrived()
        vm.enablePressed()
        vm.answered(granted = false)

        val forbidden = listOf(
            "permission_denied_recovery_shown", "permission_settings_opened", "consent_changed",
        )
        assertTrue(
            "none of $forbidden may fire here: ${events.names()}",
            events.names().none { it in forbidden },
        )
    }

    // ── the sheet ───────────────────────────────────────────────────────────

    @Test
    fun `pressing enable reports the sheet once and refuses a second press`() {
        val vm = build()
        assertTrue("the first press raises the sheet", vm.enablePressed())
        assertFalse("the second does not", vm.enablePressed())

        assertEquals(
            "a tap that raises no sheet is not a sheet being shown",
            1, events.count(ProfileAnalytics.PERMISSION_OS_SHEET_SHOWN),
        )
        assertEquals("notifications", events.only(ProfileAnalytics.PERMISSION_OS_SHEET_SHOWN)["type"])
    }

    @Test
    fun `granting reports granted and registers for push`() = runTest(dispatcher) {
        val vm = build()
        vm.enablePressed()
        vm.answered(granted = true)
        advanceUntilIdle()

        assertEquals("granted", events.only(ProfileAnalytics.PERMISSION_RESULT)["result"])
        assertEquals(
            "granting and never registering is the silent failure this ticket names",
            1, push.calls,
        )
    }

    @Test
    fun `denying reports denied and registers nothing`() = runTest(dispatcher) {
        val vm = build()
        vm.enablePressed()
        vm.answered(granted = false)
        advanceUntilIdle()

        assertEquals("denied", events.only(ProfileAnalytics.PERMISSION_RESULT)["result"])
        assertEquals(0, push.calls)
    }

    @Test
    fun `the result is never limited`() = runTest(dispatcher) {
        // `granted | denied | limited` is the family's set and `limited` is a photo-library state
        // that cannot occur for notifications. The signature takes a Boolean so there is no third
        // value to pass by mistake; this asserts the mapping.
        listOf(true, false).forEach { granted ->
            // A fresh recorder each time: the same one would hold two results and `only` is the
            // point of the assertion.
            events = Recorder()
            NotificationsViewModel(
                FixedNotificationAccess(NotificationPermission.NotDetermined), push, events,
            ).answered(granted)
            advanceUntilIdle()
            assertTrue(
                events.only(ProfileAnalytics.PERMISSION_RESULT)["result"] in
                    listOf("granted", "denied"),
            )
        }
    }

    // ── the foreground re-read ──────────────────────────────────────────────

    @Test
    fun `a status that became determined while mounted advances the screen`() {
        // The user backgrounds the sheet, turns notifications on in Settings by hand, comes back.
        // The screen is never a terminal state: a dead CTA must be unreachable.
        assertTrue(build(NotificationPermission.Granted).statusIsNowDetermined())
        assertTrue(build(NotificationPermission.Denied).statusIsNowDetermined())
        assertFalse(build(NotificationPermission.NotDetermined).statusIsNowDetermined())
    }

    @Test
    fun `the reconciler's event is not this screen's to fire`() {
        // `permission_status_changed` belongs to the shared reconciler and must never double up
        // with `permission_result` for one act. The foreground re-read here is navigation only.
        val vm = build(NotificationPermission.Granted)
        vm.arrived()
        vm.statusIsNowDetermined()
        assertEquals(0, events.count(ProfileAnalytics.PERMISSION_STATUS_CHANGED))
    }
}

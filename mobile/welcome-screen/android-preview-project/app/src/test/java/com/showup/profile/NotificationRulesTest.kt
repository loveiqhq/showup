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
    fun `arriving reports the screen, and nothing else`() {
        val vm = build()
        vm.arrived()

        // `permission_prompted` USED TO FIRE HERE and no longer does. Registry 1.4.6 moved it:
        // "From 25 Sep 2026 the notifications case is Stay reachable (Profile 10) ... Profile 09
        // NO LONGER fires it -- 09 raises no sheet any more." A pre-permission event on a screen
        // that pre-permissions nothing would double-count the ask against Stay reachable's own.
        assertEquals(listOf(ProfileAnalytics.SCREEN_VIEWED), events.names())
        val viewed = events.only(ProfileAnalytics.SCREEN_VIEWED)
        assertEquals("profile_notifications", viewed["screen_id"])
        assertEquals("ProfileNotifications", viewed["screen_name"])
        assertEquals("profile_media", viewed["referrer_screen_id"])
    }

    @Test
    fun `arriving twice reports once`() {
        val vm = build()
        vm.arrived()
        vm.arrived()
        // The guard is on the screenview now that it is the only thing arrival reports. A
        // remembered `LaunchedEffect(Unit)` does not re-run, but a configuration change
        // recreates the composition and would fire a second one.
        assertEquals(1, events.count(ProfileAnalytics.SCREEN_VIEWED))
    }

    @Test
    fun `no step event fires on this screen, in any state`() {
        // §2's note, added 21 Sep 2026: a step_id row with a dash index is NOT a licence to fire
        // profile_step_viewed, and this screen has no skip CTA so it fires no step event at all.
        // A phantom step here is invisible until someone reads the completion funnel.
        val vm = build()
        vm.arrived()
        vm.continuePressed()

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
        vm.continuePressed()

        val forbidden = listOf(
            "permission_denied_recovery_shown", "permission_settings_opened", "consent_changed",
        )
        assertTrue(
            "none of $forbidden may fire here: ${events.names()}",
            events.names().none { it in forbidden },
        )
    }

    // ── the dialog this screen no longer raises (SHOWUP-163) ────────────────

    /**
     * THE WHOLE OF WHAT 163 TOOK AWAY, asserted as an absence.
     *
     * This screen owned the OS notification dialog until 25 September 2026: `enablePressed`
     * reported `permission_os_sheet_shown` and launched the request, and `answered` reported
     * `permission_result`. All three events and the request moved to Stay reachable, where the
     * dialog is raised by `Save preferences`.
     *
     * Five tests were deleted with them -- they were testing a thing that is now somebody else's,
     * and `ReachabilityRulesTest` is where they live in spirit. What is left is this one, which
     * would fail the moment any of it came back.
     */
    @Test
    fun `no permission event fires on this screen any more`() {
        val vm = build()
        vm.arrived()
        vm.continuePressed()
        vm.continuePressed()

        val moved = listOf(
            ProfileAnalytics.PERMISSION_PROMPTED,
            ProfileAnalytics.PERMISSION_OS_SHEET_SHOWN,
            ProfileAnalytics.PERMISSION_RESULT,
        )
        for (name in moved) {
            assertEquals("$name moved to Stay reachable and must not fire here", 0,
                events.count(name))
        }
        // The screenview stays: this is still a screen somebody looked at.
        assertEquals(1, events.count(ProfileAnalytics.SCREEN_VIEWED))
    }

    @Test
    fun `continue navigates once and refuses a second press`() {
        val vm = build()
        assertTrue("the first press navigates", vm.continuePressed())
        assertFalse("a double tap must not produce two navigations", vm.continuePressed())
        // And nothing is reported for either -- the press is not an event on this screen.
        assertEquals(emptyList<String>(), events.names())
    }

    @Test
    fun `the CTA reads Continue, not Enable notifications`() {
        // 163 renames it, because the button no longer enables anything -- it goes to the screen
        // that does. A label describing the screen after it is the kind of thing a copy pass
        // quietly reverts.
        assertEquals("Continue", NotificationsCopy.CTA)
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

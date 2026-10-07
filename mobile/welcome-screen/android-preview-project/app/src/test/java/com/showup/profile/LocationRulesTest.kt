/*
 * LocationRulesTest.kt
 * ShowUp · the location ask's rules, with no device and no permission (SHOWUP-165)
 *
 * The arrival matrix, the Android permission mapping, and the view model: every tracking row, the
 * position advancing AFTER the answer and only on a grant or `Not now`, one act producing one
 * event, and the foreground reconciler doing nothing while our own dialog is up.
 */
package com.showup.profile

import com.showup.analytics.RecordingAnalytics
import com.showup.api.generated.model.FlowPosition
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
class LocationRulesTest {

    // ── the matrix ──────────────────────────────────────────────────────────────

    private fun status(p: PermissionStatus, on: Boolean = true) = LocationStatus(p, servicesOn = on)

    @Test
    fun `the phone's switch off is C whatever the permission says -- checked first`() {
        PermissionStatus.entries.forEach {
            assertEquals("$it", LocationArrival.Show(LocationState.ServicesOff), locationArrival(status(it, on = false)))
        }
    }

    @Test
    fun `not determined is A, granted is skipped, denied and restricted are B`() {
        assertEquals(LocationArrival.Show(LocationState.Ask), locationArrival(status(PermissionStatus.NotDetermined)))
        assertEquals(LocationArrival.Skip, locationArrival(status(PermissionStatus.Granted)))
        assertEquals(LocationArrival.Show(LocationState.Denied), locationArrival(status(PermissionStatus.Denied)))
        assertEquals(LocationArrival.Show(LocationState.Denied), locationArrival(status(PermissionStatus.Restricted)))
    }

    @Test
    fun `B and C carry their section 26 reasons and A carries none`() {
        assertNull(LocationState.Ask.unavailableReason)
        assertEquals("denied", LocationState.Denied.unavailableReason?.trackingValue)
        assertEquals("services_off", LocationState.ServicesOff.unavailableReason?.trackingValue)
    }

    @Test
    fun `either precision is a grant, and asked-but-refused is denied`() {
        assertEquals(PermissionStatus.Granted, locationPermissionFor(fine = true, coarse = true, hasAsked = true))
        assertEquals(PermissionStatus.Granted, locationPermissionFor(fine = false, coarse = true, hasAsked = true))
        assertEquals(PermissionStatus.Denied, locationPermissionFor(fine = false, coarse = false, hasAsked = true))
        assertEquals(
            PermissionStatus.NotDetermined,
            locationPermissionFor(fine = false, coarse = false, hasAsked = false),
        )
    }

    @Test
    fun `only while-in-use permissions are ever requested`() {
        assertEquals(
            listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
            LOCATION_PERMISSIONS.toList(),
        )
        assertFalse(LOCATION_PERMISSIONS.any { it.contains("BACKGROUND") })
    }

    // ── the view model ─────────────────────────────────────────────────────────

    private class Positions : FlowPositionSink {
        val reported = mutableListOf<FlowPosition>()
        override suspend fun report(position: FlowPosition): Boolean {
            reported += position
            return true
        }
    }

    private class FakeHost : LocationHost {
        var dialogs = 0
        val settings = mutableListOf<LocationState>()
        override fun requestLocationPermission() {
            dialogs++
        }

        override fun openSettings(state: LocationState) {
            settings += state
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var access: FixedLocationAccess
    private lateinit var positions: Positions
    private lateinit var analytics: RecordingAnalytics
    private lateinit var host: FakeHost
    private lateinit var model: LocationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        access = FixedLocationAccess()
        positions = Positions()
        analytics = RecordingAnalytics()
        host = FakeHost()
        model = LocationViewModel(access, positions, analytics).also { it.attach(host) }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The phone in the state [shown] reads as, then the screen's arrival -- which reads it. */
    private fun arrive(shown: LocationState, referrer: ProfileScreen? = ProfileScreen.Reachability) {
        access.status = when (shown) {
            LocationState.Ask -> LocationStatus(PermissionStatus.NotDetermined, servicesOn = true)
            LocationState.Denied -> LocationStatus(PermissionStatus.Denied, servicesOn = true)
            LocationState.ServicesOff -> LocationStatus(PermissionStatus.NotDetermined, servicesOn = false)
        }
        model.arrived(referrer) { error("$shown must not advance on arrival") }
    }

    @Test
    fun `A fires screen_viewed and permission_prompted location, once per mount`() = runTest(dispatcher) {
        arrive(LocationState.Ask)
        arrive(LocationState.Ask)
        assertEquals(listOf("screen_viewed", "permission_prompted"), analytics.names())
        val view = analytics.of("screen_viewed").single().properties
        assertEquals("profile_location", view["screen_id"])
        assertEquals("ProfileLocation", view["screen_name"])
        assertEquals("profile_reachability", view["referrer_screen_id"])
        assertEquals("location", analytics.of("permission_prompted").single().properties["type"])
    }

    @Test
    fun `B fires the recovery event with reason denied, and never permission_prompted`() = runTest(dispatcher) {
        arrive(LocationState.Denied)
        assertEquals(listOf("screen_viewed", "permission_denied_recovery_shown"), analytics.names())
        val recovery = analytics.of("permission_denied_recovery_shown").single().properties
        assertEquals("location", recovery["type"])
        assertEquals("denied", recovery["reason"])
    }

    @Test
    fun `C fires the recovery event with reason services_off`() = runTest(dispatcher) {
        arrive(LocationState.ServicesOff)
        assertEquals("services_off", analytics.of("permission_denied_recovery_shown").single().properties["reason"])
    }

    @Test
    fun `Allow raises the dialog and nothing else, and cannot raise it twice`() = runTest(dispatcher) {
        arrive(LocationState.Ask)
        analytics.clear()
        model.allowPressed()
        model.allowPressed()
        assertEquals(1, host.dialogs)
        assertEquals(listOf("permission_os_sheet_shown"), analytics.names())
        assertTrue(model.state.value.requesting)
    }

    @Test
    fun `granted at any precision records the position AFTER the answer and advances`() = runTest(dispatcher) {
        arrive(LocationState.Ask)
        model.allowPressed()
        advanceUntilIdle()
        assertTrue("nothing recorded while the dialog is up", positions.reported.isEmpty())
        var advanced = false
        model.permissionAnswered(granted = true) { advanced = true }
        advanceUntilIdle()
        assertTrue(advanced)
        assertEquals(listOf(FlowPosition.location), positions.reported)
        val result = analytics.of("permission_result").single().properties
        assertEquals("granted", result["result"])
        assertEquals("location", result["type"])
    }

    @Test
    fun `denied shows B in place, records NOTHING, and so a relaunch lands on B`() = runTest(dispatcher) {
        arrive(LocationState.Ask)
        model.allowPressed()
        var advanced = false
        model.permissionAnswered(granted = false) { advanced = true }
        advanceUntilIdle()
        assertFalse(advanced)
        assertEquals(LocationState.Denied, model.state.value.state)
        assertFalse(model.state.value.requesting)
        assertTrue("the position stays at Stay reachable", positions.reported.isEmpty())
        assertEquals("denied", analytics.of("permission_result").single().properties["result"])
        assertEquals("denied", analytics.of("permission_denied_recovery_shown").single().properties["reason"])
    }

    @Test
    fun `Open Settings opens the page for the state and stays`() = runTest(dispatcher) {
        arrive(LocationState.ServicesOff)
        analytics.clear()
        model.openSettingsPressed()
        assertEquals(listOf(LocationState.ServicesOff), host.settings)
        assertEquals(listOf("permission_settings_opened"), analytics.names())
        assertEquals(LocationState.ServicesOff, model.state.value.state)
    }

    @Test
    fun `Not now records the position, fires profile_step_skipped location, and advances`() = runTest(dispatcher) {
        arrive(LocationState.Denied)
        analytics.clear()
        var advanced = false
        model.notNowPressed { advanced = true }
        advanceUntilIdle()
        assertTrue(advanced)
        assertEquals(listOf(FlowPosition.location), positions.reported)
        val skipped = analytics.of("profile_step_skipped").single().properties
        assertEquals("location", skipped["step_id"])
        assertEquals("profile_location", skipped["screen_id"])
    }

    @Test
    fun `A has no Not now equivalent`() = runTest(dispatcher) {
        arrive(LocationState.Ask)
        var advanced = false
        model.notNowPressed { advanced = true }
        assertFalse(advanced)
    }

    @Test
    fun `a grant in Settings advances silently on return -- no permission_result`() = runTest(dispatcher) {
        arrive(LocationState.Denied)
        analytics.clear()
        access.status = LocationStatus(PermissionStatus.Granted, servicesOn = true)
        var advanced = false
        model.foregrounded { advanced = true }
        advanceUntilIdle()
        assertTrue(advanced)
        assertTrue(analytics.of("permission_result").isEmpty())
        assertEquals(listOf(FlowPosition.location), positions.reported)
    }

    @Test
    fun `turning Location on from C shows A, and A announces itself then`() = runTest(dispatcher) {
        arrive(LocationState.ServicesOff)
        analytics.clear()
        access.status = LocationStatus(PermissionStatus.NotDetermined, servicesOn = true)
        model.foregrounded {}
        assertEquals(LocationState.Ask, model.state.value.state)
        assertEquals(listOf("permission_prompted"), analytics.names())
    }

    @Test
    fun `while our dialog is up the reconciler does nothing -- one act, one event`() = runTest(dispatcher) {
        arrive(LocationState.Ask)
        model.allowPressed()
        access.status = LocationStatus(PermissionStatus.Granted, servicesOn = true)
        var advanced = false
        model.foregrounded { advanced = true }
        assertFalse(advanced)
        assertTrue(positions.reported.isEmpty())
    }

    @Test
    fun `a grant on arrival is a silent skip that still records the position`() = runTest(dispatcher) {
        access.status = LocationStatus(PermissionStatus.Granted, servicesOn = true)
        assertEquals(LocationArrival.Skip, model.decideArrival())
        advanceUntilIdle()
        assertTrue("nothing about the screen is reported", analytics.all().isEmpty())
        assertEquals(listOf(FlowPosition.location), positions.reported)
    }

    @Test
    fun `the answer after a process death is still accepted on A`() = runTest(dispatcher) {
        // A fresh view model that never saw the press.
        arrive(LocationState.Ask)
        var advanced = false
        model.permissionAnswered(granted = true) { advanced = true }
        assertTrue(advanced)
    }

    @Test
    fun `a grant made while the app was gone is a silent skip on arrival -- nothing reported`() =
        runTest(dispatcher) {
            // Restored after the system ended the process, with the permission granted in Settings
            // meanwhile: the arrival reads it, records the position and advances, and reports no view.
            access.status = LocationStatus(PermissionStatus.Granted, servicesOn = true)
            var advanced = false
            model.arrived(ProfileScreen.Reachability) { advanced = true }
            advanceUntilIdle()
            assertTrue(advanced)
            assertTrue(analytics.all().isEmpty())
            assertEquals(listOf(FlowPosition.location), positions.reported)
        }

    @Test
    fun `the arrival shows what the phone says, not what the push decided`() = runTest(dispatcher) {
        // Denied in place and then restored: the arrival reads denied and shows B, announcing B.
        arrive(LocationState.Denied)
        assertEquals(LocationState.Denied, model.state.value.state)
        assertEquals(listOf("screen_viewed", "permission_denied_recovery_shown"), analytics.names())
    }

    @Test
    fun `a Deny delivered before the screen mounted is kept, and announced once`() = runTest(dispatcher) {
        // Process death with the dialog up: AndroidX hands the answer to the rebuilt launcher before
        // the screen's arrival runs. The status read would still say "not determined" only if the
        // ask was never logged; the answer wins either way.
        access.status = LocationStatus(PermissionStatus.NotDetermined, servicesOn = true)
        model.permissionAnswered(granted = false) { error("a denial does not advance") }
        model.arrived(ProfileScreen.Reachability) { error("a denial does not advance") }
        assertEquals(LocationState.Denied, model.state.value.state)
        assertEquals(1, analytics.of("permission_denied_recovery_shown").size)
        assertTrue(analytics.of("permission_prompted").isEmpty())
    }

    @Test
    fun `a grant delivered before the screen mounted leaves once`() = runTest(dispatcher) {
        // Process death or a rebuild with the dialog up: the grant reaches the launcher during the
        // first composition, and the screen's arrival runs in that same pass.
        access.status = LocationStatus(PermissionStatus.Granted, servicesOn = true)
        var advances = 0
        model.permissionAnswered(granted = true) { advances++ }
        model.arrived(ProfileScreen.Reachability) { advances++ }
        advanceUntilIdle()
        assertEquals(1, advances)
        assertEquals(listOf(FlowPosition.location), positions.reported)
        assertTrue(analytics.of("screen_viewed").isEmpty())
    }

    @Test
    fun `a cancelled request is not a denial -- A keeps a working button`() = runTest(dispatcher) {
        arrive(LocationState.Ask)
        model.allowPressed()
        model.permissionCancelled()
        assertEquals(LocationState.Ask, model.state.value.state)
        assertFalse(model.state.value.requesting)
        assertTrue(analytics.of("permission_result").isEmpty())
        model.allowPressed()
        assertEquals(2, host.dialogs)
    }

    @Test
    fun `taps on the outgoing screen after leaving do nothing`() = runTest(dispatcher) {
        arrive(LocationState.Denied)
        var advances = 0
        model.notNowPressed { advances++ }
        model.left()
        model.notNowPressed { advances++ }
        advanceUntilIdle()
        assertEquals(1, advances)
        assertEquals(listOf(FlowPosition.location), positions.reported)
    }

    @Test
    fun `a detached host is not called`() = runTest(dispatcher) {
        arrive(LocationState.Denied)
        model.detach(host)
        model.openSettingsPressed()
        assertTrue(host.settings.isEmpty())
    }

    @Test
    fun `nothing that must not fire here ever does`() = runTest(dispatcher) {
        arrive(LocationState.Ask)
        model.allowPressed()
        model.permissionAnswered(false) {}
        model.openSettingsPressed()
        model.notNowPressed {}
        advanceUntilIdle()
        val forbidden = setOf(
            "profile_step_viewed", "profile_step_completed", "location_gate_shown", "consent_changed",
        )
        assertTrue(analytics.names().none { it in forbidden })
    }
}

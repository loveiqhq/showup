package com.showup.search

import com.showup.analytics.RecordingAnalytics
import com.showup.profile.FixedLocationAccess
import com.showup.profile.LocationHost
import com.showup.profile.LocationState
import com.showup.profile.LocationStatus
import com.showup.profile.PermissionStatus
import com.showup.profile.ProfileAnalytics
import com.showup.profile.ProfileScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Search location gate's rules (SHOWUP-188): the state table, the events the ticket names and
 * the ones it forbids, A's dialog, B and C's Settings routing, the foreground re-read, and D.
 */
class LocationGateViewModelTest {

    private class FakeHost : LocationHost {
        var requests = 0
        val settings = mutableListOf<LocationState>()
        override fun requestLocationPermission() { requests++ }
        override fun openSettings(state: LocationState) { settings += state }
    }

    private lateinit var access: FixedLocationAccess
    private lateinit var analytics: RecordingAnalytics
    private lateinit var host: FakeHost

    @Before
    fun setUp() {
        access = FixedLocationAccess()
        analytics = RecordingAnalytics()
        host = FakeHost()
    }

    private fun phone(permission: PermissionStatus, servicesOn: Boolean = true) {
        access.status = LocationStatus(permission, servicesOn)
    }

    private fun gate() = LocationGateViewModel(access, analytics).also { it.attach(host) }

    private fun props(event: String, nth: Int = 0) = analytics.of(event)[nth].properties

    /** Never on the gate: one event per act, and this is not sign-up. */
    private fun assertNoForbidden() {
        listOf(
            ProfileAnalytics.PERMISSION_PROMPTED, ProfileAnalytics.PERMISSION_DENIED_RECOVERY_SHOWN,
            "profile_step_skipped", "profile_step_viewed", "profile_step_completed",
        ).forEach { assertTrue("$it must not fire on the gate", analytics.of(it).isEmpty()) }
    }

    // ── the state table, decided before the tab renders ─────────────────────────────────────────

    @Test
    fun `the state is read when the gate is made -- services off first, then denied, then never asked`() {
        PermissionStatus.entries.forEach {
            phone(it, servicesOn = false)
            assertEquals("$it with the phone's Location off", LocationState.ServicesOff, gate().state.value.blocked)
        }
        phone(PermissionStatus.Denied); assertEquals(LocationState.Denied, gate().state.value.blocked)
        phone(PermissionStatus.Restricted); assertEquals("restricted is denied (§24)", LocationState.Denied, gate().state.value.blocked)
        phone(PermissionStatus.NotDetermined); assertEquals(LocationState.Ask, gate().state.value.blocked)
        phone(PermissionStatus.Granted); assertNull("granted, any precision: Search, no gate", gate().state.value.blocked)
    }

    // ── what it reports ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a mount reports screen_viewed once and the state once, with its section 26 reason`() {
        phone(PermissionStatus.NotDetermined)
        val g = gate()
        g.mounted(ProfileScreen.LikesReceived)
        g.mounted(ProfileScreen.LikesReceived)
        assertEquals(listOf("screen_viewed", ProfileAnalytics.LOCATION_GATE_SHOWN), analytics.names())
        assertEquals("search_location_gate", props("screen_viewed")["screen_id"])
        assertEquals("Search - Location gate", props("screen_viewed")["screen_name"])
        assertEquals("likes_received", props("screen_viewed")["referrer_screen_id"])
        assertEquals("not_determined", props(ProfileAnalytics.LOCATION_GATE_SHOWN)["reason"])
        assertNoForbidden()
    }

    @Test
    fun `a foreground reports the state again only when its reason changed`() {
        phone(PermissionStatus.Denied, servicesOn = false)
        val g = gate()
        g.mounted(null)
        g.refresh()
        assertEquals("not on every foreground", 1, analytics.of(ProfileAnalytics.LOCATION_GATE_SHOWN).size)
        // The phone's Location turned on, the app still denied: C -> B, in place.
        phone(PermissionStatus.Denied)
        g.refresh()
        assertEquals(LocationState.Denied, g.state.value.blocked)
        assertEquals(listOf("services_off", "denied"), analytics.of(ProfileAnalytics.LOCATION_GATE_SHOWN).map { it.properties["reason"] })
        assertEquals("one screen_viewed per mount, never per state", 1, analytics.of("screen_viewed").size)
    }

    @Test
    fun `left and mounted again is a new showing`() {
        phone(PermissionStatus.Denied)
        val g = gate()
        g.mounted(null)
        g.left()
        g.mounted(ProfileScreen.LikesReceived)
        assertEquals(2, analytics.of("screen_viewed").size)
        assertEquals(2, analytics.of(ProfileAnalytics.LOCATION_GATE_SHOWN).size)
    }

    // ── A: the dialog ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `Allow raises the dialog and nothing else, and cannot raise it twice`() {
        phone(PermissionStatus.NotDetermined)
        val g = gate()
        g.mounted(null)
        analytics.clear()
        g.allowPressed()
        g.allowPressed()
        assertEquals(1, host.requests)
        assertTrue(g.state.value.requesting)
        assertEquals(listOf(ProfileAnalytics.PERMISSION_OS_SHEET_SHOWN), analytics.names())
        assertEquals("location", props(ProfileAnalytics.PERMISSION_OS_SHEET_SHOWN)["type"])
        assertEquals("search_location_gate", props(ProfileAnalytics.PERMISSION_OS_SHEET_SHOWN)["screen_id"])
    }

    @Test
    fun `granted lifts the gate in place, and the Search that loads names it as the referrer once`() {
        phone(PermissionStatus.NotDetermined)
        val g = gate()
        g.mounted(null)
        g.allowPressed()
        phone(PermissionStatus.Granted)
        g.permissionAnswered(granted = true)
        assertNull(g.state.value.blocked)
        assertFalse(g.state.value.requesting)
        assertEquals("granted", props(ProfileAnalytics.PERMISSION_RESULT)["result"])
        assertEquals("search_location_gate", props(ProfileAnalytics.PERMISSION_RESULT)["screen_id"])
        assertTrue(g.takeLift())
        assertFalse("asked once", g.takeLift())
        assertNoForbidden()
    }

    @Test
    fun `denied shows B in place, reported as the new state`() {
        phone(PermissionStatus.NotDetermined)
        val g = gate()
        g.mounted(null)
        g.allowPressed()
        phone(PermissionStatus.Denied)
        g.permissionAnswered(granted = false)
        assertEquals(LocationState.Denied, g.state.value.blocked)
        assertEquals("denied", props(ProfileAnalytics.PERMISSION_RESULT)["result"])
        assertEquals(listOf("not_determined", "denied"), analytics.of(ProfileAnalytics.LOCATION_GATE_SHOWN).map { it.properties["reason"] })
        assertFalse(g.takeLift())
    }

    @Test
    fun `a grant with the phone's Location off still blocks, as C`() {
        phone(PermissionStatus.NotDetermined)
        val g = gate()
        g.mounted(null)
        g.allowPressed()
        phone(PermissionStatus.Granted, servicesOn = false)
        g.permissionAnswered(granted = true)
        assertEquals(LocationState.ServicesOff, g.state.value.blocked)
    }

    @Test
    fun `while our dialog is up the foreground re-read does nothing -- one act, one event`() {
        phone(PermissionStatus.NotDetermined)
        val g = gate()
        g.mounted(null)
        g.allowPressed()
        phone(PermissionStatus.Granted)
        g.refresh()
        assertEquals("the dialog's answer decides", LocationState.Ask, g.state.value.blocked)
        g.permissionAnswered(granted = true)
        assertNull(g.state.value.blocked)
        assertEquals(1, analytics.of(ProfileAnalytics.PERMISSION_RESULT).size)
    }

    @Test
    fun `a cancelled request is no answer -- A keeps a working button`() {
        phone(PermissionStatus.NotDetermined)
        val g = gate()
        g.mounted(null)
        g.allowPressed()
        g.permissionCancelled()
        assertFalse(g.state.value.requesting)
        assertEquals(LocationState.Ask, g.state.value.blocked)
        g.allowPressed()
        assertEquals(2, host.requests)
        assertTrue(analytics.of(ProfileAnalytics.PERMISSION_RESULT).isEmpty())
    }

    // ── B and C: Settings, and the way back ───────────────────────────────────────────────────

    @Test
    fun `Open Settings routes per state and stays`() {
        phone(PermissionStatus.Denied)
        val g = gate()
        g.mounted(null)
        g.openSettingsPressed()
        phone(PermissionStatus.Denied, servicesOn = false)
        g.refresh()
        g.openSettingsPressed()
        assertEquals(listOf(LocationState.Denied, LocationState.ServicesOff), host.settings)
        assertEquals(LocationState.ServicesOff, g.state.value.blocked)
        assertEquals(2, analytics.of(ProfileAnalytics.PERMISSION_SETTINGS_OPENED).size)
        assertEquals("search_location_gate", props(ProfileAnalytics.PERMISSION_SETTINGS_OPENED)["screen_id"])
        assertEquals("location", props(ProfileAnalytics.PERMISSION_SETTINGS_OPENED)["type"])
    }

    @Test
    fun `A has no Settings and B and C have no dialog`() {
        phone(PermissionStatus.NotDetermined)
        val a = gate().also { it.mounted(null) }
        a.openSettingsPressed()
        phone(PermissionStatus.Denied)
        val b = gate().also { it.mounted(null) }
        b.allowPressed()
        assertTrue(host.settings.isEmpty())
        assertEquals(0, host.requests)
    }

    @Test
    fun `allowed in Settings, the return lifts the gate with no tap and no permission_result`() {
        phone(PermissionStatus.Denied)
        val g = gate()
        g.mounted(null)
        g.openSettingsPressed()
        phone(PermissionStatus.Granted)
        g.refresh()
        assertNull(g.state.value.blocked)
        assertTrue("Search loads, reached from the gate", g.takeLift())
        assertTrue("a grant noticed on return is the reconciler's", analytics.of(ProfileAnalytics.PERMISSION_RESULT).isEmpty())
    }

    @Test
    fun `the status follows the phone while the gate is not on screen, and reports nothing`() {
        phone(PermissionStatus.Granted)
        val g = gate()
        phone(PermissionStatus.Denied)
        g.refresh()
        assertEquals(LocationState.Denied, g.state.value.blocked)
        phone(PermissionStatus.Granted)
        g.refresh()
        assertNull(g.state.value.blocked)
        assertFalse("not a lift of a gate that was never up", g.takeLift())
        assertTrue(analytics.all().isEmpty())
    }

    // ── D: Confirm date on a profile opened from the Likes tab ────────────────────────────────

    @Test
    fun `D -- with location usable, Confirm date goes on to Be sure`() {
        phone(PermissionStatus.Granted)
        val g = gate()
        assertFalse(g.confirmNeedsLocation())
        assertFalse(g.state.value.overProfile)
    }

    @Test
    fun `D -- without location the gate goes over the profile, and back returns to it`() {
        phone(PermissionStatus.Denied)
        val g = gate()
        assertTrue(g.confirmNeedsLocation())
        assertTrue(g.state.value.overProfile)
        g.mounted(ProfileScreen.SearchProfile)
        assertEquals("search_profile", props("screen_viewed")["referrer_screen_id"])
        g.backToProfile()
        assertFalse(g.state.value.overProfile)
        assertEquals("still blocked: back is not a grant", LocationState.Denied, g.state.value.blocked)
    }

    @Test
    fun `D -- the location turned on lifts the gate back to the profile, and nothing is confirmed`() {
        phone(PermissionStatus.Denied)
        val g = gate()
        g.confirmNeedsLocation()
        g.mounted(ProfileScreen.SearchProfile)
        g.openSettingsPressed()
        phone(PermissionStatus.Granted)
        g.refresh()
        assertFalse("back on the profile", g.state.value.overProfile)
        assertNull(g.state.value.blocked)
        assertFalse("no Search arrival from D's lift", g.takeLift())
    }

    @Test
    fun `D re-reads the status when Confirm date is tapped`() {
        phone(PermissionStatus.Granted)
        val g = gate()
        phone(PermissionStatus.Denied, servicesOn = false)
        assertTrue(g.confirmNeedsLocation())
        assertEquals(LocationState.ServicesOff, g.state.value.blocked)
    }
}

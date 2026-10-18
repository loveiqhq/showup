/*
 * PermissionReconcilerTest.kt
 * ShowUp · the shared permission reconciler's rules, with no device (epic SHOWUP-151, SHOWUP-163)
 *
 * Every rule the epic states: a starting point reports nothing, a change made outside the app fires
 * once with what it was and what it is, the first start is the launch and every later one a
 * foreground, our own dialog's answer never fires it, and a notifications change moves the push
 * token. Plus the snapshot: built from the screens' own readers, mapped onto §24.
 */
package com.showup.profile

import com.showup.analytics.RecordingAnalytics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionReconcilerTest {

    private class CountingPush : PushTokenSync {
        var registered = 0
        var unregistered = 0
        override suspend fun register() {
            registered++
        }
        override suspend fun unregister() {
            unregistered++
        }
    }

    /** Launch on the first start, foreground after -- as the process-wide mark does. */
    private class OneLaunch : LaunchMark {
        private var seen = false
        override fun firstStart(): Boolean = !seen.also { seen = true }
    }

    private val all = WatchedPermission.entries
    private lateinit var device: MutableMap<WatchedPermission, PermissionStatus>
    private lateinit var ledger: InMemoryPermissionLedger
    private lateinit var analytics: RecordingAnalytics
    private lateinit var push: CountingPush

    @Before
    fun setUp() {
        device = all.associateWith { PermissionStatus.NotDetermined }.toMutableMap()
        ledger = InMemoryPermissionLedger()
        analytics = RecordingAnalytics()
        push = CountingPush()
    }

    private fun TestScope.reconciler() = PermissionReconciler(
        snapshot = { device.toMap() },
        ledger = ledger,
        push = push,
        analytics = analytics,
        scope = this,
        launchMark = OneLaunch(),
    )

    private fun changes() = analytics.of(ProfileAnalytics.PERMISSION_STATUS_CHANGED).map { it.properties }

    @Test
    fun `the first start records a starting point and reports nothing`() = runTest {
        device[WatchedPermission.Location] = PermissionStatus.Granted
        val r = reconciler()
        assertTrue(r.onStart(inFlow = false).isEmpty())
        assertTrue(changes().isEmpty())
        assertEquals(PermissionStatus.Granted, ledger.last(WatchedPermission.Location))
        assertEquals("every watched permission has a starting point", all.size, all.count { ledger.last(it) != null })
    }

    @Test
    fun `a change made outside the app fires once, with what it was and what it is`() = runTest {
        device[WatchedPermission.Notifications] = PermissionStatus.Granted
        val r = reconciler()
        r.onStart(inFlow = false)                                             // launch: the starting point
        device[WatchedPermission.Notifications] = PermissionStatus.Denied     // turned off in Settings
        r.onStart(inFlow = false)
        r.onStart(inFlow = false)                                             // nothing new since
        val fired = changes()
        assertEquals(1, fired.size)
        assertEquals("notifications", fired[0]["type"])
        assertEquals("granted", fired[0]["from"])
        assertEquals("denied", fired[0]["to"])
        assertEquals("foreground", fired[0]["detected_on"])
        assertEquals(false, fired[0]["in_flow"])
    }

    @Test
    fun `a change found at launch says launch, and one inside profile creation says so`() = runTest {
        // A change made while the app was not running: recorded by an earlier process.
        ledger.record(WatchedPermission.Camera, PermissionStatus.Granted)
        device[WatchedPermission.Camera] = PermissionStatus.Denied
        reconciler().onStart(inFlow = true)
        val fired = changes().single()
        assertEquals("camera", fired["type"])
        assertEquals("launch", fired["detected_on"])
        assertEquals(true, fired["in_flow"])
    }

    @Test
    fun `our own dialog's answer is absorbed and never fires the event`() = runTest {
        val r = reconciler()
        r.onStart(inFlow = true)
        device[WatchedPermission.Location] = PermissionStatus.Granted         // the user answered OUR sheet
        r.absorbOwnAnswer()
        r.onStart(inFlow = true)
        assertTrue("that act is permission_result's", changes().isEmpty())
        assertEquals(PermissionStatus.Granted, ledger.last(WatchedPermission.Location))
    }

    @Test
    fun `a notifications grant registers the push token and a refusal removes it`() = runTest {
        val r = reconciler()
        r.onStart(inFlow = false)
        device[WatchedPermission.Notifications] = PermissionStatus.Granted
        r.onStart(inFlow = false)
        advanceUntilIdle()
        assertEquals(1, push.registered)
        device[WatchedPermission.Notifications] = PermissionStatus.Denied
        r.onStart(inFlow = false)
        advanceUntilIdle()
        assertEquals(1, push.unregistered)
        device[WatchedPermission.Notifications] = PermissionStatus.Restricted
        r.onStart(inFlow = false)
        advanceUntilIdle()
        assertEquals("restricted is treated as denied", 2, push.unregistered)
    }

    @Test
    fun `only notifications move the push token`() = runTest {
        val r = reconciler()
        r.onStart(inFlow = false)
        device[WatchedPermission.Location] = PermissionStatus.Granted
        device[WatchedPermission.Camera] = PermissionStatus.Denied
        device[WatchedPermission.Microphone] = PermissionStatus.Granted
        r.onStart(inFlow = false)
        advanceUntilIdle()
        assertEquals(0, push.registered)
        assertEquals(0, push.unregistered)
        assertEquals("each change is its own event", setOf("location", "camera", "microphone"), changes().map { it["type"] }.toSet())
    }

    @Test
    fun `the snapshot reads the screens' own readers and maps capture onto section 24`() {
        val snapshot = permissionSnapshot(
            notifications = FixedNotificationAccess(PermissionStatus.Granted),
            // The device-wide switch is off, the grant stands: §24 is about the grant.
            location = FixedLocationAccess(LocationStatus(PermissionStatus.Granted, servicesOn = false)),
            media = FixedMediaAccess(MediaAccess(camera = MediaPermission.CanAsk, microphone = MediaPermission.Blocked)),
        ).read()
        assertEquals(PermissionStatus.Granted, snapshot[WatchedPermission.Notifications])
        assertEquals(PermissionStatus.Granted, snapshot[WatchedPermission.Location])
        assertEquals(PermissionStatus.Denied, snapshot[WatchedPermission.Camera])
        assertEquals(PermissionStatus.Denied, snapshot[WatchedPermission.Microphone])
        assertEquals(PermissionStatus.NotDetermined, MediaPermission.NotDetermined.asPermissionStatus())
        assertEquals(PermissionStatus.Granted, MediaPermission.Granted.asPermissionStatus())
    }

    @Test
    fun `the process launch mark is true exactly once`() {
        // A fresh object per test is not possible for a process-wide one, so this reads it twice
        // after whatever ran before: whatever the first answer was, the second must be false.
        ProcessLaunchMark.firstStart()
        assertEquals(false, ProcessLaunchMark.firstStart())
    }
}

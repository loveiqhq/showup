/*
 * PermissionReconcilerTest.kt
 * ShowUp · the shared permission reconciler's rules, with no device (epic SHOWUP-151, SHOWUP-163)
 *
 * Every rule the epic states: a starting point reports nothing, a change made outside the app fires
 * once with what it was and what it is, the first start is the launch and every later one a
 * foreground, our own dialog's answer never fires it, and a notifications change moves the push
 * token. Plus the snapshot: built from the screens' own readers, mapped onto §24. And the server:
 * every status goes to it when it moves, a refused send is tried again, a new sign-in sends again.
 */
package com.showup.profile

import com.showup.analytics.RecordingAnalytics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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

    /** Records every send; answers with [accepts], so a test can make the server refuse. */
    private class RecordingServer : PermissionStatusSync {
        val sent = mutableListOf<Map<WatchedPermission, PermissionStatus>>()
        var accepts = true
        override suspend fun save(statuses: Map<WatchedPermission, PermissionStatus>): Boolean {
            sent += statuses
            return accepts
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
    private lateinit var server: RecordingServer

    @Before
    fun setUp() {
        device = all.associateWith { PermissionStatus.NotDetermined }.toMutableMap()
        ledger = InMemoryPermissionLedger()
        analytics = RecordingAnalytics()
        push = CountingPush()
        server = RecordingServer()
    }

    private fun TestScope.reconciler() = PermissionReconciler(
        snapshot = { device.toMap() },
        ledger = ledger,
        push = push,
        server = server,
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
    fun `the launch sends every status once, and a start with nothing new sends nothing`() = runTest {
        device[WatchedPermission.Notifications] = PermissionStatus.Granted
        val r = reconciler()
        r.onStart(inFlow = false)
        advanceUntilIdle()
        r.onStart(inFlow = false)
        advanceUntilIdle()
        assertEquals(1, server.sent.size)
        assertEquals("the whole snapshot, not only what moved", device.toMap(), server.sent.single())
    }

    @Test
    fun `a change made outside the app reaches the server, and so does our own dialog's answer`() = runTest {
        val r = reconciler()
        r.onStart(inFlow = false)
        advanceUntilIdle()
        device[WatchedPermission.Notifications] = PermissionStatus.Denied     // turned off in Settings
        r.onStart(inFlow = false)
        advanceUntilIdle()
        device[WatchedPermission.Location] = PermissionStatus.Granted         // the user answered OUR sheet
        r.absorbOwnAnswer()
        advanceUntilIdle()
        assertEquals(3, server.sent.size)
        assertEquals(PermissionStatus.Denied, server.sent[1][WatchedPermission.Notifications])
        assertEquals("silent for the analytics, not for the server", PermissionStatus.Granted, server.sent[2][WatchedPermission.Location])
        assertTrue(changes().single()["type"] == "notifications")
    }

    @Test
    fun `a send the server refused is tried again at the next start`() = runTest {
        server.accepts = false
        val r = reconciler()
        r.onStart(inFlow = false)
        advanceUntilIdle()
        server.accepts = true
        r.onStart(inFlow = false)                                             // nothing moved on the phone
        advanceUntilIdle()
        r.onStart(inFlow = false)
        advanceUntilIdle()
        assertEquals("the refused one, its retry, and nothing after", 2, server.sent.size)
    }

    @Test
    fun `a new sign-in sends the statuses again, even with nothing changed`() = runTest {
        val r = reconciler()
        r.onStart(inFlow = false)
        advanceUntilIdle()
        r.accountChanged()
        advanceUntilIdle()
        assertEquals(2, server.sent.size)
        assertEquals(server.sent[0], server.sent[1])
    }

    @Test
    fun `an answer that arrives after a new sign-in does not count for the new account`() = runTest {
        // The launch send is still out when the session changes: it was the old account's.
        val slow = object : PermissionStatusSync {
            val sent = mutableListOf<Map<WatchedPermission, PermissionStatus>>()
            override suspend fun save(statuses: Map<WatchedPermission, PermissionStatus>): Boolean {
                sent += statuses
                delay(1_000)
                return true
            }
        }
        val r = PermissionReconciler(
            snapshot = { device.toMap() }, ledger = ledger, push = push, server = slow,
            analytics = analytics, scope = this, launchMark = OneLaunch(),
        )
        r.onStart(inFlow = false)
        runCurrent()                                                          // the launch send is out
        r.accountChanged()
        advanceUntilIdle()
        r.onStart(inFlow = false)
        advanceUntilIdle()
        assertEquals("the old account's send, the new account's, and no third", 2, slow.sent.size)
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

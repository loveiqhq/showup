/*
 * PermissionReconciler.kt
 * ShowUp · the one shared permission reconciler (epic SHOWUP-151, built for SHOWUP-163)
 *
 * The epic's rule, verbatim: "One shared permission reconciler, not a per-screen check. On launch
 * and on every foreground, for the life of the app: read the status -> if it differs from the last
 * value synced, update the server, register or invalidate the token, and emit
 * permission_status_changed (type, from, to, detected_on, in_flow)."
 *
 * WHY IT IS NOT THE SCREENS' OWN RE-READS. Each permission screen already re-reads on foreground,
 * and that is what makes a Settings trip land on the right state -- but only while that screen is
 * up. A user who turns notifications off in Settings a week later is on no permission screen at
 * all, and without this the app, the server and the analytics would go on treating them as
 * reachable. This runs for the life of the app, on top of the same readers the screens use.
 *
 * OUR OWN DIALOG IS NOT AN OUTSIDE CHANGE. The answer to our sheet is `permission_result`, and the
 * two "must never both fire for one act" (events.json). The host hands every answer to
 * [absorbOwnAnswer], which records it silently, so the next foreground sees nothing new.
 *
 * "UPDATE THE SERVER" IS TWO THINGS. Every watched status goes to `PUT /me/permissions`, and the
 * server's push path reads it: a phone with notifications off is not sent pushes it would never
 * show. And a notifications change registers or removes the push token. The ledger stays the
 * device's own record of the last status, because the analytics `from` must survive a restart
 * even when the server could not be reached.
 *
 * WHAT IS SENT, AND WHEN. The whole snapshot, whenever it differs from what the server last took
 * in this process: once at launch, after any change -- our own dialogs' answers included, because
 * the server must know even though the analytics stay silent -- and again for a new sign-in. A
 * send that fails is simply tried again at the next start; nothing waits on it.
 */
package com.showup.profile

import android.content.Context
import com.showup.analytics.AnalyticsTracker
import com.showup.api.ShowUpApi
import com.showup.api.TokenStore
import com.showup.api.generated.model.DevicePermission
import com.showup.api.generated.model.DevicePermissionStatus
import com.showup.api.generated.model.PermissionStatusItemDto
import com.showup.api.generated.model.SavePermissionStatusesDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The permissions the reconciler watches: the MVP's asks, as `enums.json` §8 names them. */
enum class WatchedPermission(val trackingType: String) {
    Notifications(ProfileAnalytics.PERMISSION_NOTIFICATIONS),
    Location(ProfileAnalytics.PERMISSION_LOCATION),
    Camera("camera"),
    Microphone("microphone"),
}

/**
 * The last status recorded for each permission -- the `from` of the next change. Survives a
 * restart, which is the point: a change made while the app was not running is still a change.
 */
interface PermissionLedger {
    fun last(permission: WatchedPermission): PermissionStatus?
    fun record(permission: WatchedPermission, status: PermissionStatus)
}

/** The device's ledger. Holds nothing but statuses, so plain preferences are enough. */
class SharedPrefsPermissionLedger(context: Context) : PermissionLedger {
    private val prefs = context.getSharedPreferences("showup_permission_ledger", Context.MODE_PRIVATE)

    override fun last(permission: WatchedPermission): PermissionStatus? =
        prefs.getString(permission.trackingType, null)
            ?.let { value -> PermissionStatus.entries.firstOrNull { it.trackingValue == value } }

    override fun record(permission: WatchedPermission, status: PermissionStatus) {
        prefs.edit().putString(permission.trackingType, status.trackingValue).apply()
    }
}

/** A ledger in memory. Previews and tests. */
class InMemoryPermissionLedger(
    private val values: MutableMap<WatchedPermission, PermissionStatus> = mutableMapOf(),
) : PermissionLedger {
    override fun last(permission: WatchedPermission): PermissionStatus? = values[permission]
    override fun record(permission: WatchedPermission, status: PermissionStatus) {
        values[permission] = status
    }
}

/** Every watched permission, read now. */
fun interface PermissionSnapshot {
    fun read(): Map<WatchedPermission, PermissionStatus>
}

/**
 * The snapshot from the readers the screens already use -- no second copy of any permission
 * check. Location reports the PERMISSION, not the device-wide switch: §24 is about the grant.
 */
fun permissionSnapshot(
    notifications: NotificationAccessReader,
    location: LocationAccessReader,
    media: MediaAccessReader,
): PermissionSnapshot = PermissionSnapshot {
    val capture = media.read()
    mapOf(
        WatchedPermission.Notifications to notifications.read(),
        WatchedPermission.Location to location.read().permission,
        WatchedPermission.Camera to capture.camera.asPermissionStatus(),
        WatchedPermission.Microphone to capture.microphone.asPermissionStatus(),
    )
}

/** §24 for a capture permission: refused-but-askable and blocked are both a refusal. */
fun MediaPermission.asPermissionStatus(): PermissionStatus = when (this) {
    MediaPermission.Granted -> PermissionStatus.Granted
    MediaPermission.NotDetermined -> PermissionStatus.NotDetermined
    MediaPermission.CanAsk, MediaPermission.Blocked -> PermissionStatus.Denied
}

/** One permission whose status moved since it was last recorded. */
data class PermissionChange(
    val permission: WatchedPermission,
    val from: PermissionStatus,
    val to: PermissionStatus,
)

/**
 * The changes between what was recorded and what was just read. A permission with nothing
 * recorded yet is not a change: the first read is a starting point, with no `from` to report.
 */
fun permissionChanges(
    last: (WatchedPermission) -> PermissionStatus?,
    now: Map<WatchedPermission, PermissionStatus>,
): List<PermissionChange> = now.mapNotNull { (permission, status) ->
    val before = last(permission) ?: return@mapNotNull null
    if (before == status) null else PermissionChange(permission, before, status)
}

/**
 * Which start of the app this is. The first start a process sees is the launch; every later one
 * is a return to the foreground. Process-wide on purpose: the activity, and with it the
 * reconciler, can be recreated while the process -- and so the launch -- carries on.
 */
fun interface LaunchMark {
    /** True exactly once per process: on the first call. */
    fun firstStart(): Boolean
}

object ProcessLaunchMark : LaunchMark {
    // One flag for the life of the process, which is what "launch" means. Written from the main
    // thread only; volatile so a read from any other thread still sees the write.
    @Volatile private var started = false

    override fun firstStart(): Boolean {
        if (started) return false
        started = true
        return true
    }
}

/** What the reconciler does with the push token. A seam, so a test can count the calls. */
interface PushTokenSync {
    suspend fun register()
    suspend fun unregister()
}

/** The real one: [PushRegistration]'s two calls, results ignored -- never blocking anything. */
class PushRegistrationSync(private val push: PushRegistration) : PushTokenSync {
    override suspend fun register() {
        push.register()
    }

    override suspend fun unregister() {
        push.unregister()
    }
}

/**
 * What the reconciler tells the server: every watched permission, as just read. A seam, so a test
 * can see what was sent. True when the server took it.
 */
fun interface PermissionStatusSync {
    suspend fun save(statuses: Map<WatchedPermission, PermissionStatus>): Boolean
}

/**
 * The real one: `PUT /me/permissions`. Signed out, it sends nothing -- there is no account to store
 * it against, and the sign-in sends it instead.
 */
class ApiPermissionStatusSync(
    private val api: ShowUpApi,
    private val tokens: TokenStore,
) : PermissionStatusSync {
    override suspend fun save(statuses: Map<WatchedPermission, PermissionStatus>): Boolean {
        if (tokens.accessToken() == null) return false
        // NOT runCatching: that would also swallow this coroutine's cancellation, and a send cut
        // short by a new sign-in would read as one the server took or refused.
        return try {
            api.permissions.savePermissionStatuses(
                SavePermissionStatusesDto(
                    platform = SavePermissionStatusesDto.Platform.android,
                    statuses = statuses.map { (permission, status) ->
                        PermissionStatusItemDto(permission = permission.wire, status = status.wire)
                    },
                ),
            ).isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}

// Spelled out rather than matched on the strings: a value added on either side fails to compile
// here instead of being sent as something the server refuses.
private val WatchedPermission.wire: DevicePermission
    get() = when (this) {
        WatchedPermission.Notifications -> DevicePermission.notifications
        WatchedPermission.Location -> DevicePermission.location
        WatchedPermission.Camera -> DevicePermission.camera
        WatchedPermission.Microphone -> DevicePermission.microphone
    }

private val PermissionStatus.wire: DevicePermissionStatus
    get() = when (this) {
        PermissionStatus.NotDetermined -> DevicePermissionStatus.not_determined
        PermissionStatus.Granted -> DevicePermissionStatus.granted
        PermissionStatus.Denied -> DevicePermissionStatus.denied
        PermissionStatus.Restricted -> DevicePermissionStatus.restricted
    }

class PermissionReconciler(
    private val snapshot: PermissionSnapshot,
    private val ledger: PermissionLedger,
    private val push: PushTokenSync?,
    private val server: PermissionStatusSync?,
    private val analytics: AnalyticsTracker?,
    private val scope: CoroutineScope,
    private val launchMark: LaunchMark = ProcessLaunchMark,
) {
    // What the server last took, in this process. Null: nothing yet, so the next read is sent.
    // Touched only from the main thread, like everything else here.
    private var lastSent: Map<WatchedPermission, PermissionStatus>? = null

    // The send in flight. Each one waits for the one before, so an older read can never land on
    // the server after a newer one.
    private var sending: Job? = null

    // Which sign-in a send belongs to. A send for an earlier one is skipped, and its answer ignored.
    private var session = 0

    /**
     * The app came to the foreground -- or launched. [inFlow]: the user is still inside profile
     * creation. Returns what changed, for a test to read.
     */
    fun onStart(inFlow: Boolean): List<PermissionChange> {
        val detectedOn = if (launchMark.firstStart()) {
            ProfileAnalytics.DETECTED_ON_LAUNCH
        } else {
            ProfileAnalytics.DETECTED_ON_FOREGROUND
        }
        val now = snapshot.read()
        val changes = permissionChanges(ledger::last, now)
        // Recorded before anything is reported: the ledger is the truth the next read compares
        // against, and a crash in what follows must not make the same change fire twice.
        now.forEach { (permission, status) ->
            if (ledger.last(permission) != status) ledger.record(permission, status)
        }
        for (change in changes) {
            analytics?.report(
                ProfileAnalytics.permissionStatusChanged(
                    from = change.from,
                    to = change.to,
                    detectedOn = detectedOn,
                    inFlow = inFlow,
                    type = change.permission.trackingType,
                ),
            )
            if (change.permission == WatchedPermission.Notifications) syncPushToken(change.to)
        }
        sendIfMoved(now)
        return changes
    }

    /**
     * One of our own permission dialogs was answered: whatever it changed is recorded silently.
     * That act is `permission_result`'s, and the screen reports it -- never this event. The server
     * is still told: what it stores is the status, however it came about.
     */
    fun absorbOwnAnswer() {
        val now = snapshot.read()
        now.forEach { (permission, status) -> ledger.record(permission, status) }
        sendIfMoved(now)
    }

    /**
     * A session began, possibly a different account's. What the server took before was for the
     * account before, so the statuses go again -- and a send still on its way no longer counts.
     */
    fun accountChanged() {
        session += 1
        lastSent = null
        sendIfMoved(snapshot.read())
    }

    private fun sendIfMoved(now: Map<WatchedPermission, PermissionStatus>) {
        val sync = server ?: return
        if (now == lastSent) return
        val forSession = session
        val before = sending
        sending = scope.launch {
            before?.join()
            // Queued for the account before, or already carried by the send ahead of this one.
            if (forSession != session || now == lastSent) return@launch
            // The session can change while the call is out; then what it took was the old one's.
            if (sync.save(now) && forSession == session) lastSent = now
        }
    }

    /** A notifications grant registers the token; a refusal removes it. Background work, always. */
    private fun syncPushToken(to: PermissionStatus) {
        val sync = push ?: return
        when (to) {
            PermissionStatus.Granted -> scope.launch { sync.register() }
            PermissionStatus.Denied, PermissionStatus.Restricted -> scope.launch { sync.unregister() }
            PermissionStatus.NotDetermined -> Unit
        }
    }
}

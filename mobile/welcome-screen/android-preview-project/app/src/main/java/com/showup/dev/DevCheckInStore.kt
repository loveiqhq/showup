/*
 * DevCheckInStore.kt
 * ShowUp · an in-memory Check-In server, for walking the flow on a phone with no backend (debug only)
 *
 * The Check-In screens need an account and `/me/check-in` behind them, and a phone on the train has
 * neither. This stands in for both so every screen, every transition and every failure state can be
 * walked and recorded on a real device: the real host routing, the real view model, only the
 * server swapped.
 *
 * Reached only through `MainActivity`'s debug launch extras (see [DevCheckInLaunch]), behind
 * `BuildConfig.DEBUG`, so R8 removes it from a release build. Nothing here is product behaviour --
 * the commit's rules are the server's (`check-in-flow.service.ts`), mirrored loosely: idempotent per
 * window, the contact-info check repeated.
 */
package com.showup.dev

import com.showup.checkin.CheckInFilters
import com.showup.checkin.CheckInState
import com.showup.checkin.CheckInStore
import com.showup.checkin.CommitRequest
import com.showup.checkin.CommitResult
import com.showup.api.Outcome
import com.showup.checkin.upliftHasContact
import kotlinx.coroutines.delay
import java.util.UUID

/** The jumps that land on Search, so the fake Check-In starts committed. */
val DEV_SEARCH_STARTS = setOf("CheckInSearch", "SearchFilters", "LikesReceived")

/** How the fake commit answers -- the four ends the Uplift screen draws. */
enum class DevCommitMode { Saves, Network, Server, ContactInfo }

class DevCheckInStore(
    /** Milliseconds every call takes: long enough to see the saving state, short enough to walk. */
    private val latencyMs: Long = 900,
    private val commitMode: DevCommitMode = DevCommitMode.Saves,
    introPassed: Boolean = false,
    /** Already committed: a jump straight to Search, which the gate would otherwise take back. */
    active: Boolean = false,
) : CheckInStore {

    private var state = CheckInState(
        windowId = UUID.randomUUID().toString(),
        introPassed = introPassed,
        active = active,
        firstCheckIn = true,
        lastMotivation = null,
        lastVibes = emptySet(),
        // The profile pre-fill a straight man would get: women, any age, 10 km.
        filters = CheckInFilters(genders = setOf("woman")),
    )
    private var committedWindow: String? = null

    override suspend fun load(): Outcome<CheckInState> {
        delay(latencyMs / 3)
        return Outcome.Ok(state)
    }

    override suspend fun markIntroPassed(): Boolean {
        state = state.copy(introPassed = true)
        return true
    }

    override suspend fun saveAnswers(motivation: String?, vibes: Set<String>?): Boolean {
        state = state.copy(
            lastMotivation = motivation ?: state.lastMotivation,
            lastVibes = vibes ?: state.lastVibes,
        )
        return true
    }

    override suspend fun saveFilters(filters: CheckInFilters): Outcome<Unit> {
        state = state.copy(filters = filters)
        return Outcome.Ok(Unit)
    }

    override suspend fun commit(request: CommitRequest): CommitResult {
        delay(latencyMs)
        return when {
            commitMode == DevCommitMode.Network -> CommitResult.Network
            commitMode == DevCommitMode.Server -> CommitResult.Server
            // The server repeats the client's check; this mode refuses text the client let through.
            commitMode == DevCommitMode.ContactInfo || upliftHasContact(request.uplift.orEmpty()) -> CommitResult.ContactInfo
            else -> {
                // Idempotent per window: a retry answers with the same saved state.
                if (committedWindow != request.windowId) committedWindow = request.windowId
                state = state.copy(active = true, firstCheckIn = false)
                CommitResult.Saved(state)
            }
        }
    }
}

/**
 * The debug launch that opens the Check-In on this store, e.g.
 *
 *   adb shell am start -n com.showup/.MainActivity --ez dev_checkin true --es dev_start CheckInIntro \
 *       --es dev_commit Network --el dev_latency 1500 --ez dev_intro_passed false
 *
 * `dev_start` is a [com.showup.welcome.FlowScreen] name. Read once, on a fresh launch only.
 * `--ez dev_premium true` opens Search on the Premium plan -- the grid and the Likes tab -- with no
 * trip to the dev menu's plan switch, so a walk or a recording starts where it is meant to.
 * `--es dev_stage_close sample-leonie` picks who the first availability re-check closes (Sofia by
 * default, as frame J has it) -- Search 03's "another like (staged) closed" recording.
 */
data class DevCheckInLaunch(
    val start: String,
    val commit: DevCommitMode,
    val latencyMs: Long,
    val introPassed: Boolean,
    val premium: Boolean = false,
    val stageClose: String = "sample-sofia",
) {
    companion object {
        const val EXTRA_ON = "dev_checkin"
        const val EXTRA_START = "dev_start"
        const val EXTRA_COMMIT = "dev_commit"
        const val EXTRA_LATENCY = "dev_latency"
        const val EXTRA_INTRO_PASSED = "dev_intro_passed"
        const val EXTRA_PREMIUM = "dev_premium"
        const val EXTRA_STAGE_CLOSE = "dev_stage_close"

        fun from(intent: android.content.Intent?): DevCheckInLaunch? {
            if (intent?.getBooleanExtra(EXTRA_ON, false) != true) return null
            return DevCheckInLaunch(
                start = intent.getStringExtra(EXTRA_START) ?: "CheckInIntro",
                commit = runCatching { DevCommitMode.valueOf(intent.getStringExtra(EXTRA_COMMIT) ?: "Saves") }
                    .getOrDefault(DevCommitMode.Saves),
                latencyMs = intent.getLongExtra(EXTRA_LATENCY, 900),
                introPassed = intent.getBooleanExtra(EXTRA_INTRO_PASSED, false),
                premium = intent.getBooleanExtra(EXTRA_PREMIUM, false),
                stageClose = intent.getStringExtra(EXTRA_STAGE_CLOSE) ?: "sample-sofia",
            )
        }
    }
}

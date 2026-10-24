/*
 * DevSearchStore.kt
 * ShowUp · an in-memory Search server, for walking date search on a phone with no backend (debug only)
 *
 * The other half of [DevCheckInStore]: once the fake Check-In commits, Search opens on these samples,
 * so the profile, the like flow, the coach, the viewer and the way to Filters and back can all be
 * walked and recorded on a real device. The real host routing and the real view model; only the
 * server is swapped.
 *
 * Reached only through `MainActivity`'s debug launch extras ([DevCheckInLaunch]), behind
 * `BuildConfig.DEBUG`, so R8 removes it from a release build. Nothing here is product behaviour.
 */
package com.showup.dev

import com.showup.api.Outcome
import com.showup.search.SearchAnalytics
import com.showup.search.SearchQueue
import com.showup.search.SearchSamples
import com.showup.search.SearchSession
import com.showup.search.SearchStore
import kotlinx.coroutines.delay

class DevSearchStore(
    /** Milliseconds every call takes: long enough to see the loading state, short enough to walk. */
    private val latencyMs: Long = 900,
) : SearchStore {

    /** Leonie in full, then Mara and Ana. A like or a pass takes someone out, as the server does. */
    private val queue = (listOf(SearchSamples.FULL) + SearchSamples.NEXT).toMutableList()
    private var coachHidden = false
    private var premium = false

    override suspend fun queue(zone: String): Outcome<SearchQueue> {
        delay(latencyMs)
        return Outcome.Ok(SearchQueue(queue.size, queue.toList(), SearchSamples.VIEWER))
    }

    override suspend fun like(targetUserId: String, message: String?, position: Int): Outcome<Unit> {
        delay(latencyMs / 3)
        queue.removeAll { it.userId == targetUserId }
        return Outcome.Ok(Unit)
    }

    override suspend fun pass(targetUserId: String, type: SearchAnalytics.PassType): Outcome<Unit> {
        delay(latencyMs / 3)
        queue.removeAll { it.userId == targetUserId }
        return Outcome.Ok(Unit)
    }

    override suspend fun hideCoach(): Outcome<Unit> {
        coachHidden = true
        return Outcome.Ok(Unit)
    }

    override suspend fun likesOpenCount(): Outcome<Int> = Outcome.Ok(2)

    override suspend fun session(): Outcome<SearchSession> = Outcome.Ok(SearchSession(premium = premium, coachHidden = coachHidden))

    override suspend fun setPremium(premium: Boolean): Outcome<Unit> {
        this.premium = premium
        return Outcome.Ok(Unit)
    }
}

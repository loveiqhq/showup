/*
 * DevSearchStore.kt
 * ShowUp · an in-memory Search server, for walking date search on a phone with no backend (debug only)
 *
 * The other half of [DevCheckInStore]: once the fake Check-In commits, Search opens on these samples,
 * so the profile, the like flow, the coach, the viewer and the way to Filters and back can all be
 * walked and recorded on a real device. The real host routing and the real view model; only the
 * server is swapped.
 *
 * PREMIUM (the dev menu's plan switch) opens the grid and the Likes tab on the reference's people,
 * with the reference's staged answers -- the kit's demo props, here the server's: a confirm to Leonie
 * is refused (`matched`, frame H), a confirm to Anna wins; the first re-check closes Sofia (`matched`)
 * and moves Anna's meet time to 18:00 tomorrow (frame J); Clara's closed like shows on the first
 * Likes visit only (seen on load).
 *
 * Reached only through `MainActivity`'s debug launch extras ([DevCheckInLaunch]), behind
 * `BuildConfig.DEBUG`, so R8 removes it from a release build. Nothing here is product behaviour.
 */
package com.showup.dev

import com.showup.api.Outcome
import com.showup.search.CardStatus
import com.showup.search.CloseReason
import com.showup.search.ConfirmAnswer
import com.showup.search.LikesReceived
import com.showup.search.SearchAnalytics
import com.showup.search.SearchGrid
import com.showup.search.SearchQueue
import com.showup.search.SearchSamples
import com.showup.search.SearchSession
import com.showup.search.SearchStore
import kotlinx.coroutines.delay
import java.time.Duration

class DevSearchStore(
    /** Milliseconds every call takes: long enough to see the loading state, short enough to walk. */
    private val latencyMs: Long = 900,
) : SearchStore {

    /** Leonie in full, then Mara and Ana. A like or a pass takes someone out, as the server does. */
    private val queue = (listOf(SearchSamples.FULL) + SearchSamples.NEXT).toMutableList()
    private var coachHidden = false
    private var premium = false

    /** Who the viewer has answered: out of the next grid, and -- for a liker -- off the list. */
    private val answered = mutableSetOf<String>()
    /** Clara's closed card is seen on the first Likes visit and gone from the next. */
    private var closedSeen = false
    /** The first re-check is the staged one. */
    private var rechecks = 0

    override suspend fun queue(zone: String): Outcome<SearchQueue> {
        delay(latencyMs)
        return Outcome.Ok(SearchQueue(queue.size, queue.toList(), SearchSamples.VIEWER))
    }

    override suspend fun like(targetUserId: String, message: String?, position: Int): Outcome<Unit> {
        delay(latencyMs / 3)
        queue.removeAll { it.userId == targetUserId }
        answered += targetUserId
        return Outcome.Ok(Unit)
    }

    override suspend fun pass(targetUserId: String, type: SearchAnalytics.PassType): Outcome<Unit> {
        delay(latencyMs / 3)
        queue.removeAll { it.userId == targetUserId }
        if (type == SearchAnalytics.PassType.NotInterested) answered += targetUserId
        return Outcome.Ok(Unit)
    }

    override suspend fun hideCoach(): Outcome<Unit> {
        coachHidden = true
        return Outcome.Ok(Unit)
    }

    override suspend fun likesOpenCount(): Outcome<Int> =
        Outcome.Ok(SearchSamples.LIKES.count { it.closed == null && it.userId !in answered })

    override suspend fun session(): Outcome<SearchSession> = Outcome.Ok(SearchSession(premium = premium, coachHidden = coachHidden))

    override suspend fun setPremium(premium: Boolean): Outcome<Unit> {
        this.premium = premium
        return Outcome.Ok(Unit)
    }

    override suspend fun grid(zone: String): Outcome<SearchGrid> {
        delay(latencyMs)
        val cards = SearchSamples.GRID.filter { it.profile.userId !in answered }
        return Outcome.Ok(SearchGrid(cards.size, cards, SearchSamples.VIEWER))
    }

    override suspend fun availability(userIds: List<String>, zone: String): Outcome<Map<String, CardStatus>> {
        delay(latencyMs / 3)
        val staged = rechecks++ == 0
        return Outcome.Ok(
            userIds.associateWith { id ->
                val profile = SearchSamples.GRID.firstOrNull { it.profile.userId == id }?.profile
                when {
                    staged && id == "sample-sofia" -> CardStatus.Closed(CloseReason.Matched)
                    staged && id == "sample-anna" && profile != null ->
                        CardStatus.Available(profile.meetAt.plus(Duration.ofDays(1)), "tomorrow")
                    profile != null -> CardStatus.Available(profile.meetAt, profile.meetDay)
                    else -> CardStatus.Closed(CloseReason.NoOverlap)
                }
            },
        )
    }

    override suspend fun likesReceived(zone: String): Outcome<LikesReceived> {
        delay(latencyMs)
        val open = SearchSamples.LIKES.filter { it.closed == null && it.userId !in answered }
        val closed = if (closedSeen) emptyList() else SearchSamples.LIKES.filter { it.closed != null }
        closedSeen = true
        return Outcome.Ok(LikesReceived(open.size, closed.size, open + closed, SearchSamples.VIEWER))
    }

    override suspend fun confirm(targetUserId: String, position: Int): Outcome<ConfirmAnswer> {
        // Long enough to see Confirm date wait for the answer.
        delay(latencyMs)
        answered += targetUserId
        return Outcome.Ok(
            if (targetUserId == "sample-leonie") ConfirmAnswer.Refused(CloseReason.Matched) else ConfirmAnswer.Won,
        )
    }
}

/*
 * FakeSearchStore.kt
 * ShowUp · an in-memory Search server for the view model tests (SHOWUP-184, 185, 186)
 *
 * Records every call, answers from samples, and can be made to fail or to answer a confirm, a
 * re-check or a list read in a particular way -- so every path of the view model is driven with no
 * network and no device.
 */
package com.showup.search

import com.showup.api.CallFailure
import com.showup.api.Outcome

/** A server that records every call and can be made to fail. */
internal class FakeSearchStore : SearchStore {
    var profiles = listOf(SearchSamples.FULL) + SearchSamples.NEXT
    var coachHidden = false
    var queueFailure: CallFailure? = null
    /** How many like calls fail before one lands. */
    var likeFailures = 0
    val likes = mutableListOf<Triple<String, String?, Int>>()
    val passes = mutableListOf<Pair<String, SearchAnalytics.PassType>>()
    var coachHides = 0
    var queueReads = 0

    override suspend fun queue(zone: String): Outcome<SearchQueue> {
        queueReads++
        queueFailure?.let { return Outcome.Failed(it) }
        return Outcome.Ok(SearchQueue(profiles.size, profiles, SearchSamples.VIEWER))
    }
    override suspend fun like(targetUserId: String, message: String?, position: Int): Outcome<Unit> {
        likes += Triple(targetUserId, message, position)
        if (likeFailures > 0) { likeFailures--; return Outcome.Failed(CallFailure.Network) }
        return Outcome.Ok(Unit)
    }
    override suspend fun pass(targetUserId: String, type: SearchAnalytics.PassType): Outcome<Unit> {
        passes += targetUserId to type
        return Outcome.Ok(Unit)
    }
    override suspend fun hideCoach(): Outcome<Unit> {
        coachHides++
        if (coachHideLands) coachHidden = true
        return Outcome.Ok(Unit)
    }

    /** False: the server has not stored the flag yet when the next session read arrives. */
    var coachHideLands = true
    var openCount = 2
    var countReads = 0
    override suspend fun likesOpenCount(): Outcome<Int> {
        countReads++
        return Outcome.Ok(openCount)
    }
    var premium = false
    override suspend fun session(): Outcome<SearchSession> = Outcome.Ok(SearchSession(premium = premium, coachHidden = coachHidden))
    override suspend fun setPremium(premium: Boolean): Outcome<Unit> = Outcome.Ok(Unit)

    // ── the grid and the Likes tab ──
    var grid = SearchSamples.GRID
    var gridFailure: CallFailure? = null
    var gridReads = 0
    override suspend fun grid(zone: String): Outcome<SearchGrid> {
        gridReads++
        gridFailure?.let { return Outcome.Failed(it) }
        return Outcome.Ok(SearchGrid(grid.size, grid, SearchSamples.VIEWER))
    }

    /** What the next re-check answers, per person; anyone not here is unchanged. */
    var statuses: Map<String, CardStatus> = emptyMap()
    var availabilityFails = false
    val availabilityAsks = mutableListOf<List<String>>()
    override suspend fun availability(userIds: List<String>, zone: String): Outcome<Map<String, CardStatus>> {
        availabilityAsks += userIds
        if (availabilityFails) return Outcome.Failed(CallFailure.Network)
        return Outcome.Ok(statuses.filterKeys { it in userIds })
    }

    var likesList = SearchSamples.LIKES
    var likesReads = 0
    override suspend fun likesReceived(zone: String): Outcome<LikesReceived> {
        likesReads++
        val open = likesList.count { it.closed == null }
        return Outcome.Ok(LikesReceived(open, likesList.size - open, likesList, SearchSamples.VIEWER))
    }

    /** The confirm's answer; null answers nothing at all (offline). */
    var confirmAnswer: ConfirmAnswer? = ConfirmAnswer.Won
    val confirms = mutableListOf<Pair<String, Int>>()
    override suspend fun confirm(targetUserId: String, position: Int): Outcome<ConfirmAnswer> {
        confirms += targetUserId to position
        return confirmAnswer?.let { Outcome.Ok(it) } ?: Outcome.Failed(CallFailure.Network)
    }
}

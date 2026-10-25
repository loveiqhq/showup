/*
 * SearchRepository.kt
 * ShowUp · what Date search reads and writes, behind one interface (SHOWUP-184, 185, 186)
 *
 * The screen and its view model never see a generated type: the server's payload is read here into
 * the app's own [SearchProfile], and a value this app does not know is dropped rather than drawn.
 * `DevSearchStore` implements the same interface in memory, so every state can be walked on a
 * phone with no backend.
 */
package com.showup.search

import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.api.ShowUpApi
import com.showup.api.callCatching
import com.showup.api.generated.model.CreateLikeDto
import com.showup.api.generated.model.CreateSearchPassDto
import com.showup.api.generated.model.LikeReceivedDto
import com.showup.api.generated.model.PlanTier
import com.showup.api.generated.model.SearchAvailabilityRequestDto
import com.showup.api.generated.model.SearchMediaDto
import com.showup.api.generated.model.SearchPassType
import com.showup.api.generated.model.SearchProfileDto
import com.showup.api.generated.model.SetPlanTierDto
import com.showup.api.outcome
import java.time.Instant

/** A written prompt on someone's profile: its topic id (the app's own text) and the answer. */
data class SearchPrompt(val topicId: String, val answer: String)

/** A voice note or a video: where to load it, its real length, and the prompt it answers. */
data class SearchMedia(val url: String, val durationMs: Int, val promptId: String)

/**
 * One profile on the Search tab. Every section's source is optional: an empty list or a null is a
 * section the screen does not draw ("Every section is optional").
 */
data class SearchProfile(
    val userId: String,
    val name: String,
    /** Null when the person hides their age: the name stands alone. */
    val age: Int?,
    /** Rounded by the server: 0 is "< 1 km". */
    val distanceKm: Int,
    val district: String?,
    val meetAt: Instant,
    /** `today` or `tomorrow`, by the Check-In's 7am day, from the server. */
    val meetDay: String,
    val attendedCount: Int,
    val motivation: String?,
    val vibes: List<String>,
    val uplift: String?,
    /** Loadable URLs, in profile order. */
    val photos: List<String>,
    val prompts: List<SearchPrompt>,
    val voice: SearchMedia?,
    val video: SearchMedia?,
    val essentials: EssentialAnswers,
    /** How I live: field id to value id, answered groups only. */
    val howILive: Map<String, String>,
    val interests: List<String>,
    /** "Charms from past dates" have no source until post-date ratings ship: always empty today. */
    val charms: List<Charm> = emptyList(),
)

/** A charm from a past date (callout 21). Nothing sends one yet. */
data class Charm(val label: String, val star: Boolean)

/** How many profiles the queue holds, and the next few in full. */
/**
 * One read of the queue. [viewer] is the user's own vibes and interests, from the same request, so
 * shared ones are marked against what the server matched on -- also on a launch straight onto Search.
 */
data class SearchQueue(val resultsCount: Int, val profiles: List<SearchProfile>, val viewer: SearchViewer = SearchViewer())

/** What the session says about Search: the plan and the coach's stored opt-out. */
data class SearchSession(val premium: Boolean, val coachHidden: Boolean)

/** One card of the Premium grid (Search 02): the whole profile, and whether they liked the viewer. */
data class GridCard(val profile: SearchProfile, val likedYou: Boolean)

/** The Premium grid: the whole queue in queue order, only people still available. */
data class SearchGrid(val resultsCount: Int, val cards: List<GridCard>, val viewer: SearchViewer = SearchViewer())

/**
 * One person's status on the re-check. A status this app does not know (a newer server) is not here
 * at all, which the view model reads as "no change" -- never as closed.
 */
sealed interface CardStatus {
    /** Still available: the earliest hour both hold, which may have moved. */
    data class Available(val meetAt: Instant, val meetDay: String) : CardStatus

    data class Closed(val reason: CloseReason) : CardStatus
}

/**
 * One like the viewer received (Search 03). Open: [profile] to open, and the message if one was
 * written. Closed: the name and photo only.
 */
data class LikeReceived(
    val userId: String,
    val name: String,
    val age: Int?,
    /** The first photo's URL, or empty for none (the slot draws its placeholder). */
    val photo: String,
    val closed: CloseReason?,
    val message: String?,
    val profile: SearchProfile?,
)

/** The Likes tab, as read once per visit, in the server's order. */
data class LikesReceived(
    val openCount: Int,
    val closedCount: Int,
    val cards: List<LikeReceived>,
    val viewer: SearchViewer = SearchViewer(),
)

/** What the server said to a Confirm date (Search 02, 03). */
sealed interface ConfirmAnswer {
    /** The first confirm to arrive: a match. */
    data object Won : ConfirmAnswer

    /** The like had closed first; the sorry sheet says why. */
    data class Refused(val reason: CloseReason) : ConfirmAnswer
}

interface SearchStore {
    /** The queue, with `meetDay` worked out in [zone] (an IANA id). */
    suspend fun queue(zone: String): Outcome<SearchQueue>

    /**
     * Sends a like. Idempotent per person on the server, so a retry after a timeout never makes a
     * second one -- which is what lets the view model retry it silently.
     */
    suspend fun like(targetUserId: String, message: String?, position: Int): Outcome<Unit>

    suspend fun pass(targetUserId: String, type: SearchAnalytics.PassType): Outcome<Unit>

    /** `Don't show again`: the one act that sets the server's coach flag. */
    suspend fun hideCoach(): Outcome<Unit>

    suspend fun likesOpenCount(): Outcome<Int>

    suspend fun session(): Outcome<SearchSession>

    /** Dev menu only: the plan switch, a 404 in production. */
    suspend fun setPremium(premium: Boolean): Outcome<Unit>

    /** The Premium grid, with `meetDay` worked out in [zone]. */
    suspend fun grid(zone: String): Outcome<SearchGrid>

    /** The availability re-check: every card on screen in one call. */
    suspend fun availability(userIds: List<String>, zone: String): Outcome<Map<String, CardStatus>>

    /** The Likes tab, once per visit. Reading it marks the closed cards on it seen. */
    suspend fun likesReceived(zone: String): Outcome<LikesReceived>

    /**
     * `Confirm date`: a like to someone who liked the viewer, sent at once and waited for -- never
     * in the background, because the sheet waits on the answer. Idempotent on the server, so a
     * retry after a timeout answers with the like already made.
     */
    suspend fun confirm(targetUserId: String, position: Int): Outcome<ConfirmAnswer>
}

class SearchRepository(private val api: ShowUpApi) : SearchStore {

    override suspend fun queue(zone: String): Outcome<SearchQueue> = outcome {
        val response = api.search.getSearchQueue(zone)
        response.body()?.takeIf { response.isSuccessful }?.let { dto ->
            SearchQueue(
                dto.resultsCount,
                dto.profiles.map { it.toProfile(api) },
                SearchViewer(vibes = dto.viewer.vibes.toSet(), interests = dto.viewer.interests.toSet()),
            )
        }
    }

    override suspend fun like(targetUserId: String, message: String?, position: Int): Outcome<Unit> = outcome {
        api.matching.likeProfile(
            CreateLikeDto(targetUserId = targetUserId, message = message, position = position),
        ).takeIf { it.isSuccessful }?.let { }
    }

    override suspend fun pass(targetUserId: String, type: SearchAnalytics.PassType): Outcome<Unit> = outcome {
        api.search.passSearchProfile(
            CreateSearchPassDto(
                targetUserId = targetUserId,
                // By wire value, not constant name: the generator strips the shared `not_` prefix
                // (`now`, `interested`), and a regenerated name must not change what is sent.
                passType = SearchPassType.entries.first { it.value == type.wire },
            ),
        ).takeIf { it.isSuccessful }?.let { }
    }

    override suspend fun hideCoach(): Outcome<Unit> = outcome {
        api.search.hideSearchCoach().takeIf { it.isSuccessful }?.let { }
    }

    override suspend fun likesOpenCount(): Outcome<Int> = outcome {
        val response = api.search.getLikesOpenCount()
        response.body()?.takeIf { response.isSuccessful }?.count
    }

    override suspend fun session(): Outcome<SearchSession> = outcome {
        val response = api.account.getCurrentUser()
        response.body()?.takeIf { response.isSuccessful }?.let { me ->
            // Read tolerantly: a tier this app does not know is Freemium, never Premium.
            SearchSession(premium = me.planTier == "premium", coachHidden = me.searchCoachHidden)
        }
    }

    override suspend fun setPremium(premium: Boolean): Outcome<Unit> = callCatching {
        api.dev.setDevPlanTier(SetPlanTierDto(if (premium) PlanTier.premium else PlanTier.freemium)).isSuccessful
    }.fold(
        onSuccess = { ok -> if (ok) Outcome.Ok(Unit) else Outcome.Failed(CallFailure.Server) },
        onFailure = { Outcome.Failed(CallFailure.Network) },
    )

    override suspend fun grid(zone: String): Outcome<SearchGrid> = outcome {
        val response = api.search.getSearchGrid(zone)
        response.body()?.takeIf { response.isSuccessful }?.let { dto ->
            SearchGrid(
                dto.resultsCount,
                dto.cards.map { GridCard(it.profile.toProfile(api), it.likedYou) },
                SearchViewer(vibes = dto.viewer.vibes.toSet(), interests = dto.viewer.interests.toSet()),
            )
        }
    }

    override suspend fun availability(userIds: List<String>, zone: String): Outcome<Map<String, CardStatus>> = outcome {
        val response = api.search.getSearchAvailability(SearchAvailabilityRequestDto(userIds), zone)
        response.body()?.takeIf { response.isSuccessful }?.let { dto ->
            dto.statuses.mapNotNull { s -> readStatus(s.status, s.meetAt?.toInstant(), s.meetDay)?.let { s.userId to it } }.toMap()
        }
    }

    override suspend fun likesReceived(zone: String): Outcome<LikesReceived> = outcome {
        val response = api.search.getLikesReceived(zone)
        response.body()?.takeIf { response.isSuccessful }?.let { dto ->
            LikesReceived(
                openCount = dto.openCount,
                closedCount = dto.closedCount,
                // A card that is neither open (with its profile) nor closed for a known reason is
                // one this app cannot draw: dropped, never guessed at.
                cards = dto.cards.mapNotNull { it.toLike(api) },
                viewer = SearchViewer(vibes = dto.viewer.vibes.toSet(), interests = dto.viewer.interests.toSet()),
            )
        }
    }

    override suspend fun confirm(targetUserId: String, position: Int): Outcome<ConfirmAnswer> = outcome {
        val response = api.matching.likeProfile(CreateLikeDto(targetUserId = targetUserId, position = position))
        response.body()?.takeIf { response.isSuccessful }?.let { dto -> confirmAnswer(dto.matched, dto.closed) }
    }
}

/**
 * A re-check status as the app reads it: `available` with its hour, or closed for a reason it knows.
 * Anything else -- a value from a newer server, an available with no hour -- is null: no change.
 */
internal fun readStatus(status: String, meetAt: Instant?, meetDay: String?): CardStatus? = when (status) {
    "available" -> if (meetAt != null && meetDay != null) CardStatus.Available(meetAt, meetDay) else null
    else -> CloseReason.of(status)?.let { CardStatus.Closed(it) }
}

/**
 * A confirm's answer. Not a match and no reason this app knows: the confirm did not make a date, and
 * `no_overlap` is the one reason that never claims a match that did not happen.
 */
internal fun confirmAnswer(matched: Boolean, closed: String?): ConfirmAnswer =
    if (matched) ConfirmAnswer.Won else ConfirmAnswer.Refused(CloseReason.of(closed) ?: CloseReason.NoOverlap)

private fun LikeReceivedDto.toLike(api: ShowUpApi): LikeReceived? {
    val reason = closed?.let { CloseReason.of(it) ?: return null }
    val opened = if (reason == null) (profile?.toProfile(api) ?: return null) else null
    return LikeReceived(
        userId = userId,
        name = name,
        age = age,
        photo = photo?.url?.let { api.absoluteUrl(it) }.orEmpty(),
        closed = reason,
        message = message?.takeIf { reason == null },
        profile = opened,
    )
}

/** The payload, read into the app's own types. Photo and media paths become loadable URLs here. */
internal fun SearchProfileDto.toProfile(api: ShowUpApi) = SearchProfile(
    userId = userId,
    name = name,
    age = age,
    distanceKm = distanceKm,
    district = district,
    meetAt = meetAt.toInstant(),
    meetDay = meetDay,
    attendedCount = attendedCount,
    motivation = motivation,
    vibes = vibes,
    uplift = uplift,
    photos = photos.map { api.absoluteUrl(it.url) },
    prompts = prompts.map { SearchPrompt(it.topicId, it.answer) },
    voice = voice?.toMedia(api),
    video = video?.toMedia(api),
    essentials = EssentialAnswers(
        heightCm = essentials.heightCm,
        gender = essentials.gender,
        orientation = essentials.orientation,
        datingLanguages = essentials.datingLanguages.orEmpty(),
        education = essentials.education,
        religion = essentials.religion,
        politics = essentials.politics,
    ),
    howILive = buildMap {
        howILive.activity?.let { put("activity", it) }
        howILive.diet?.let { put("diet", it) }
        howILive.alcohol?.let { put("alcohol", it) }
        howILive.smoking?.let { put("smoking", it) }
        howILive.rhythm?.let { put("rhythm", it) }
        howILive.social?.let { put("social", it) }
    },
    interests = interests,
)

private fun SearchMediaDto.toMedia(api: ShowUpApi) = SearchMedia(api.absoluteUrl(url), durationMs, promptId)

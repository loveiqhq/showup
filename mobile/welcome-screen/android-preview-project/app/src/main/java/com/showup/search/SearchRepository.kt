/*
 * SearchRepository.kt
 * ShowUp · what Date search 01 reads and writes, behind one interface (SHOWUP-184)
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
import com.showup.api.generated.model.PlanTier
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
data class SearchQueue(val resultsCount: Int, val profiles: List<SearchProfile>)

/** What the session says about Search: the plan and the coach's stored opt-out. */
data class SearchSession(val premium: Boolean, val coachHidden: Boolean)

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
}

class SearchRepository(private val api: ShowUpApi) : SearchStore {

    override suspend fun queue(zone: String): Outcome<SearchQueue> = outcome {
        val response = api.search.getSearchQueue(zone)
        response.body()?.takeIf { response.isSuccessful }?.let { dto ->
            SearchQueue(dto.resultsCount, dto.profiles.map { it.toProfile(api) })
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

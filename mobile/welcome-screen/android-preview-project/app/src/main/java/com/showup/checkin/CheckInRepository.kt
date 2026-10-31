/*
 * CheckInRepository.kt
 * ShowUp · the 48hrs Check-In's server calls (SHOWUP-178 to SHOWUP-183)
 *
 * Behind [CheckInStore] so the view model is tested with no network. Every call is the generated
 * client's (`CheckInApi`, from openapi.json); nothing here hand-writes a request.
 */
package com.showup.checkin

import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.api.ShowUpApi
import com.showup.api.callCatching
import com.showup.api.outcome
import com.showup.api.generated.model.CheckInSlotDto
import com.showup.api.generated.model.CheckInSlotTier
import com.showup.api.generated.model.CheckInStateDto
import com.showup.api.generated.model.CommitCheckInDto
import com.showup.api.generated.model.MeetGender
import com.showup.api.generated.model.SaveCheckInAnswersDto
import com.showup.api.generated.model.SaveFilterSettingsDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import com.showup.api.generated.model.CheckInMotivation as WireMotivation
import com.showup.api.generated.model.CheckInVibe as WireVibe

/** What the Check-In opens with -- `GET /me/check-in`. */
data class CheckInState(
    /** `window_id` on every Check-In event. Null only before profile creation completes. */
    val windowId: String?,
    /** The gate reopens at Today once true, at the intro before. */
    val introPassed: Boolean,
    /** A committed Check-In in the current window: date search is unlocked. */
    val active: Boolean,
    val firstCheckIn: Boolean,
    /** The last accepted answers -- they pre-select Motivation and Vibe. */
    val lastMotivation: String?,
    val lastVibes: Set<String>,
    /** The saved filter settings, or the profile pre-fill for a user with none. */
    val filters: CheckInFilters,
    /**
     * The remote flag `filters_dealbreakers` (Check-In 05b), from our own server: whether Filters
     * shows its two Dealbreaker switches. Off until matching applies the soft range.
     */
    val filtersDealbreakers: Boolean = false,
)

/** One picked hour on the wire: the instant it starts and its tier. */
data class CommitSlot(val startsAt: Instant, val tier: SlotTier)

/** `Find my date`. */
data class CommitRequest(
    val windowId: String,
    val slots: List<CommitSlot>,
    val motivation: String,
    val vibes: Set<String>,
    /** Trimmed; null when empty -- the server stores no uplift, never "". */
    val uplift: String?,
    /** Where the phone is, and its district: what Search measures from. Null without location. */
    val place: CheckInPlace? = null,
)

/** How a commit ended -- each maps to one state of the Uplift screen (story 06). */
sealed interface CommitResult {
    data class Saved(val state: CheckInState) : CommitResult

    /** The server refused the text for contact info: state C, not E. */
    data object ContactInfo : CommitResult

    /** No response -- §32 `network`. */
    data object Network : CommitResult

    /** A response that is not a save -- §32 `server`. */
    data object Server : CommitResult

    /**
     * Every picked hour has started -- the flow sat open past them -- so there is nothing to commit.
     * Found on the device before sending, or refused by the server when its clock is ahead of the
     * device's. Not a failed save: the user picks again.
     */
    data object NoHours : CommitResult
}

interface CheckInStore {
    suspend fun load(): Outcome<CheckInState>
    suspend fun markIntroPassed(): Boolean
    suspend fun saveAnswers(motivation: String?, vibes: Set<String>?): Boolean
    /** [withDealbreakers]: the remote flag is on, so the two switches are sent; off, they are left out. */
    suspend fun saveFilters(filters: CheckInFilters, withDealbreakers: Boolean = false): Outcome<Unit>
    suspend fun commit(request: CommitRequest): CommitResult
}

class CheckInRepository(private val api: ShowUpApi) : CheckInStore {
    override suspend fun load(): Outcome<CheckInState> = outcome {
        val response = api.checkIn.getCheckInState()
        response.body()?.takeIf { response.isSuccessful }?.toState()
    }

    override suspend fun markIntroPassed(): Boolean = callCatching {
        api.checkIn.markCheckInIntroPassed().isSuccessful
    }.getOrDefault(false)

    override suspend fun saveAnswers(motivation: String?, vibes: Set<String>?): Boolean = callCatching {
        api.checkIn.saveCheckInAnswers(
            SaveCheckInAnswersDto(
                motivation = motivation?.let { id -> WireMotivation.entries.first { it.value == id } },
                vibes = vibes?.let { set -> VIBE_ORDER.filter { it in set }.map { id -> wireVibe(id) } },
            ),
        ).isSuccessful
    }.getOrDefault(false)

    override suspend fun saveFilters(filters: CheckInFilters, withDealbreakers: Boolean): Outcome<Unit> = outcome {
        api.checkIn.saveFilterSettings(
            SaveFilterSettingsDto(
                meetGender = CheckInGender.entries.filter { it.id in filters.genders }
                    .map { g -> MeetGender.entries.first { it.value == g.id } },
                minAge = filters.minAge,
                maxAge = filters.maxAgeOrNull,
                distanceKm = filters.distanceKm,
                // "With the flag off ... the fields are not sent": the server keeps what it has.
                ageDealbreaker = filters.ageDealbreaker.takeIf { withDealbreakers },
                distanceDealbreaker = filters.distanceDealbreaker.takeIf { withDealbreakers },
            ),
        ).takeIf { it.isSuccessful }?.let { }
    }

    override suspend fun commit(request: CommitRequest): CommitResult {
        val response = try {
            api.checkIn.commitCheckIn(
                CommitCheckInDto(
                    windowId = UUID.fromString(request.windowId),
                    slots = request.slots.map {
                        CheckInSlotDto(
                            startsAt = it.startsAt.toString(),
                            tier = if (it.tier == SlotTier.Preferred) CheckInSlotTier.preferred
                            else CheckInSlotTier.available,
                        )
                    },
                    motivation = WireMotivation.entries.first { it.value == request.motivation },
                    vibes = VIBE_ORDER.filter { it in request.vibes }.map { wireVibe(it) },
                    uplift = request.uplift,
                    latitude = request.place?.latitude?.toBigDecimal(),
                    longitude = request.place?.longitude?.toBigDecimal(),
                    district = request.place?.district,
                ),
            )
        } catch (e: CancellationException) {
            // The screen's scope ended: not a failed commit, and nothing may be reported as one.
            throw e
        } catch (e: IOException) {
            return CommitResult.Network
        } catch (e: Exception) {
            return CommitResult.Server
        }
        val body = response.body()
        if (response.isSuccessful && body != null) return CommitResult.Saved(body.toState())
        // A 422 naming `uplift_contact_info` is the server's contact-info check, not a failed save.
        val error = runCatching {
            Json.parseToJsonElement(response.errorBody()?.string().orEmpty())
                .jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            response.code() == 422 && error == CONTACT_INFO_ERROR -> CommitResult.ContactInfo
            response.code() == 400 && error == NO_HOURS_ERROR -> CommitResult.NoHours
            else -> CommitResult.Server
        }
    }

    private fun wireVibe(id: String) = WireVibe.entries.first { it.value == id }

    /**
     * `runCatching` that lets a cancellation through. Plain `runCatching` would turn the end of the
     * screen's scope into a failed save -- and a failed filter save inside the commit into a reported
     * `check_in_commit_failed` for a commit nobody is waiting on.
     */
    private companion object {
        /** The API's error code for an uplift refused for contact info (`check-in-flow.ts`). */
        const val CONTACT_INFO_ERROR = "uplift_contact_info"

        /** The API's error code for a commit whose every hour has started (`check-in-flow.ts`). */
        const val NO_HOURS_ERROR = "check_in_no_hours"
    }
}

/** The server's state, read into the app's own types. Unknown values a newer server sent are dropped. */
internal fun CheckInStateDto.toState() = CheckInState(
    windowId = windowId?.toString(),
    introPassed = introPassed,
    active = active,
    firstCheckIn = firstCheckIn,
    lastMotivation = lastMotivation?.value?.takeIf { id -> MotivationOption.entries.any { it.id == id } },
    lastVibes = lastVibes.map { it.value }.filter { it in VIBE_ORDER }.toSet(),
    filters = CheckInFilters(
        genders = filters.meetGender.map { it.value }.toSet(),
        minAge = filters.minAge,
        maxAge = filters.maxAge ?: AGE_MAX,
        distanceKm = filters.distanceKm,
        ageDealbreaker = filters.ageDealbreaker,
        distanceDealbreaker = filters.distanceDealbreaker,
    ),
    filtersDealbreakers = filtersDealbreakers,
)

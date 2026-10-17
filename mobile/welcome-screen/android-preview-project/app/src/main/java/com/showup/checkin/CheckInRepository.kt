/*
 * CheckInRepository.kt
 * ShowUp · the 48hrs Check-In's server calls (SHOWUP-178 to SHOWUP-183)
 *
 * Behind [CheckInStore] so the view model is tested with no network. Every call is the generated
 * client's (`CheckInApi`, from openapi.json); nothing here hand-writes a request.
 */
package com.showup.checkin

import com.showup.api.ShowUpApi
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
}

interface CheckInStore {
    /** Null when the server could not be reached or refused. */
    suspend fun load(): CheckInState?
    suspend fun markIntroPassed(): Boolean
    suspend fun saveAnswers(motivation: String?, vibes: Set<String>?): Boolean
    suspend fun saveFilters(filters: CheckInFilters): Boolean
    suspend fun commit(request: CommitRequest): CommitResult
}

class CheckInRepository(private val api: ShowUpApi) : CheckInStore {
    override suspend fun load(): CheckInState? = callCatching {
        val response = api.checkIn.getCheckInState()
        response.body()?.takeIf { response.isSuccessful }?.toState()
    }.getOrNull()

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

    override suspend fun saveFilters(filters: CheckInFilters): Boolean = callCatching {
        api.checkIn.saveFilterSettings(
            SaveFilterSettingsDto(
                meetGender = CheckInGender.entries.filter { it.id in filters.genders }
                    .map { g -> MeetGender.entries.first { it.value == g.id } },
                minAge = filters.minAge,
                maxAge = filters.maxAgeOrNull,
                distanceKm = filters.distanceKm,
            ),
        ).isSuccessful
    }.getOrDefault(false)

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
        return if (response.code() == 422 && error == CONTACT_INFO_ERROR) CommitResult.ContactInfo
        else CommitResult.Server
    }

    private fun wireVibe(id: String) = WireVibe.entries.first { it.value == id }

    /**
     * `runCatching` that lets a cancellation through. Plain `runCatching` would turn the end of the
     * screen's scope into a failed save -- and a failed filter save inside the commit into a reported
     * `check_in_commit_failed` for a commit nobody is waiting on.
     */
    private inline fun <T> callCatching(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        /** The API's error code for an uplift refused for contact info (`check-in-flow.ts`). */
        const val CONTACT_INFO_ERROR = "uplift_contact_info"
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
    ),
)

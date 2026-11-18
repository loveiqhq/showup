/*
 * ChangeRepository.kt
 * ShowUp · what Date 05 asks the backend, and the app's own types for the answers (SHOWUP-196)
 *
 * Every call goes through the GENERATED client. Nothing here describes a URL or a request body. The
 * model is the app's own so a screen never imports a generated type: a regenerated enum constant
 * must not change what a screen reads or what the phone sends.
 *
 * NO PLACE NAME, ADDRESS, COORDINATE OR DISTANCE LEAVES THIS FILE FOR ANALYTICS. They are values for
 * the screen; the events carry ids of dates and kinds only (ticket, Data and legal).
 */
package com.showup.change

import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.api.ShowUpApi
import com.showup.api.generated.model.AgreeChangeDto
import com.showup.api.generated.model.ChangePendingDto
import com.showup.api.generated.model.ChangePlaceDto
import com.showup.api.generated.model.ChangePlacesDto
import com.showup.api.generated.model.ChangeStateDto
import com.showup.api.generated.model.ChangeSuggestionDto
import com.showup.api.generated.model.PlaceOptionDto
import com.showup.api.generated.model.SuggestChangeDto
import com.showup.api.outcome
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt
import com.showup.api.generated.model.ChangeEntry as WireEntry
import com.showup.api.generated.model.ChangeKind as WireKind
import com.showup.api.generated.model.ChangeStatus as WireStatus

/** What is suggested: a later time on the date's day, or another public place. */
enum class ChangeKind(val wire: String) {
    Time("time"),
    Place("place"),
    ;

    companion object {
        fun fromWire(wire: String): ChangeKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** §42 `change_entry`: where the picker was opened from. `cancel_sheet` is Date 07's, not built here. */
enum class ChangeEntry(val wire: String) {
    CoordinateTile("coordinate_tile"),
    ChatChip("chat_chip"),
    CounterCard("counter_card"),
    CounterThread("counter_thread"),
    ;

    companion object {
        fun fromWire(wire: String): ChangeEntry? = entries.firstOrNull { it.wire == wire }
    }
}

/** Where `I agree` was tapped: the request card on the Date screen, or the card in the thread. */
enum class AgreeSurface(val wire: String) {
    CoordinateCard("coordinate_card"),
    Thread("thread"),
}

enum class ChangeStatus { Pending, Agreed, Replaced, Expired }

/** The three kinds of public place the picker lists (R4). */
enum class PlaceType { Cafe, Bar, Restaurant }

/** One row of the place picker. [distanceMeters] is from the BINDING meet place, whole metres. */
data class PlaceOption(
    val placeId: String,
    val name: String,
    val type: PlaceType,
    val address: String,
    val distanceMeters: Int,
)

/** The places answer: [google] is true only when Google answered and its terms ask for the attribution line. */
data class PlacesList(val google: Boolean, val places: List<PlaceOption>)

/** A suggested place, as the server stored it. */
data class ChangePlace(val placeId: String, val name: String, val type: PlaceType, val address: String)

/**
 * One suggestion, in every state it can be read in. [time] is set for a time, [place] for a place. The
 * `expired*` fields are the binding values AT the moment it expired, for the system line. A pending
 * item from the date payload carries no status history: [status] is Pending and the rest null.
 */
data class ChangeSuggestion(
    val id: String,
    val kind: ChangeKind,
    val byYou: Boolean,
    val time: Instant?,
    val place: ChangePlace?,
    val wasTime: Instant,
    val wasPlaceName: String?,
    val deadline: Instant,
    val createdAt: Instant,
    val status: ChangeStatus = ChangeStatus.Pending,
    val answeredAt: Instant? = null,
    val expiredAt: Instant? = null,
    val expiredBindingTime: Instant? = null,
    val expiredBindingPlaceName: String? = null,
)

/**
 * The `change` block of the date payload. [slots] are the server's later start times (the client never
 * computes them); [timeWas] and [placeWas] are the ORIGINAL values after an agreed change, for
 * `Changed · was {original}`.
 */
data class DateChange(
    val cutoffAt: Instant,
    val allowed: Boolean,
    val slots: List<Instant>,
    val timeWas: Instant?,
    val placeWas: String?,
    val pending: List<ChangeSuggestion>,
)

/** What `Suggest` sends. [clientId] is the same on every retry of one send (R14). */
data class SuggestRequest(
    val kind: ChangeKind,
    val time: Instant?,
    val placeId: String?,
    val entry: ChangeEntry,
    val fromSearch: Boolean?,
    val clientId: String,
    val tz: String,
)

/**
 * What `Suggest` came back with. [Stale] is 409: the server refuses a suggestion that can no longer be made
 * (past the cutoff, after a check-in, a slot no longer offered, the place now binding), and asking again cannot
 * change that -- so it is not K: the picker closes and the date is read again. Everything else that is not a
 * success ([Failed]: no response, 503, any other status) is K, and a retry may succeed.
 */
sealed interface SuggestResult {
    data class Sent(val suggestion: ChangeSuggestion) : SuggestResult

    data object Stale : SuggestResult

    data class Failed(val why: CallFailure) : SuggestResult
}

/** Date 05's calls, behind an interface so the models are tested with no network. */
interface ChangeStore {
    /** Public places near the binding meet place, or matching [query] (null or blank: nearby). */
    suspend fun places(dateId: String, query: String?): Outcome<PlacesList>

    /** A suggestion; a repeat of the same clientId is answered with the stored one. */
    suspend fun suggest(dateId: String, request: SuggestRequest): SuggestResult

    /** `I agree`: the suggestion becomes binding for both. A refusal (no longer pending) is a failure. */
    suspend fun agree(dateId: String, suggestionId: String, surface: AgreeSurface): Outcome<ChangeSuggestion>

    /** Every suggestion of the date, oldest first, all statuses: the thread's cards and system lines. */
    suspend fun suggestions(dateId: String): Outcome<List<ChangeSuggestion>>
}

class ApiChangeStore(private val api: ShowUpApi) : ChangeStore {

    override suspend fun places(dateId: String, query: String?): Outcome<PlacesList> = outcome {
        val response = api.dates.listChangePlaces(dateId, query?.takeIf { it.isNotBlank() })
        response.body()?.takeIf { response.isSuccessful }?.toList()
    }

    /**
     * [outcome]'s line between "no response" and "a response that is not a success", plus the one answer that is
     * neither: 409, which the caller resolves with a date read rather than a retry.
     */
    override suspend fun suggest(dateId: String, request: SuggestRequest): SuggestResult = try {
        // By wire value, not constant name: a regenerated enum constant must not change what is sent.
        val body = SuggestChangeDto(
            clientId = UUID.fromString(request.clientId),
            entry = WireEntry.entries.first { it.value == request.entry.wire },
            kind = WireKind.entries.first { it.value == request.kind.wire },
            fromSearch = request.fromSearch,
            placeId = request.placeId,
            time = request.time?.atOffset(java.time.ZoneOffset.UTC),
            tz = request.tz,
        )
        val response = api.dates.suggestDateChange(dateId, body)
        val sent = response.body()
        when {
            response.isSuccessful && sent != null -> SuggestResult.Sent(sent.toSuggestion())
            response.code() == 409 -> SuggestResult.Stale
            else -> SuggestResult.Failed(CallFailure.Server)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        SuggestResult.Failed(CallFailure.Network)
    } catch (e: Exception) {
        SuggestResult.Failed(CallFailure.Server)
    }

    override suspend fun agree(dateId: String, suggestionId: String, surface: AgreeSurface): Outcome<ChangeSuggestion> = outcome {
        val wire = AgreeChangeDto.Surface.entries.first { it.value == surface.wire }
        val response = api.dates.agreeDateChange(dateId, suggestionId, AgreeChangeDto(wire))
        response.body()?.takeIf { response.isSuccessful }?.toSuggestion()
    }

    override suspend fun suggestions(dateId: String): Outcome<List<ChangeSuggestion>> = outcome {
        val response = api.dates.listDateChangeSuggestions(dateId)
        response.body()?.takeIf { response.isSuccessful }?.map { it.toSuggestion() }
    }
}

// ── the generated types, read once ──────────────────────────────────────────────────────────────

private fun PlaceOptionDto.Type.toType() = when (this) {
    PlaceOptionDto.Type.cafe -> PlaceType.Cafe
    PlaceOptionDto.Type.bar -> PlaceType.Bar
    PlaceOptionDto.Type.restaurant -> PlaceType.Restaurant
}

private fun ChangePlaceDto.Type.toType() = when (this) {
    ChangePlaceDto.Type.cafe -> PlaceType.Cafe
    ChangePlaceDto.Type.bar -> PlaceType.Bar
    ChangePlaceDto.Type.restaurant -> PlaceType.Restaurant
}

private fun ChangePlacesDto.toList() = PlacesList(
    google = attribution == ChangePlacesDto.Attribution.google,
    places = places.map { PlaceOption(it.placeId, it.name, it.type.toType(), it.address, it.distanceMeters.toDouble().roundToInt()) },
)

private fun WireKind.toKind() = if (this == WireKind.time) ChangeKind.Time else ChangeKind.Place

private fun ChangePlaceDto.toPlace() = ChangePlace(placeId, name, type.toType(), address)

private fun ChangeSuggestionDto.toSuggestion() = ChangeSuggestion(
    id = id.toString(),
    kind = kind.toKind(),
    byYou = byYou,
    time = time?.toInstant(),
    place = place?.toPlace(),
    wasTime = wasTime.toInstant(),
    wasPlaceName = wasPlace?.name,
    deadline = deadline.toInstant(),
    createdAt = createdAt.toInstant(),
    status = when (status) {
        WireStatus.pending -> ChangeStatus.Pending
        WireStatus.agreed -> ChangeStatus.Agreed
        WireStatus.replaced -> ChangeStatus.Replaced
        WireStatus.expired -> ChangeStatus.Expired
    },
    answeredAt = answeredAt?.toInstant(),
    expiredAt = expiredAt?.toInstant(),
    expiredBindingTime = expiredBindingTime?.toInstant(),
    expiredBindingPlaceName = expiredBindingPlace?.name,
)

/** A pending item of the date payload: the same shape without the status history. */
internal fun ChangePendingDto.toSuggestion() = ChangeSuggestion(
    id = id.toString(),
    kind = kind.toKind(),
    byYou = byYou,
    time = time?.toInstant(),
    place = place?.toPlace(),
    wasTime = wasTime.toInstant(),
    wasPlaceName = wasPlace?.name,
    deadline = deadline.toInstant(),
    createdAt = createdAt.toInstant(),
)

internal fun ChangeStateDto.toChange() = DateChange(
    cutoffAt = cutoffAt.toInstant(),
    allowed = allowed,
    slots = slots.map { it.toInstant() },
    timeWas = timeWas?.toInstant(),
    placeWas = placeWas?.name,
    pending = pending.map { it.toSuggestion() },
)

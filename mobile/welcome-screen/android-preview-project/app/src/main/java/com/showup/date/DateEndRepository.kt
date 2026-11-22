/*
 * DateEndRepository.kt
 * ShowUp · End date, Stay for now and Cancel (SHOWUP-197, SHOWUP-198)
 *
 * Every call goes through the GENERATED client. The 409 refusals carry their reason in the body's `code`; it is
 * read here and handed on as a string, so a screen decides what each one says.
 */
package com.showup.date

import com.showup.api.CallFailure
import com.showup.api.ShowUpApi
import com.showup.api.callCatching
import com.showup.api.generated.model.CancelDateDto
import com.showup.api.generated.model.EndDateDto
import com.showup.api.generated.model.EndedOutcomeDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import java.io.IOException
import com.showup.api.generated.model.DateEndReason as WireReason
import com.showup.api.generated.model.EndEntry as WireEntry

/** How the viewer ends the date (registry section 43). */
enum class EndReason(val wire: String) { Met("met"), PartnerNoShow("partner_no_show"), Uncomfortable("uncomfortable") }

/** Where the end started (registry section 45). */
enum class EndEntry(val wire: String) { EndDateBar("end_date_bar"), CheckinBarLink("checkin_bar_link"), Report("report") }

/** The code of a refusal that is a 404: the server has the end and cancel routes switched off. */
const val REFUSED_NOT_FOUND = "not_found"

/** The code of a 409 whose body named none. */
const val REFUSED_UNKNOWN = "unknown"

/** What `End date` came back with. Named apart from the chat's `EndResult`, which is Date 02's. */
sealed interface EndDateResult {
    data class Ended(val outcome: EndedOutcome) : EndDateResult

    /** 409 `date_not_active`, `before_meet` or `not_checked_in`; or [REFUSED_NOT_FOUND] when the switch is off. */
    data class Refused(val code: String) : EndDateResult

    data class Failed(val why: CallFailure) : EndDateResult
}

/** What `Cancel` came back with. */
sealed interface CancelResult {
    data class Cancelled(val outcome: EndedOutcome) : CancelResult

    /** 409 `date_not_active` or `checked_in`; or [REFUSED_NOT_FOUND] when the switch is off. */
    data class Refused(val code: String) : CancelResult

    data class Failed(val why: CallFailure) : CancelResult
}

/** End date and Stay for now, behind an interface so the view models are tested with no network. */
interface DateEndStore {
    suspend fun end(dateId: String, reason: EndReason, entry: EndEntry): EndDateResult

    /** "Stay for now": an internal flag the date keeps. True when it landed; the caller does not block on it. */
    suspend fun flagDiscomfort(dateId: String): Result<Boolean>
}

/** Cancel from the red link, behind an interface for the same reason. */
interface CancelStore {
    suspend fun cancel(dateId: String): CancelResult
}

/** The ended payload, in the app's own types. The photo path becomes a loadable URL here. */
internal fun EndedOutcomeDto.toOutcome(api: ShowUpApi) = EndedOutcome(
    dateId = dateId,
    name = name,
    endedByYou = endedByYou,
    photoUrl = photoUrl?.let(api::absoluteUrl),
    reason = OutcomeReason.fromWire(reason.value),
    partnerScreen = PartnerScreen.fromWire(partnerScreen?.value),
)

/** The refusal code of a 404 or 409, from the body's `code` (the `error` field carries the same). */
private fun refusalCode(response: Response<*>): String {
    if (response.code() == 404) return REFUSED_NOT_FOUND
    val body = response.errorBody()?.string()
    val code = runCatching {
        val root = Json.parseToJsonElement(body.orEmpty()).jsonObject
        (root["code"] ?: root["error"])?.jsonPrimitive?.content
    }.getOrNull()
    return code?.takeIf { it.isNotBlank() } ?: REFUSED_UNKNOWN
}

private fun isRefusal(code: Int) = code == 409 || code == 404

class ApiDateEndStore(private val api: ShowUpApi) : DateEndStore {

    override suspend fun end(dateId: String, reason: EndReason, entry: EndEntry): EndDateResult = try {
        // By wire value, not constant name: a regenerated enum constant must not change what is sent.
        val dto = EndDateDto(
            entry = WireEntry.entries.first { it.value == entry.wire },
            reason = WireReason.entries.first { it.value == reason.wire },
        )
        val response = api.dates.endDate(dateId, dto)
        val body = response.body()
        when {
            response.isSuccessful && body != null -> EndDateResult.Ended(body.toOutcome(api))
            isRefusal(response.code()) -> EndDateResult.Refused(refusalCode(response))
            else -> EndDateResult.Failed(CallFailure.Server)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        EndDateResult.Failed(CallFailure.Network)
    } catch (e: Exception) {
        EndDateResult.Failed(CallFailure.Server)
    }

    override suspend fun flagDiscomfort(dateId: String): Result<Boolean> = callCatching {
        api.dates.flagDateDiscomfort(dateId).isSuccessful
    }
}

class ApiCancelStore(private val api: ShowUpApi) : CancelStore {

    override suspend fun cancel(dateId: String): CancelResult = try {
        val response = api.dates.cancelDate(dateId, CancelDateDto())
        val body = response.body()
        when {
            response.isSuccessful && body != null -> CancelResult.Cancelled(body.toOutcome(api))
            isRefusal(response.code()) -> CancelResult.Refused(refusalCode(response))
            else -> CancelResult.Failed(CallFailure.Server)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        CancelResult.Failed(CallFailure.Network)
    } catch (e: Exception) {
        CancelResult.Failed(CallFailure.Server)
    }
}

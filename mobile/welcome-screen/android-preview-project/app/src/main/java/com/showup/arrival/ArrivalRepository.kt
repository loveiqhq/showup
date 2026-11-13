/*
 * ArrivalRepository.kt
 * ShowUp · what the arrival check-in asks the backend (SHOWUP-195)
 *
 * Every call goes through the GENERATED client. The dev partner check-in stays with the Date screen's
 * dev routes on [com.showup.date.DateStore].
 */
package com.showup.arrival

import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.api.ShowUpApi
import com.showup.api.generated.model.CreateArrivalCheckInDto
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import com.showup.api.generated.model.ArrivalEntry as WireEntry

/** What a check-in came back with. */
sealed interface CheckInResult {
    data object Stored : CheckInResult

    /** 409: a committed check-in exists already (or the date is no longer on). The next date read says how. */
    data object AlreadyCheckedIn : CheckInResult

    data class Failed(val why: CallFailure) : CheckInResult
}

/** The arrival calls, behind an interface so the gate is tested with no network. */
interface ArrivalStore {
    /** Stores the fix; the same [clientId] on a resend is stored once. A new one replaces an uncommitted check-in. */
    suspend fun checkIn(dateId: String, fix: LocationFix, entry: ArrivalEntry, clientId: String): CheckInResult

    /**
     * Deletes an uncommitted check-in. Every 4xx (409: already committed, among them) is [Outcome.Ok]: there is
     * nothing left to retry. Only no answer or a 5xx is [Outcome.Failed].
     */
    suspend fun undo(dateId: String): Outcome<Unit>

    /** Tells the match. Every 4xx (404: nothing to commit) is [Outcome.Ok] for the same reason. */
    suspend fun commit(dateId: String): Outcome<Unit>
}

class ApiArrivalStore(private val api: ShowUpApi) : ArrivalStore {

    override suspend fun checkIn(dateId: String, fix: LocationFix, entry: ArrivalEntry, clientId: String): CheckInResult = try {
        // By wire value, not constant name: a regenerated enum constant must not change what is sent.
        val wire = WireEntry.entries.first { it.value == entry.wire }
        val dto = CreateArrivalCheckInDto(
            latitude = BigDecimal.valueOf(fix.latitude),
            longitude = BigDecimal.valueOf(fix.longitude),
            entry = wire,
            clientId = UUID.fromString(clientId),
            accuracyM = fix.accuracyM?.let(BigDecimal::valueOf),
        )
        val response = api.dates.createArrivalCheckIn(dateId, dto)
        when {
            response.isSuccessful -> CheckInResult.Stored
            response.code() == 409 -> CheckInResult.AlreadyCheckedIn
            else -> CheckInResult.Failed(CallFailure.Server)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        CheckInResult.Failed(CallFailure.Network)
    } catch (e: Exception) {
        CheckInResult.Failed(CallFailure.Server)
    }

    override suspend fun undo(dateId: String): Outcome<Unit> = settled { api.dates.undoArrivalCheckIn(dateId) }

    override suspend fun commit(dateId: String): Outcome<Unit> = settled { api.dates.commitArrivalCheckIn(dateId) }

    /**
     * Success or ANY 4xx is done: a refused request answers the same again (409 already committed, 404 nothing
     * to commit, 400/401/403/429 no retry will fix), so retrying it forever helps nobody. No answer is Network,
     * a 5xx Server: those are retried.
     */
    private inline fun settled(call: () -> Response<*>): Outcome<Unit> = try {
        val response = call()
        if (response.isSuccessful || response.code() in 400..499) Outcome.Ok(Unit) else Outcome.Failed(CallFailure.Server)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Outcome.Failed(CallFailure.Network)
    } catch (e: Exception) {
        Outcome.Failed(CallFailure.Server)
    }
}

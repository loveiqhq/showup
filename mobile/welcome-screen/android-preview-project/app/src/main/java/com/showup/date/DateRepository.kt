/*
 * DateRepository.kt
 * ShowUp · what the Date screen asks the backend (SHOWUP-193)
 *
 * Every call goes through the GENERATED client. Nothing here describes a URL or a request body.
 */
package com.showup.date

import com.showup.api.Outcome
import com.showup.api.ShowUpApi
import com.showup.api.callCatching
import com.showup.api.generated.model.ActiveDateDto
import com.showup.api.generated.model.CreateDevDateDto
import com.showup.api.generated.model.DateEndReason as WireEndReason
import com.showup.api.generated.model.DevDateChangeDto
import com.showup.api.generated.model.DevDatePartnerEndsDto
import com.showup.api.generated.model.DevDateMessageDto
import com.showup.api.outcome
import com.showup.change.ChangeKind
import com.showup.change.toChange
import com.showup.change.toSuggestion
import com.showup.chat.ChatReason
import com.showup.search.SearchProfile
import com.showup.search.toProfile
import com.showup.api.generated.model.ChangeKind as WireKind
import com.showup.api.generated.model.ChatReason as WireReason

/** The Date screen's calls, behind an interface so the view model is tested with no network. */
interface DateStore {
    /** The viewer's active date, read in [zone] (for `today` / `tomorrow`). */
    suspend fun active(zone: String): Outcome<DateRead>

    /** "See {name}'s full profile": the match, built by the server as Search builds a profile. */
    suspend fun partnerProfile(zone: String): Outcome<SearchProfile>

    /** Debug only: a date with the test match Leonie in [minutes]. */
    suspend fun createTestDate(minutes: Int): Result<Boolean>

    /**
     * Debug only: a message from Leonie (the chat must be open: 90 minutes or less to go). A null
     * [reason] sends it with no chip, as a message typed without one.
     */
    suspend fun messageFromMatch(reason: ChatReason?, body: String): Result<Boolean>

    /** Debug only: ends the viewer's date, the escape from a screen whose exits are later tickets. */
    suspend fun endTestDate(): Result<Boolean>

    /**
     * Debug only: the test match ends the date in the chat (SHOWUP-194), through the real path -- so it
     * needs her "I can't make it today" message in the thread, and the viewer lands on H.
     */
    suspend fun matchEndsInChat(): Result<Boolean>

    /**
     * The viewer has seen the match's arrival card: the chat-read stamp, the same one the chat's open sets
     * (Date 03 R3). A stamp: a failed one is not reported, the chat's open stamps the same field.
     */
    suspend fun markArrivalSeen(dateId: String)

    /** Debug only: the test match checks in and commits, sending the push. */
    suspend fun partnerChecksIn(): Result<Boolean>

    /** Debug only (Date 05): the test match suggests a later time or another place, so the request card shows. */
    suspend fun matchSuggests(kind: ChangeKind): Result<Boolean>

    /** Debug only (Date 05): the test match agrees to the viewer's earliest-deadline pending suggestion. */
    suspend fun matchAgrees(): Result<Boolean>

    /** Debug only (Date 05): every pending suggestion is past its deadline and the expiry runs once. */
    suspend fun expireSuggestions(): Result<Boolean>

    /**
     * Debug only (Date 07 H): the test match cancels the viewer's date from the red link; the viewer's next read is
     * the cancelled outcome. A default that refuses, so only the real store and the demo carry it.
     */
    suspend fun partnerCancels(): Result<Boolean> = Result.failure(UnsupportedOperationException("partnerCancels"))

    /**
     * Debug only (Date 06, the Date 08 slot): the test match ends the viewer's date with [reason]. The server has her
     * check in first for the two problem reasons.
     */
    suspend fun partnerEnds(reason: EndReason): Result<Boolean> = Result.failure(UnsupportedOperationException("partnerEnds"))
}

class DateRepository(private val api: ShowUpApi) : DateStore {

    override suspend fun active(zone: String): Outcome<DateRead> = outcome {
        val response = api.dates.getActiveDate(zone)
        response.body()?.takeIf { response.isSuccessful }?.let { body ->
            body.date?.let { DateRead.Active(it.toActiveDate()) }
                ?: body.ended?.let { DateRead.Ended(it.toOutcome(api)) }
                ?: DateRead.None
        }
    }

    override suspend fun partnerProfile(zone: String): Outcome<SearchProfile> = outcome {
        val response = api.dates.getActiveDateProfile(zone)
        response.body()?.takeIf { response.isSuccessful }?.toProfile(api)
    }

    override suspend fun createTestDate(minutes: Int): Result<Boolean> = callCatching {
        api.dev.createDevDate(CreateDevDateDto(minutesFromNow = minutes)).isSuccessful
    }

    override suspend fun messageFromMatch(reason: ChatReason?, body: String): Result<Boolean> = callCatching {
        // By wire value, not constant name: a regenerated enum constant must not change what is sent.
        val wire = reason?.let { chip -> WireReason.entries.first { it.value == chip.wire } }
        api.dev.sendDevDateMessage(DevDateMessageDto(body = body, reason = wire)).isSuccessful
    }

    override suspend fun endTestDate(): Result<Boolean> = callCatching {
        api.dev.endDevDate().isSuccessful
    }

    override suspend fun matchEndsInChat(): Result<Boolean> = callCatching {
        api.dev.endDevDateInChat().isSuccessful
    }

    override suspend fun markArrivalSeen(dateId: String) {
        callCatching { api.dates.markDateChatRead(dateId) }
    }

    override suspend fun partnerChecksIn(): Result<Boolean> = callCatching {
        api.dev.devPartnerCheckIn().isSuccessful
    }

    override suspend fun matchSuggests(kind: ChangeKind): Result<Boolean> = callCatching {
        // By wire value, not constant name: a regenerated enum constant must not change what is sent.
        val wire = WireKind.entries.first { it.value == kind.wire }
        api.dev.createDevDateChange(DevDateChangeDto(kind = wire)).isSuccessful
    }

    override suspend fun matchAgrees(): Result<Boolean> = callCatching {
        api.dev.agreeDevDateChange().isSuccessful
    }

    override suspend fun expireSuggestions(): Result<Boolean> = callCatching {
        api.dev.expireDevDateChange().isSuccessful
    }

    override suspend fun partnerCancels(): Result<Boolean> = callCatching {
        api.dev.cancelDevDateAsPartner().isSuccessful
    }

    override suspend fun partnerEnds(reason: EndReason): Result<Boolean> = callCatching {
        // By wire value, not constant name, as every dev call here.
        val wire = WireEndReason.entries.first { it.value == reason.wire }
        api.dev.endDevDateAsPartner(DevDatePartnerEndsDto(reason = wire)).isSuccessful
    }

    private fun ActiveDateDto.toActiveDate() = ActiveDate(
        id = id,
        meetAt = meetAt.toInstant(),
        meetDay = meetDay.value,
        unlockAt = unlockAt.toInstant(),
        coordinateOpen = coordinateOpen,
        place = place?.let { DatePlace(it.name, it.address) },
        match = DateMatch(
            userId = match.userId,
            name = match.name,
            age = match.age,
            district = match.district,
            distanceKm = match.distanceKm,
            photoUrl = match.photoUrl?.let(api::absoluteUrl),
            attendedCount = match.attendedCount,
        ),
        motivationYou = motivations.you,
        motivationThem = motivations.them,
        sharedVibes = sharedVibes,
        sharedInterests = sharedInterests,
        unread = unread?.let { DateUnread(it.count, ChatReason.fromWire(it.reason?.value), it.body, it.sentAt.toInstant()) },
        checkedIn = checkedIn,
        chatTutorialSeen = chatTutorialSeen,
        arrival = arrival?.let { DateArrival(it.checkedInAt.toInstant()) },
        change = change?.toChange(),
        changeRequest = changeRequest?.toSuggestion(),
        endsEnabled = endsEnabled,
        cancelPenaltyPoints = cancelPenaltyPoints,
    )
}

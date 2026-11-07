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
import com.showup.api.generated.model.DevDateMessageDto
import com.showup.api.outcome
import com.showup.chat.ChatReason
import com.showup.search.SearchProfile
import com.showup.search.toProfile
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
}

class DateRepository(private val api: ShowUpApi) : DateStore {

    override suspend fun active(zone: String): Outcome<DateRead> = outcome {
        val response = api.dates.getActiveDate(zone)
        response.body()?.takeIf { response.isSuccessful }?.let { body ->
            body.date?.let { DateRead.Active(it.toActiveDate()) }
                ?: body.ended?.let { DateRead.Ended(EndedInChat(it.dateId, it.name, it.endedByYou)) }
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
    )
}
